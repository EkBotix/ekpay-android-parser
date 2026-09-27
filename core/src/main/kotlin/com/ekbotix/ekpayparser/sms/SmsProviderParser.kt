package com.ekbotix.ekpayparser.sms

interface SmsProviderParser {
    fun supports(sender: String, body: String): Boolean
    fun parse(message: SmsMessageInput): ParseResult
}
