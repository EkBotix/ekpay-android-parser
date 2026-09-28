package com.ekbotix.ekpayparser.sms

import org.junit.Assert.*
import org.junit.Test

class SmsParserTest {
    private val pipeline=SmsParserPipeline(listOf(BkashSmsParser(),NagadSmsParser(),RocketSmsParser(),UpaySmsParser()))
    private fun parse(sender:String,body:String)=pipeline.process(SmsMessageInput(sender,body,1_000L))

    @Test fun syntheticProviderRulesParseOnlyExplicitIncomingTypes() {
        val cases=listOf(
            "TEST_BKASH" to "You have received Tk 500.50. TxnId: TEST_BK1",
            "TEST_NAGAD" to "Money received. Amount: Tk 10.00. TxnID: TEST_NG1",
            "TEST_ROCKET" to "You received Tk 20.00. TxnId: TEST_RK1",
            "TEST_UPAY" to "Payment received Tk 30.00. TrxID TEST_UP1"
        )
        cases.forEach { (sender,body) ->
            val result=parse(sender,body);assertEquals(ParseStatus.PARSED,result.status)
            assertEquals(TransactionDirection.INCOMING,result.direction);assertNull(result.providerTimestamp)
            assertTrue(result.transactionId!!.startsWith("TEST_"));assertNotNull(result.messageHash)
        }
        assertEquals(50050L,parse(cases[0].first,cases[0].second).amountMinor)
        assertEquals(ParseStatus.UNSUPPORTED_SENDER,parse("bKash","You have received Tk 1.00. TxnId: TEST_X").status)
        assertEquals(SenderRegistryState.UNVERIFIED,SenderRegistry.state("bKash"))
    }

    @Test fun amountSelectionRejectsAmbiguityButExcludesFeeAndBalance() {
        val safe=parse("TEST_BKASH","You have received Tk 100.00. Fee Tk 2.00. Balance Tk 900.00. TxnId: TEST_SAFE")
        assertEquals(ParseStatus.PARSED,safe.status);assertEquals(10_000L,safe.amountMinor)
        assertEquals(ParseStatus.AMBIGUOUS,parse("TEST_BKASH","You have received Tk 100.00 and Tk 200.00. TxnId: TEST_AMT").status)
        assertEquals(ParseStatus.AMBIGUOUS,parse("TEST_BKASH","You have received Tk 100.00. TxnId: TEST_A TxnId: TEST_B").status)
        assertEquals(ParseStatus.INVALID,parse("TEST_BKASH","You have received Tk 10.123. TxnId: TEST_DEC").status)
    }

    @Test fun authenticationPromotionBalanceAndDirectionsFailSafe() {
        listOf("OTP 1234 do not share","Your verification code expires soon","আপনার ওটিপি ১২৩৪ শেয়ার করবেন না").forEach {
            assertEquals(ParseStatus.IGNORED,parse("TEST_BKASH",it).status)
        }
        listOf("Cashback campaign offer","Get a discount voucher reward","ক্যাশব্যাক অফার").forEach {
            assertEquals(ParseStatus.IGNORED,parse("TEST_BKASH",it).status)
        }
        assertEquals(ParseStatus.IGNORED,parse("TEST_BKASH","Your current balance is Tk 20.00").status)
        listOf("Money sent Tk 10.00 TxnId: TEST_S","Cash out Tk 10.00 TxnId: TEST_C","Payment refunded Tk 10.00 TxnId: TEST_R","Transaction reversed Tk 10.00 TxnId: TEST_V","Payment failed Tk 10.00 TxnId: TEST_F").forEach {
            assertEquals(ParseStatus.UNSUPPORTED_TYPE,parse("TEST_BKASH",it).status)
        }
    }

    @Test fun unicodeNormalizationSupportsCandidatesWithoutProviderClaims() {
        val result=parse("test_bkash","You have received\u00a0Tk ১২৩.৪৫ — TxnId： TEST_UNICODE")
        assertEquals(ParseStatus.PARSED,result.status);assertEquals(12_345L,result.amountMinor)
        val lab=FormatAnalyzer.analyze("bkash","Received Tk 100.00 from 01XXXXXXXXX. TxnId XXXXXXXX.")
        assertEquals(ParseStatus.MANUAL_REVIEW,lab.status);assertEquals(listOf("100.00"),lab.amountCandidates)
        assertEquals(listOf("XXXXXXXX"),lab.transactionIdCandidates);assertNull(lab.timestampCandidate)
    }

    @Test fun multipartIsBoundedOrderedAndSenderConsistent() {
        val assembled=MultipartAssembler.assemble(listOf(SmsPart("TEST_BKASH","You have received ",1000),SmsPart("test_bkash","Tk 1.00. TxnId: TEST_M",1001)))
        assertEquals("You have received Tk 1.00. TxnId: TEST_M",assembled!!.body)
        assertNull(MultipartAssembler.assemble(listOf(SmsPart("TEST_BKASH","a",1000),SmsPart("TEST_NAGAD","b",1001))))
        assertNull(MultipartAssembler.assemble(listOf(SmsPart("TEST_BKASH","a",1000),SmsPart("TEST_BKASH","b",200_000))))
        assertNull(MultipartAssembler.assemble(List(11){SmsPart("TEST_BKASH","x",1000)}))
    }

    @Test fun messageHashIsRawBodyStableAndNotAuthenticityProof() {
        val body="You have received Tk 1.00. TxnId: TEST_HASH"
        val first=parse("TEST_BKASH",body);val retry=parse("TEST_BKASH",body)
        assertEquals(first.messageHash,retry.messageHash)
        assertNotEquals(first.messageHash,parse("TEST_BKASH",body.replace(" ","  ")).messageHash)
    }

    @Test fun senderNormalizationDoesNotCollapseLocalAndCountryCodeForms() {
        assertEquals("+8801712345678",SenderRegistry.normalize("880 1712-345678"))
        assertEquals("01712345678",SenderRegistry.normalize("01712 345678"))
        assertNotEquals(SenderRegistry.normalize("01712345678"),SenderRegistry.normalize("+8801712345678"))
        assertEquals("TEST_BKASH",SenderRegistry.normalize("test_bkash"))
    }
}
