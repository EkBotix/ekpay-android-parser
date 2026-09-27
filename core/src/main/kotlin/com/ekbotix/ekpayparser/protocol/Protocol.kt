package com.ekbotix.ekpayparser.protocol

import com.ekbotix.ekpayparser.crypto.ParserKeyStore
import com.ekbotix.ekpayparser.model.*
import com.google.gson.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

object Protocol {
    const val EVIDENCE = "/api/internal/parser/evidence"
    const val HEARTBEAT = "/api/internal/parser/heartbeat"
    const val PAIR = "/sandbox/parser/pair" // disposable bridge only, NOT a deployed backend route
    const val MAX_BODY = 4096
    const val CLOCK_WINDOW_MS = 300_000L
    private val json = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    fun publicEncoding(bytes: ByteArray): String { require(bytes.size == 32); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) }
    fun nonce() = publicEncodingNonce(ByteArray(16).also { SecureRandom().nextBytes(it) })
    private fun publicEncodingNonce(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun ingestionId() = UUID.randomUUID().toString()
    fun iso(millis: Long) = DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.MILLIS))
    fun evidenceBody(e: Evidence): ByteArray {
        require(iso(Instant.parse(e.providerTimestamp).toEpochMilli()) == e.providerTimestamp)
        return bytes(JsonObject().apply {
            addProperty("provider", e.provider); addProperty("provider_transaction_id", e.transactionId)
            addProperty("amount_minor", e.amountMinor); addProperty("currency", "BDT")
            add("receiver_identity_hash", e.receiverHash?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
            add("sender_identity_hash", e.senderHash?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
            addProperty("provider_timestamp", e.providerTimestamp)
        })
    }
    fun heartbeatBody(appVersion: String) = bytes(JsonObject().apply { addProperty("app_version", appVersion.take(32)); addProperty("protocol_version", 1) })
    fun bytes(value: JsonObject): ByteArray = json.toJson(value).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BODY) }
    // evidenceBody is already in backend's exact normalized property order.
    fun messageHash(path: String, body: ByteArray): String { require(path in setOf(EVIDENCE, HEARTBEAT)); return sha(body) }
    fun canonical(device: String, version: Int, timestamp: Long, nonce: String, ingestion: String, hash: String, path: String, raw: ByteArray): ByteArray {
        require(path in setOf(EVIDENCE, HEARTBEAT)); require(version > 0); require(timestamp.toString().matches(Regex("[1-9][0-9]{12}")))
        require(UUID.fromString(device).toString() == device); require(UUID.fromString(ingestion).version() == 4 && UUID.fromString(ingestion).toString() == ingestion)
        require(nonce.matches(Regex("[A-Za-z0-9_-]{22}")) && Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(nonce)) == nonce)
        require(hash.matches(Regex("[a-f0-9]{64}"))); require(raw.size <= MAX_BODY)
        return listOf("ekpay-parser-v1", "POST", path, device, version.toString(), timestamp.toString(), nonce, ingestion, hash, sha(raw)).joinToString("\n").toByteArray(Charsets.UTF_8)
    }
    fun signed(identity: DeviceIdentity, keys: ParserKeyStore, path: String, body: ByteArray, ingestion: String, now: Long = System.currentTimeMillis(), nonce: String = nonce()): SignedRequest {
        require(identity.state == "active" && identity.environment == "test")
        val raw = body.copyOf() // hashed bytes and transmitted bytes are identical, no reserialization
        val hash = messageHash(path, raw)
        val signature = hex(keys.sign(identity.alias, canonical(identity.deviceId, identity.keyVersion, now, nonce, ingestion, hash, path, raw)))
        require(signature.length == 128)
        return SignedRequest(path, raw, linkedMapOf("Content-Type" to "application/json", "EkPay-Parser-Protocol" to "1", "X-EkPay-Device-Id" to identity.deviceId,
            "X-EkPay-Key-Version" to identity.keyVersion.toString(), "X-EkPay-Timestamp" to now.toString(), "X-EkPay-Nonce" to nonce, "X-EkPay-Ingestion-Id" to ingestion,
            "X-EkPay-Message-Hash" to hash, "X-EkPay-Signature" to signature))
    }
    fun pairBody(device: String, version: Int, token: String, alias: String, keys: ParserKeyStore): ByteArray {
        require(UUID.fromString(device).toString() == device && version in 0..2147483646 && token.matches(Regex("[a-f0-9]{64}")))
        val pub = keys.publicKey(alias)
        val proof = listOf("ekpay-parser-pair-v1", device, version.toString(), sha(token.toByteArray(Charsets.UTF_8)), sha(pub)).joinToString("\n").toByteArray(Charsets.UTF_8)
        return bytes(JsonObject().apply { addProperty("device_id", device); addProperty("expected_version", version); addProperty("pairing_token", token)
            addProperty("public_key", publicEncoding(pub)); addProperty("proof_signature", hex(keys.sign(alias, proof))) })
    }
}
