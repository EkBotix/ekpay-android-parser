package com.ekbotix.ekpayparser.sms

import org.junit.Assert.*
import org.junit.Test

class NagadRealFormatTest {
    private val pipeline = SmsParserPipeline(listOf(NagadSmsParser()))
    private fun parse(body: String) = pipeline.processApproved("nagad", SmsMessageInput("NAGAD", body, 1_000L))

    // 1. Money Received accepted, 3. primary Amount parsed exactly, 4. Balance excluded,
    // 5. phone number not interpreted as amount, 6. Ref: N/A accepted and NOT used as TxnID, 7. TxnID parsed
    @Test fun testMoneyReceivedAccepted() {
        val sms = "Money Received.\nAmount: Tk 123.45\nSender: 01XXXXXXXXX\nRef: N/A\nTxnID: ABC123XYZ9\nBalance: Tk 999.99\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.PARSED, result.status)
        assertEquals(TransactionDirection.INCOMING, result.direction)
        assertEquals("ABC123XYZ9", result.transactionId)
        assertEquals(12345L, result.amountMinor)
        assertEquals("2025-10-05T08:52:00.000Z", result.providerTimestamp) // 14:52 Asia/Dhaka -> 08:52 UTC
    }

    // 2. Cash In Received accepted
    @Test fun testCashInReceivedAccepted() {
        val sms = "Cash In Received.\nAmount: Tk 200.00\nUddokta: 01XXXXXXXXX\nTxnID: DEF456UVW8\nBalance: 500.00\n04/12/2025 21:48"
        val result = parse(sms)
        assertEquals(ParseStatus.PARSED, result.status)
        assertEquals(TransactionDirection.INCOMING, result.direction)
        assertEquals("DEF456UVW8", result.transactionId)
        assertEquals(20000L, result.amountMinor)
        assertEquals("2025-12-04T15:48:00.000Z", result.providerTimestamp)
    }

    // 8. whitespace/newline variation
    @Test fun testWhitespaceNewlineVariation() {
        val sms = "Money Received. Amount:   Tk 123.45\nSender: 01XXXXXXXXX Ref: N/A TxnID: ABC123XYZ9 Balance: Tk 999.99 05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.PARSED, result.status)
        assertEquals(12345L, result.amountMinor)
        assertEquals("ABC123XYZ9", result.transactionId)
    }

    // 9. missing Amount rejected
    @Test fun testMissingAmountRejected() {
        val sms = "Money Received.\nSender: 01XXXXXXXXX\nRef: N/A\nTxnID: ABC123XYZ9\nBalance: Tk 999.99\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.INVALID, result.status)
    }

    // 10. malformed Amount rejected
    @Test fun testMalformedAmountRejected() {
        val sms = "Money Received.\nAmount: Tk ABC\nSender: 01XXXXXXXXX\nRef: N/A\nTxnID: ABC123XYZ9\nBalance: Tk 999.99\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.INVALID, result.status)
    }

    // 11. duplicate/conflicting Amount rejected
    @Test fun testDuplicateAmountRejected() {
        val sms = "Money Received.\nAmount: Tk 123.45\nAmount: Tk 200.00\nSender: 01XXXXXXXXX\nRef: N/A\nTxnID: ABC123XYZ9\nBalance: Tk 999.99\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.AMBIGUOUS, result.status)
    }

    // 12. missing TxnID rejected
    @Test fun testMissingTxnIdRejected() {
        val sms = "Money Received.\nAmount: Tk 123.45\nSender: 01XXXXXXXXX\nRef: N/A\nBalance: Tk 999.99\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.INVALID, result.status)
    }

    // 13. multiple/conflicting TxnIDs rejected
    @Test fun testMultipleTxnIdRejected() {
        val sms = "Money Received.\nAmount: Tk 123.45\nSender: 01XXXXXXXXX\nRef: N/A\nTxnID: ABC123XYZ9\nTxnID: DEF456XYZ9\nBalance: Tk 999.99\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.AMBIGUOUS, result.status)
    }

