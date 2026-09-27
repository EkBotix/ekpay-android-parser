package com.ekbotix.ekpayparser.sms

class SmsParserPipeline(private val parsers: List<SmsProviderParser>) {
    fun process(message: SmsMessageInput): ParseResult {
        // Safety filtering
        val bodyLower = message.body.lowercase()
        val badKeywords = listOf(
            "otp", "verification code", "pin", "one-time password",
            "offer", "cashback campaign", "promotional", "cashback"
        )
        // Also reject balance-only: wait, balance-only could just not match the regex.
        // But let's check for "verification" and "one time password".
        if (badKeywords.any { it in bodyLower }) {
            return ParseResult(ParseStatus.IGNORED)
        }

        val parser = parsers.firstOrNull { it.supports(message.sender, message.body) }
            ?: return ParseResult(ParseStatus.UNSUPPORTED_SENDER)

        return parser.parse(message)
    }
}
