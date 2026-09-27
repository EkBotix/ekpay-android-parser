package com.ekbotix.ekpayparser.crypto

/** No private export method. Alias is local only; never a hardware identifier. */
interface ParserKeyStore {
    fun generate(): String
    fun publicKey(alias: String): ByteArray
    fun sign(alias: String, bytes: ByteArray): ByteArray
    fun delete(alias: String)
    fun protection(alias: String): String
}
