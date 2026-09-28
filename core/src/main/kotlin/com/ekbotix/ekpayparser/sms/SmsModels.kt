package com.ekbotix.ekpayparser.sms

enum class ParseStatus {
    IGNORED,
    UNSUPPORTED_SENDER,
    PARSED,
    PARTIAL,
    INVALID,
    AMBIGUOUS,
    UNSUPPORTED_TYPE,
    MANUAL_REVIEW
}

enum class TransactionDirection { INCOMING, OUTGOING, REFUND_OR_REVERSAL, AUTHENTICATION, PROMOTIONAL, BALANCE_ONLY, UNKNOWN }
enum class SenderRegistryState { UNVERIFIED, OBSERVED, APPROVED, DISABLED }

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
    val parserVersion: String = "ekpay-sms-parser-v2",
    val direction: TransactionDirection = TransactionDirection.UNKNOWN
)

data class SmsPart(val sender:String?,val body:String?,val timestamp:Long)
data class FormatAnalysis(val amountCandidates:List<String>,val transactionIdCandidates:List<String>,val timestampCandidate:String?,val keywords:List<String>,val status:ParseStatus,val warnings:List<String>)
