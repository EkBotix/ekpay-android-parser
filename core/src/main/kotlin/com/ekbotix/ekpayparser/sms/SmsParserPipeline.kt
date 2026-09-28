package com.ekbotix.ekpayparser.sms

class SmsParserPipeline(private val parsers: List<SmsProviderParser>) {
    fun process(message: SmsMessageInput): ParseResult {
        val direction=SmsSafety.direction(message.body)
        if(direction in setOf(TransactionDirection.AUTHENTICATION,TransactionDirection.PROMOTIONAL,TransactionDirection.BALANCE_ONLY))return ParseResult(ParseStatus.IGNORED,direction=direction)
        if(direction in setOf(TransactionDirection.OUTGOING,TransactionDirection.REFUND_OR_REVERSAL))return ParseResult(ParseStatus.UNSUPPORTED_TYPE,direction=direction)

        val parser = parsers.firstOrNull { it.supports(message.sender, message.body) }
            ?: return ParseResult(ParseStatus.UNSUPPORTED_SENDER)

        return parser.parse(message)
    }
}
