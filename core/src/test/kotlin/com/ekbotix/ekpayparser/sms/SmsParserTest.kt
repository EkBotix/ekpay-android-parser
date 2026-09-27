package com.ekbotix.ekpayparser.sms

import org.junit.Test
import org.junit.Assert.*

class SmsParserTest {

    @Test
    fun testBkashParser() {
        val parser = BkashSmsParser()

        // Happy path
        val msg = SmsMessageInput("TEST_BKASH", "You have received Tk 500.50 from 01711. TxnId: TEST_TXN123", 1000L)
        val res = parser.parse(msg)
        assertEquals(ParseStatus.PARSED, res.status)
        assertEquals(50050L, res.amountMinor)
        assertEquals("TEST_TXN123", res.transactionId)

        val msgInt = SmsMessageInput("TEST_BKASH", "You have received Tk 10 from 01711. TxnId: TEST_TXN124", 1000L)
        assertEquals(1000L, parser.parse(msgInt).amountMinor)

        val msgOneCent = SmsMessageInput("TEST_BKASH", "You have received Tk 0.01 from 01711. TxnId: TEST_TXN125", 1000L)
        assertEquals(1L, parser.parse(msgOneCent).amountMinor)

        // Invalid extra decimals
        val msgInvalid = SmsMessageInput("TEST_BKASH", "You have received Tk 10.123 from 01711. TxnId: TEST_TXN126", 1000L)
        assertEquals(ParseStatus.INVALID, parser.parse(msgInvalid).status)

        // Invalid TxnId format (missing TEST_ prefix)
        val msgInvalidTxn = SmsMessageInput("TEST_BKASH", "You have received Tk 500.50 from 01711. TxnId: TXN123", 1000L)
        assertEquals(ParseStatus.INVALID, parser.parse(msgInvalidTxn).status)
    }

    @Test
    fun testPipelineFilters() {
        val pipeline = SmsParserPipeline(listOf(BkashSmsParser(), NagadSmsParser(), RocketSmsParser(), UpaySmsParser()))

        // OTP Ignored
        val msgOtp = SmsMessageInput("TEST_BKASH", "Your bKash verification code is 1234. TxnId: TEST_X", 1000L)
        assertEquals(ParseStatus.IGNORED, pipeline.process(msgOtp).status)

        // Promo Ignored
        val msgPromo = SmsMessageInput("TEST_BKASH", "Get 50% cashback offer. TxnId: TEST_X", 1000L)
        assertEquals(ParseStatus.IGNORED, pipeline.process(msgPromo).status)
    }
}
