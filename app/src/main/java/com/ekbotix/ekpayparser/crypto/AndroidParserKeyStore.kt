package com.ekbotix.ekpayparser.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import com.ekbotix.ekpayparser.storage.SecureVault
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.util.UUID

class AndroidParserKeyStore(private val protected: SecureVault, private val softwareAllowed: () -> Boolean) : ParserKeyStore {
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    override fun generate(): String {
        val alias = "ekpay.ed25519." + UUID.randomUUID()
        if (Build.VERSION.SDK_INT >= 33) {
            val available = runCatching {
                KeyPairGenerator.getInstance("EC", "AndroidKeyStore").apply {
                    initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("ed25519")).setDigests(KeyProperties.DIGEST_NONE).build())
                }.generateKeyPair()
                check(publicKey(alias).size == 32); check(sign(alias, "sandbox probe".toByteArray()).size == 64)
            }.isSuccess
            if (available) return alias
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        }
        check(softwareAllowed()) { "Native Ed25519 unavailable. Explicit protected-software sandbox consent required." }
        val privateKey = Ed25519PrivateKeyParameters(SecureRandom())
        val seed = privateKey.encoded
        try { protected.write(alias, seed) } finally { seed.fill(0) }
        return alias
    }
    private fun native(alias: String) = store.containsAlias(alias)
    private fun software(alias: String): Ed25519PrivateKeyParameters {
        val seed = protected.read(alias) ?: error("Signing key missing; re-pair required")
        return try { require(seed.size == 32); Ed25519PrivateKeyParameters(seed,0) } finally { seed.fill(0) }
    }
    override fun publicKey(alias: String): ByteArray {
        if (!native(alias)) return software(alias).generatePublicKey().encoded
        val encoded = store.getCertificate(alias).publicKey.encoded
        val prefix = byteArrayOf(0x30,0x2a,0x30,0x05,0x06,0x03,0x2b,0x65,0x70,0x03,0x21,0x00)
        check(encoded.size==44 && encoded.copyOfRange(0,12).contentEquals(prefix)) { "Unsupported public key encoding" }
        return encoded.copyOfRange(12,44)
    }
    override fun sign(alias: String, bytes: ByteArray): ByteArray {
        if (native(alias)) return Signature.getInstance("Ed25519").apply { initSign(store.getKey(alias,null) as PrivateKey); update(bytes) }.sign()
        return Ed25519Signer().apply { init(true,software(alias)); update(bytes,0,bytes.size) }.generateSignature()
    }
    override fun delete(alias: String) { if(native(alias))store.deleteEntry(alias); protected.delete(alias) }
    @Suppress("DEPRECATION") // API 28-30 compatibility; API 31+ uses securityLevel below.
    override fun protection(alias: String): String {
        if (!native(alias)) { publicKey(alias); return "Keystore AES-GCM protected software Ed25519 (sandbox)" }
        val info=runCatching { KeyFactory.getInstance("EC","AndroidKeyStore").getKeySpec(store.getKey(alias,null),KeyInfo::class.java) }.getOrNull()
        val hardware=if(Build.VERSION.SDK_INT>=31)info?.securityLevel in setOf(KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,KeyProperties.SECURITY_LEVEL_STRONGBOX) else info?.isInsideSecureHardware==true
        return if (hardware) "Native hardware-backed Ed25519" else "Native Android Keystore Ed25519; hardware not verified"
    }
}
