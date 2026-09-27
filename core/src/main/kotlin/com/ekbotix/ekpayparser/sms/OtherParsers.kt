package com.ekbotix.ekpayparser.sms

class NagadSmsParser : AbstractSmsParser() {
    override fun supports(sender: String, body: String): Boolean = sender.equals("TEST_NAGAD", ignoreCase = true)

    override fun parse(message: SmsMessageInput): ParseResult {
        if (!supports(message.sender, message.body)) return ParseResult(ParseStatus.UNSUPPORTED_SENDER)

        val amountRegex = Regex("Amount:\\s*Tk\\s*([0-9]+\\.?[0-9]*)", RegexOption.IGNORE_CASE)
        val txnRegex = Regex("TxnID:\\s*(TEST_[A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)

        val amountMatch = amountRegex.find(message.body)
        val txnMatch = txnRegex.find(message.body)
        if (amountMatch == null || txnMatch == null) return ParseResult(ParseStatus.INVALID)

        val amountMinor = parseAmountMinor(amountMatch.groupValues[1]) ?: return ParseResult(ParseStatus.INVALID)
        return ParseResult(ParseStatus.PARSED, "nagad", txnMatch.groupValues[1], amountMinor, "BDT", localReceivedAt = message.receivedAt, messageHash = hash(message.body))
    }
}

class RocketSmsParser : AbstractSmsParser() {
    override fun supports(sender: String, body: String): Boolean = sender.equals("TEST_ROCKET", ignoreCase = true)
    override fun parse(message: SmsMessageInput): ParseResult {
        if (!supports(message.sender, message.body)) return ParseResult(ParseStatus.UNSUPPORTED_SENDER)

        val amountRegex = Regex("Tk\\s*([0-9]+\\.?[0-9]*)", RegexOption.IGNORE_CASE)
        val txnRegex = Regex("TxnId:\\s*(TEST_[A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)

        val amountMatch = amountRegex.find(message.body)
        val txnMatch = txnRegex.find(message.body)
        if (amountMatch == null || txnMatch == null) return ParseResult(ParseStatus.INVALID)

        val amountMinor = parseAmountMinor(amountMatch.groupValues[1]) ?: return ParseResult(ParseStatus.INVALID)
        return ParseResult(ParseStatus.PARSED, "rocket", txnMatch.groupValues[1], amountMinor, "BDT", localReceivedAt = message.receivedAt, messageHash = hash(message.body))
    }
}

class UpaySmsParser : AbstractSmsParser() {
    override fun supports(sender: String, body: String): Boolean = sender.equals("TEST_UPAY", ignoreCase = true)
    override fun parse(message: SmsMessageInput): ParseResult {
        if (!supports(message.sender, message.body)) return ParseResult(ParseStatus.UNSUPPORTED_SENDER)

        val amountRegex = Regex("Tk\\s*([0-9]+\\.?[0-9]*)", RegexOption.IGNORE_CASE)
        val txnRegex = Regex("TrxID\\s*(TEST_[A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)

        val amountMatch = amountRegex.find(message.body)
        val txnMatch = txnRegex.find(message.body)
        if (amountMatch == null || txnMatch == null) return ParseResult(ParseStatus.INVALID)

        val amountMinor = parseAmountMinor(amountMatch.groupValues[1]) ?: return ParseResult(ParseStatus.INVALID)
        return ParseResult(ParseStatus.PARSED, "upay", txnMatch.groupValues[1], amountMinor, "BDT", localReceivedAt = message.receivedAt, messageHash = hash(message.body))
    }
}
