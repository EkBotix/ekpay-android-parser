package com.ekbotix.ekpayparser.sms

import java.math.BigDecimal

abstract class AbstractSmsParser : SmsProviderParser {
    protected fun hash(body: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    protected fun parseAmountMinor(amountStr: String): Long? {
        return try {
            val bd = BigDecimal(amountStr)
            if (bd.scale() > 2) return null // Reject extra decimals
            bd.movePointRight(2).longValueExact()
        } catch (e: Exception) {
            null
        }
    }
}

class BkashSmsParser : AbstractSmsParser() {
    override fun supports(sender: String, body: String): Boolean = sender.equals("TEST_BKASH", ignoreCase = true)

    override fun parse(message: SmsMessageInput): ParseResult {
        if (!supports(message.sender, message.body)) return ParseResult(ParseStatus.UNSUPPORTED_SENDER)

        val amountRegex = Regex("Tk\\s*([0-9]+\\.?[0-9]*)", RegexOption.IGNORE_CASE)
        val txnRegex = Regex("TxnId:\\s*(TEST_[A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)

        val amountMatch = amountRegex.find(message.body)
        val txnMatch = txnRegex.find(message.body)
        if (amountMatch == null || txnMatch == null) return ParseResult(ParseStatus.INVALID)

        val amountMinor = parseAmountMinor(amountMatch.groupValues[1]) ?: return ParseResult(ParseStatus.INVALID)
        if (amountMinor <= 0) return ParseResult(ParseStatus.INVALID)

        return ParseResult(ParseStatus.PARSED, "bkash", txnMatch.groupValues[1], amountMinor, "BDT", localReceivedAt = message.receivedAt, messageHash = hash(message.body))
    }
}
