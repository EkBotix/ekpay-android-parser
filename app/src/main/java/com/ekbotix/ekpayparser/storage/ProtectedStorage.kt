package com.ekbotix.ekpayparser.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64

/** Android Keystore AES key; no plaintext file fallback, backup or key export. */
interface SecureVault {
    fun encrypt(label:String,bytes:ByteArray):String
    fun decrypt(label:String,encrypted:String):ByteArray
    fun write(label:String,value:ByteArray)
    fun read(label:String):ByteArray?
    fun delete(label:String)
}
class ProtectedStorage(context: Context):SecureVault {
    private val directory = File(context.noBackupFilesDir, "parser-protected").apply { mkdirs() }
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "ekpay.storage.v1"
    @Synchronized private fun key(): SecretKey {
        if (!store.containsAlias(alias)) KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
        return store.getKey(alias, null) as SecretKey
    }
    override fun encrypt(label: String, bytes: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(label.toByteArray()) }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(bytes))
    }
    override fun decrypt(label: String, encrypted: String): ByteArray {
        // Missing wrap key on existing ciphertext is key loss, never silently recreate it.
        check(store.containsAlias(alias)) { "Protected state unavailable" }
        val bytes = Base64.getDecoder().decode(encrypted); require(bytes.size >= 28)
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, store.getKey(alias, null), GCMParameterSpec(128, bytes.copyOfRange(0,12))); updateAAD(label.toByteArray()) }.doFinal(bytes.copyOfRange(12,bytes.size))
    }
    override fun write(label: String, value: ByteArray) {
        require(label.matches(Regex("[A-Za-z0-9._-]+")))
        val file = AtomicFile(File(directory, label)); val stream = file.startWrite()
        try { stream.write(encrypt(label, value).toByteArray()); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    override fun read(label: String): ByteArray? { val f=File(directory,label); return if(f.exists()) decrypt(label,AtomicFile(f).readFully().toString(Charsets.UTF_8)) else null }
    override fun delete(label: String) { AtomicFile(File(directory,label)).delete() }
}