    // 14. malformed timestamp rejected
    @Test fun testMalformedTimestampRejected() {
        val sms = "Money Received.\nAmount: Tk 123.45\nSender: 01XXXXXXXXX\nRef: N/A\nTxnID: ABC123XYZ9\nBalance: Tk 999.99\n99/99/9999 99:99"
        val result = parse(sms)
        assertEquals(ParseStatus.INVALID, result.status) // invalid regex match or parse logic
    }

    // 15. multiple/ambiguous timestamps rejected
    @Test fun testAmbiguousTimestampsRejected() {
        val sms = "Money Received.\nAmount: Tk 123.45\nSender: 01XXXXXXXXX\nRef: N/A\nTxnID: ABC123XYZ9\nBalance: Tk 999.99\n05/10/2025 14:52\n06/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.INVALID, result.status)
    }

    // 16. OTP/PIN message rejected
    @Test fun testOtpRejected() {
        val sms = "NEVER share your OTP or PIN with anyone. Nagad will never ask for these."
        val result = parse(sms)
        assertEquals(ParseStatus.IGNORED, result.status)
        assertEquals(TransactionDirection.AUTHENTICATION, result.direction)
    }

    // 17. Send Money/outgoing rejected (Safety Test)
    @Test fun testSendMoneyRejectedSafety() {
        val sms = "Send Money.\nAmount: Tk 100.00\nTxnID: OUT123XYZ9\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.UNSUPPORTED_TYPE, result.status)
        assertEquals(TransactionDirection.OUTGOING, result.direction)
    }

    // 18. Payment/merchant-payment rejected (Safety Test)
    @Test fun testPaymentRejectedSafety() {
        val sms = "Payment of Tk 100.00 to Merchant.\nAmount: Tk 100.00\nTxnID: OUT123XYZ9\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.UNSUPPORTED_TYPE, result.status)
        assertEquals(TransactionDirection.OUTGOING, result.direction)
    }

    // 19. Cash Out rejected (Safety Test)
    @Test fun testCashOutRejectedSafety() {
        val sms = "Cash Out.\nAmount: Tk 100.00\nTxnID: OUT123XYZ9\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.UNSUPPORTED_TYPE, result.status)
        assertEquals(TransactionDirection.OUTGOING, result.direction)
    }

    // 20. refund/reversal rejected (Safety Test)
    @Test fun testRefundRejectedSafety() {
        val sms = "Refund Received.\nAmount: Tk 100.00\nTxnID: REF123XYZ9\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.UNSUPPORTED_TYPE, result.status)
        assertEquals(TransactionDirection.REFUND_OR_REVERSAL, result.direction)
    }

    // 21. failed/cancelled rejected (Safety Test)
    @Test fun testFailedRejectedSafety() {
        val sms = "Money Received.\nFailed.\nAmount: Tk 100.00\nTxnID: FAIL123XYZ\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.UNSUPPORTED_TYPE, result.status)
    }

    // 22. promo rejected (Safety Test)
    @Test fun testPromoRejectedSafety() {
        val sms = "Cashback offer! Get 50% off on your next payment.\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.IGNORED, result.status)
        assertEquals(TransactionDirection.PROMOTIONAL, result.direction)
    }

    // 23. balance-only rejected (Safety Test)
    @Test fun testBalanceOnlyRejectedSafety() {
        val sms = "Current balance.\nBalance: Tk 999.99\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.IGNORED, result.status)
        assertEquals(TransactionDirection.BALANCE_ONLY, result.direction)
    }

    // 24. valid-looking Amount + TxnID without an approved incoming phrase rejected (Safety Test)
    @Test fun testValidAmountAndTxnIdWithoutIncomingPhraseRejectedSafety() {
        val sms = "Random Message.\nAmount: Tk 123.45\nTxnID: RND123XYZ9\n05/10/2025 14:52"
        val result = parse(sms)
        assertEquals(ParseStatus.MANUAL_REVIEW, result.status)
        assertNotEquals(TransactionDirection.INCOMING, result.direction)
    }
}
