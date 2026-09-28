package com.ekbotix.ekpayparser.sms
import org.junit.Assert.*
import org.junit.Test

class BkashRealFormatTest {
    private val pipeline = SmsParserPipeline(listOf(BkashSmsParser()))
    // Use parseApproved to test the rule even though BKASH sender is OBSERVED, not internally approved globally.
    // Wait, processApproved takes provider name.
    private fun parse(body: String) = pipeline.processApproved("bkash", SmsMessageInput("bKash", body, 1_000L))

    @Test fun testReceiveMoneyAccepted() {
        val sms = "You have received Tk 123.45 from 01XXXXXXXXX. Fee Tk 0.00. Balance Tk 999.99. TrxID ABC123XYZ9 at 28/09/2026 17:18"
        val result = parse(sms)
        assertEquals(ParseStatus.PARSED, result.status)
        assertEquals(TransactionDirection.INCOMING, result.direction)
        assertEquals("ABC123XYZ9", result.transactionId)
        assertEquals(12345L, result.amountMinor)
        assertEquals("2026-09-28T11:18:00.000Z", result.providerTimestamp) // 17:18 in Asia/Dhaka is 11:18 UTC
    }

    @Test fun testCashInAccepted() {
        val sms = "Cash In Tk 200.00 from 01XXXXXXXXX successful. Fee Tk 0.00. Balance Tk 999.99. TrxID DEF456UVW8 at 15/09/2026 20:14."
        val result = parse(sms)
        assertEquals(ParseStatus.PARSED, result.status)
        assertEquals(TransactionDirection.INCOMING, result.direction)
        assertEquals("DEF456UVW8", result.transactionId)
        assertEquals(20000L, result.amountMinor)
        assertEquals("2026-09-15T14:14:00.000Z", result.providerTimestamp)
    }

    @Test fun testSendMoneyRejected() {
        val sms = "Send Money Tk 100.00 to 01XXXXXXXXX successful. Ref test. Fee Tk 0.00. Balance Tk 500.00. TrxID OUT123TEST at 28/09/2026 17:16"
        val result = parse(sms)
        assertEquals(ParseStatus.UNSUPPORTED_TYPE, result.status)
    }

    @Test fun testPaymentRejected() {
        val sms = "Payment of Tk 500.00 to TEST MERCHANT is successful. Balance Tk 100.00. TrxID PAY123TEST at 13/09/2026 14:13"
        val result = parse(sms)
        assertEquals(ParseStatus.UNSUPPORTED_TYPE, result.status)
    }

    @Test fun testOtpRejected() {
        val sms = "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk 500.00 to TEST MERCHANT is 123456. Expires in 2 min."
        val result = parse(sms)
        assertEquals(ParseStatus.IGNORED, result.status)
        assertEquals(TransactionDirection.AUTHENTICATION, result.direction)
    }

    @Test fun testCashOutRejected() {
        val sms = "Cash Out Tk 500.00 to 01XXXXXXXXX successful. Fee Tk 0.00. Balance Tk 100.00. TrxID CASHOUT123 at 13/09/2026 14:13"
        val result = parse(sms)
        assertEquals(ParseStatus.UNSUPPORTED_TYPE, result.status)
    }
}

