package com.ekbotix.ekpayparser.sms

import org.junit.Assert.*
import org.junit.Test

class UpayRealFormatTest {
    private val pipeline = SmsParserPipeline(listOf(UpaySmsParser()))
    private fun parse(body: String) = pipeline.processApproved("upay", SmsMessageInput("UPAY", body, 1_000L))

    // 1. Upay OTP rejected
    @Test fun testOtpRejected() {
        val sms = "1234 is your One-Time-Password (OTP) for upay. This OTP will be valid for 60 seconds. Please do not share your PIN or OTP with anyone. Hash: TESTHASH"
        val result = parse(sms)
        assertEquals(ParseStatus.IGNORED, result.status)
        assertEquals(TransactionDirection.AUTHENTICATION, result.direction)
    }

    // 2. Cash Reward recognized but rejected as customer-payment evidence, 11. promo/reward-only never becomes payment evidence
    @Test fun testCashRewardRejected() {
        val sms = "Congratulations! You have received Cash Reward of Tk.20.00. TrxID ABC123XYZ9 at 29/08/2026 01:13."
        val result = parse(sms)
        assertEquals(ParseStatus.IGNORED, result.status)
        assertEquals(TransactionDirection.PROMOTIONAL, result.direction)
    }

    // 3. Mobile recharge rejected
    @Test fun testMobileRechargeRejected() {
        val sms = "Mobile recharge of Tk.20.00 for 01XXXXXXXXX is successful. Balance Tk 0.00. TrxID DEF456UVW8 at 29/08/2026 01:14"
        val result = parse(sms)
        assertEquals(ParseStatus.UNSUPPORTED_TYPE, result.status)
        assertEquals(TransactionDirection.OUTGOING, result.direction)
    }

    // 4. amount extracted safely without implying incoming, 5. TrxID extracted safely, 6. timestamp parsing, 7. amount + TrxID without approved incoming phrase rejected
    @Test fun testAmountTrxIdTimestampSafelyExtractedButNotIncoming() {
        val sms = "Random Tk.20.00 TrxID DEF456UVW8 at 29/08/2026 01:14"
        val result = parse(sms)
        assertEquals(ParseStatus.MANUAL_REVIEW, result.status)
        val analysis = FormatAnalyzer.analyze("upay", sms); println("DEBUG_ANALYSIS: amount=${analysis.amountCandidates} txnid=${analysis.transactionIdCandidates} time=${analysis.timestampCandidate}")
        assertEquals(1, analysis.amountCandidates.size)
        assertEquals("20.00", analysis.amountCandidates[0])
        assertEquals(1, analysis.transactionIdCandidates.size)
        assertEquals("DEF456UVW8", analysis.transactionIdCandidates[0])
        assertEquals("2026-08-28T19:14:00.000Z", analysis.timestampCandidate)
    }

    // 8. missing TxnID rejected
    @Test fun testMissingTxnIdRejected() {
        val sms = "Tk.20.00 received. at 29/08/2026 01:14"
        val result = parse(sms)
        assertEquals(ParseStatus.INVALID, result.status)
    }

    // 9. multiple TxnIDs rejected
    @Test fun testMultipleTxnIdRejected() {
        val sms = "Tk.20.00 received. TrxID DEF456UVW8 TrxID GHI789XYZ at 29/08/2026 01:14"
        val result = parse(sms)
        assertEquals(ParseStatus.AMBIGUOUS, result.status)
    }

    // 10. malformed timestamp rejected
    @Test fun testMalformedTimestampRejected() {
        val sms = "Tk.20.00 received. TrxID DEF456UVW8 at 99/99/9999 99:99"
        val result = parse(sms)
        assertEquals(ParseStatus.INVALID, result.status)
    }
}
