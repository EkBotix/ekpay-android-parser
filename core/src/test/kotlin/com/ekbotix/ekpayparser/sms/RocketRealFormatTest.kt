package com.ekbotix.ekpayparser.sms

import org.junit.Assert.*
import org.junit.Test

class RocketRealFormatTest {
    private val pipeline = SmsParserPipeline(listOf(RocketSmsParser()))
    private fun parse(body: String) = pipeline.processApproved("rocket", SmsMessageInput("16216", body, 1_000L))
    private fun parseUnapproved(body: String) = pipeline.process(SmsMessageInput("16216", body, 1_000L))

    // 1. Rocket OTP/security code rejected
    @Test fun testOtpRejected() {
        val sms = "<#>Do not share PIN and security code with others. DBBL never asks for those. Your Rocket security code is 123456. Validity 30 seconds. UID:TESTUID123"
        val result = parse(sms)
        assertEquals(ParseStatus.IGNORED, result.status)
        assertEquals(TransactionDirection.AUTHENTICATION, result.direction)
    }

    // 2. Rocket payment/outgoing rejected
    @Test fun testOutgoingPaymentRejected() {
        val sms = "Tk200.00 paid to TEST MERCHANT Bill No TEST123 TxnId:ABC123XYZ9 Date:04-JUL-26 11:27:08 pm"
        val result = parse(sms)
        assertEquals(ParseStatus.UNSUPPORTED_TYPE, result.status)
        assertEquals(TransactionDirection.OUTGOING, result.direction)
    }

    // 3. Rocket amount can be recognized without creating incoming evidence, 4. Rocket TxnId can be recognized, 5. Rocket Date format parses safely, 6. amount + TxnId without approved incoming phrase rejected
    @Test fun testAmountTxnIdDateRecognizedButNotIncoming() {
        val sms = "Random string Tk 200.00 TxnId: ABC123XYZ9 Date:04-JUL-26 11:27:08 pm"
        val result = parse(sms)
        assertEquals(ParseStatus.MANUAL_REVIEW, result.status)
        // Verify FormatAnalyzer extracts them correctly without incoming bias
        val analysis = FormatAnalyzer.analyze("rocket", sms)
        assertEquals(1, analysis.amountCandidates.size)
        assertEquals("200.00", analysis.amountCandidates[0])
        assertEquals(1, analysis.transactionIdCandidates.size)
        assertEquals("ABC123XYZ9", analysis.transactionIdCandidates[0])
        assertEquals("2026-07-04T17:27:08.000Z", analysis.timestampCandidate)
    }

    // 7. missing/ambiguous TxnId rejected
    @Test fun testMissingTxnIdRejected() {
        val sms = "Tk 200.00 received. Date:04-JUL-26 11:27:08 pm"
        val result = parse(sms)
        assertEquals(ParseStatus.INVALID, result.status)
    }

    // 8. malformed timestamp rejected
    @Test fun testMalformedTimestampRejected() {
        // "received" makes it an incoming candidate, so it will reach the parser.
        val sms = "Tk 200.00 received. TxnId:ABC123XYZ9 Date:99-JUL-26 99:99:99 pm"
        val result = parse(sms)
        assertEquals(ParseStatus.INVALID, result.status)
    }

    // 9. promo/balance-only rejected if applicable
    @Test fun testPromoRejected() {
        val sms = "Cashback reward of Tk 20.00 received. TxnId:ABC Date:04-JUL-26 11:27:08 pm"
        val result = parse(sms)
        assertEquals(ParseStatus.IGNORED, result.status)
        assertEquals(TransactionDirection.PROMOTIONAL, result.direction)
    }
}
