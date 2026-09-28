package com.ekbotix.ekpayparser.model

data class DeviceIdentity(val deviceId: String, val keyVersion: Int, val alias: String, val pairedAt: Long, val state: String = "active", val environment: String = "test", val provider:String?=null, val accountDisplay:String?=null)
data class Evidence(val provider: String, val transactionId: String, val amountMinor: Long, val providerTimestamp: String, val receiverHash: String? = null, val senderHash: String? = null) {
    init {
        require(provider in setOf("bkash", "nagad", "rocket", "upay"))
        require(transactionId.matches(Regex("^[A-Z0-9][A-Z0-9._-]{0,127}$")))
        require(amountMinor in 1..9007199254740991L)
        require(receiverHash == null || receiverHash.matches(Regex("[a-f0-9]{64}")))
        require(senderHash == null || senderHash.matches(Regex("[a-f0-9]{64}")))
    }
}
data class SignedRequest(val path: String, val body: ByteArray, val headers: Map<String, String>)
data class ApiReply(val status: Int, val error: String? = null, val deviceId: String? = null, val keyVersion: Int? = null, val reused: Boolean = false, val evidenceId:String? = null, val provider:String?=null, val accountDisplay:String?=null)
