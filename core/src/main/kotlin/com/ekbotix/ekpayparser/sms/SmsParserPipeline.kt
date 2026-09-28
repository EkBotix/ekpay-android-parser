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

    fun processApproved(provider:String,message:SmsMessageInput):ParseResult {
        val direction=SmsSafety.direction(message.body)
        if(direction in setOf(TransactionDirection.AUTHENTICATION,TransactionDirection.PROMOTIONAL,TransactionDirection.BALANCE_ONLY))return ParseResult(ParseStatus.IGNORED,direction=direction)
        if(direction in setOf(TransactionDirection.OUTGOING,TransactionDirection.REFUND_OR_REVERSAL))return ParseResult(ParseStatus.UNSUPPORTED_TYPE,direction=direction)
        val parser=parsers.filterIsInstance<RuleBasedSmsParser>().firstOrNull { it.provider()==provider }
            ?: return ParseResult(ParseStatus.UNSUPPORTED_SENDER)
        return parser.parseApproved(message)
    }

    fun normalize(acquired:AcquiredPaymentMessage,approvedProvider:String?):Pair<ParseResult,NormalizedPaymentEvidence?> {
        val message=SmsMessageInput(acquired.sourceIdentity,acquired.body,acquired.observedAt)
        val result=if(approvedProvider==null)process(message) else processApproved(approvedProvider,message)
        val normalized=if(result.status==ParseStatus.PARSED && result.provider!=null && result.transactionId!=null && result.amountMinor!=null && result.messageHash!=null)
            NormalizedPaymentEvidence(result.provider,result.transactionId,result.amountMinor,result.currency?:"BDT",result.providerTimestamp,
                acquired.observedAt,result.receiverReference,result.senderReference,result.messageHash,result.parserVersion,acquired.source)
        else null
        return result to normalized
    }
}
