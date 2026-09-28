package com.ekbotix.ekpayparser.sms

import java.security.MessageDigest

abstract class RuleBasedSmsParser(private val rule:ProviderRuleSet):SmsProviderParser {
    override fun supports(sender:String,body:String)=SenderRegistry.normalize(sender) in rule.testSenders
    override fun parse(message:SmsMessageInput):ParseResult {
        if(!supports(message.sender,message.body))return ParseResult(ParseStatus.UNSUPPORTED_SENDER)
        val direction=SmsSafety.direction(message.body)
        if(direction in setOf(TransactionDirection.AUTHENTICATION,TransactionDirection.PROMOTIONAL,TransactionDirection.BALANCE_ONLY))return ParseResult(ParseStatus.IGNORED,direction=direction,parserVersion=rule.parserVersion)
        if(direction in setOf(TransactionDirection.OUTGOING,TransactionDirection.REFUND_OR_REVERSAL))return ParseResult(ParseStatus.UNSUPPORTED_TYPE,direction=direction,parserVersion=rule.parserVersion)
        if(direction!=TransactionDirection.INCOMING)return ParseResult(ParseStatus.MANUAL_REVIEW,direction=direction,parserVersion=rule.parserVersion)
        val amounts=FormatAnalyzer.amountCandidates(message.body)
        val ids=FormatAnalyzer.transactionCandidates(message.body,rule)
        if(amounts.size>1||ids.size>1)return ParseResult(ParseStatus.AMBIGUOUS,direction=direction,parserVersion=rule.parserVersion)
        if(amounts.size!=1||ids.size!=1)return ParseResult(ParseStatus.INVALID,direction=direction,parserVersion=rule.parserVersion)
        val transactionId=ids.single()
        if(!transactionId.matches(Regex("TEST_[A-Za-z0-9_-]{1,59}")))return ParseResult(ParseStatus.MANUAL_REVIEW,direction=direction,parserVersion=rule.parserVersion)
        val amountMinor=FormatAnalyzer.parseMinor(amounts.single()) ?: return ParseResult(ParseStatus.INVALID,direction=direction,parserVersion=rule.parserVersion)
        val hash=MessageDigest.getInstance("SHA-256").digest(message.body.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        // No provider timestamp format/timezone is approved yet; never substitute local receive time.
        return ParseResult(ParseStatus.PARSED,rule.provider,transactionId,amountMinor,"BDT",providerTimestamp=null,localReceivedAt=message.receivedAt,messageHash=hash,parserVersion=rule.parserVersion,direction=direction)
    }
}

class BkashSmsParser:RuleBasedSmsParser(ProviderRules.bkash)
