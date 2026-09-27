package com.ekbotix.ekpayparser.sms

enum class ParseStatus {
    IGNORED,
    UNSUPPORTED_SENDER,
    PARSED,
    PARTIAL,
    INVALID,
    AMBIGUOUS
}

data class SmsMessageInput(
    val sender: String,
    val body: String,
    val receivedAt: Long
)

data class ParseResult(
    val status: ParseStatus,
    val provider: String? = null,
    val transactionId: String? = null,
    val amountMinor: Long? = null,
    val currency: String? = null,
    val receiverReference: String? = null,
    val senderReference: String? = null,
    val providerTimestamp: String? = null,
    val localReceivedAt: Long? = null,
    val messageHash: String? = null,
    val parserVersion: String = "ekpay-sms-parser-v1"
)
