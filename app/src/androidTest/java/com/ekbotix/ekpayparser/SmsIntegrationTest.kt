package com.ekbotix.ekpayparser

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import com.ekbotix.ekpayparser.crypto.ParserKeyStore
import com.ekbotix.ekpayparser.data.QueueDatabase
import com.ekbotix.ekpayparser.model.ApiReply
import com.ekbotix.ekpayparser.model.SignedRequest
import com.ekbotix.ekpayparser.network.ParserApi
import com.ekbotix.ekpayparser.repository.ParserRepository
import com.ekbotix.ekpayparser.storage.DeviceState
import com.ekbotix.ekpayparser.storage.SecureVault
import com.ekbotix.ekpayparser.workers.SmsWorkProcessor
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private class InstrumentationVault : SecureVault {
    private val values=mutableMapOf<String,ByteArray>()
    override fun encrypt(label:String,bytes:ByteArray)=Base64.getEncoder().encodeToString(bytes)
    override fun decrypt(label:String,encrypted:String)=Base64.getDecoder().decode(encrypted)
    override fun write(label:String,value:ByteArray){values[label]=value.copyOf()}
    override fun read(label:String)=values[label]?.copyOf()
    override fun delete(label:String){values.remove(label)}
}
private class InstrumentationKeys : ParserKeyStore {
    private val aliases=mutableSetOf<String>()
    override fun generate()=UUID.randomUUID().toString().also(aliases::add)
    override fun publicKey(alias:String)=ByteArray(32).also { check(alias in aliases); it[0]=1 }
    override fun sign(alias:String,bytes:ByteArray)=ByteArray(64).also { check(alias in aliases) }
    override fun delete(alias:String){aliases.remove(alias)}
    override fun protection(alias:String)="instrumentation-fake"
}

@RunWith(AndroidJUnit4::class)
class SmsIntegrationTest {
    private lateinit var db:QueueDatabase
    private lateinit var vault:InstrumentationVault
    private lateinit var state:DeviceState
    private lateinit var keys:InstrumentationKeys
    private lateinit var repository:ParserRepository
    private val device="22222222-2222-4222-8222-222222222222"

    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),QueueDatabase::class.java).build()
        vault=InstrumentationVault();state=DeviceState(vault);keys=InstrumentationKeys()
        repository=ParserRepository(state,keys,db.queue(),vault,object:ParserApi {
            override fun pair(body:ByteArray)=ApiReply(200,deviceId=device,keyVersion=1)
            override fun send(request:SignedRequest)=ApiReply(200)
        })
    }
    @After fun close(){db.close()}

    private fun input(txn:String,hash:String,provider:String="bkash",receivedAt:Long=System.currentTimeMillis()):androidx.work.Data {
        return workDataOf(
            "provider" to provider, "transactionId" to txn, "amountMinor" to 10500L,
            "messageHash" to hash, "receivedAt" to receivedAt, "providerTimestamp" to com.ekbotix.ekpayparser.protocol.Protocol.iso(receivedAt)
        )
    }

    @Test fun workerProcessorAtomicallyEnqueuesAndDedupes()=runBlocking {
        assertTrue(repository.pair(device,0,"a".repeat(64)))
        assertEquals(0,db.queue().totalCount());assertEquals(0,db.queue().dedupeCount())

        val first=SmsWorkProcessor.run(input("TEST_INT123","1".repeat(64)),repository)
        assertEquals(ListenableWorker.Result.success(),first)
        assertEquals(1,db.queue().totalCount());assertEquals(1,db.queue().dedupeCount())
        assertTrue(db.queue().hasDedupe("bkash","TEST_INT123","1".repeat(64)))

        val duplicate=SmsWorkProcessor.run(input("TEST_INT123","1".repeat(64)),repository)
        assertEquals(ListenableWorker.Result.success(),duplicate)
        assertEquals(1,db.queue().totalCount());assertEquals(1,db.queue().dedupeCount())
    }

    @Test fun failedEnqueueCreatesNoDedupeAndCanBeRetried()=runBlocking {
        val value=input("TEST_RETRY123","2".repeat(64))
        val beforeQueue=db.queue().totalCount();val beforeDedupe=db.queue().dedupeCount()
        assertEquals(ListenableWorker.Result.retry(),SmsWorkProcessor.run(value,repository))
        assertEquals(beforeQueue,db.queue().totalCount());assertEquals(beforeDedupe,db.queue().dedupeCount())
        assertFalse(db.queue().hasDedupe("bkash","TEST_RETRY123","2".repeat(64)))

        assertTrue(repository.pair(device,0,"b".repeat(64)))
        assertEquals(ListenableWorker.Result.success(),SmsWorkProcessor.run(value,repository))
        assertEquals(beforeQueue+1,db.queue().totalCount());assertEquals(beforeDedupe+1,db.queue().dedupeCount())
        assertTrue(db.queue().hasDedupe("bkash","TEST_RETRY123","2".repeat(64)))
    }

    @Test fun dedupeScopesTransactionByProviderAndHashAcrossMessages()=runBlocking {
        assertTrue(repository.pair(device,0,"c".repeat(64)))
        assertEquals(ListenableWorker.Result.success(),SmsWorkProcessor.run(input("TEST_SHARED","3".repeat(64),"bkash"),repository))
        assertEquals(ListenableWorker.Result.success(),SmsWorkProcessor.run(input("TEST_SHARED","4".repeat(64),"nagad"),repository))
        assertEquals(2,db.queue().totalCount())
        // Same provider/transaction remains a duplicate even when whitespace changes the body hash.
        assertEquals(ListenableWorker.Result.success(),SmsWorkProcessor.run(input("TEST_SHARED","5".repeat(64),"bkash"),repository))
        // The same body hash is duplicate even under another provider/transaction and timestamp.
        val now=System.currentTimeMillis()
        assertEquals(ListenableWorker.Result.success(),SmsWorkProcessor.run(input("TEST_OTHER","3".repeat(64),"rocket",now),repository))
        assertEquals(2,db.queue().totalCount())

        val firstMultipart=com.ekbotix.ekpayparser.sms.MultipartAssembler.assemble(listOf(
            com.ekbotix.ekpayparser.sms.SmsPart("TEST_UPAY","Payment received Tk 1.00. ",now+1_000L),
            com.ekbotix.ekpayparser.sms.SmsPart("TEST_UPAY","TrxID TEST_MULTI",now+1_001L)
        ))!!
        val redelivery=com.ekbotix.ekpayparser.sms.MultipartAssembler.assemble(listOf(
            com.ekbotix.ekpayparser.sms.SmsPart("TEST_UPAY","Payment received Tk 1.00. ",now+2_000L),
            com.ekbotix.ekpayparser.sms.SmsPart("TEST_UPAY","TrxID TEST_MULTI",now+2_001L)
        ))!!
        val parser=com.ekbotix.ekpayparser.sms.UpaySmsParser()
        val firstHash=parser.parse(firstMultipart).messageHash!!
        val redeliveryHash=parser.parse(redelivery).messageHash!!
        assertEquals(firstHash,redeliveryHash)
        assertEquals(ListenableWorker.Result.success(),SmsWorkProcessor.run(input("TEST_MULTI",firstHash,"upay",now+1_000L),repository))
        assertEquals(ListenableWorker.Result.success(),SmsWorkProcessor.run(input("TEST_MULTI",redeliveryHash,"upay",now+2_000L),repository))
        assertEquals(3,db.queue().totalCount())
        assertEquals(3,db.queue().dedupeCount())
    }
}
