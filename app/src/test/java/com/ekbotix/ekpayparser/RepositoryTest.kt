package com.ekbotix.ekpayparser

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import com.ekbotix.ekpayparser.crypto.ParserKeyStore
import com.ekbotix.ekpayparser.data.QueueDatabase
import com.ekbotix.ekpayparser.model.*
import com.ekbotix.ekpayparser.network.ParserApi
import com.ekbotix.ekpayparser.protocol.Protocol
import com.ekbotix.ekpayparser.repository.ParserRepository
import com.ekbotix.ekpayparser.storage.*
import kotlinx.coroutines.runBlocking
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64

private class TestVault:SecureVault {
    val persisted=mutableMapOf<String,String>();private val key=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()
    override fun encrypt(label:String,bytes:ByteArray):String {val c=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key);updateAAD(label.toByteArray())};return Base64.getEncoder().encodeToString(c.iv+c.doFinal(bytes))}
    override fun decrypt(label:String,encrypted:String):ByteArray{val b=Base64.getDecoder().decode(encrypted);return Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,b.copyOfRange(0,12)));updateAAD(label.toByteArray())}.doFinal(b.copyOfRange(12,b.size))}
    override fun write(label:String,value:ByteArray){persisted[label]=encrypt(label,value)}
    override fun read(label:String)=persisted[label]?.let{decrypt(label,it)}
    override fun delete(label:String){persisted.remove(label)}
}
private class MemoryKeys:ParserKeyStore {
    private val keys=mutableMapOf<String,java.security.KeyPair>()
    override fun generate()=UUID.randomUUID().toString().also{keys[it]=KeyPairGenerator.getInstance("Ed25519").generateKeyPair()}
    override fun publicKey(alias:String)=keys.getValue(alias).public.encoded.takeLast(32).toByteArray()
    override fun sign(alias:String,bytes:ByteArray)=Signature.getInstance("Ed25519").apply{initSign(keys.getValue(alias).private);update(bytes)}.sign()
    override fun delete(alias:String){keys.remove(alias)}
    override fun protection(alias:String)="test-memory"
}
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class RepositoryTest {
    private lateinit var db:QueueDatabase;private lateinit var vault:TestVault;private lateinit var state:DeviceState;private lateinit var keys:MemoryKeys;private lateinit var repo:ParserRepository
    private val device="11111111-1111-4111-8111-111111111111"
    private val token="a".repeat(64);private var reply=ApiReply(200,deviceId=device,keyVersion=1);private val requests=mutableListOf<SignedRequest>()
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),QueueDatabase::class.java).allowMainThreadQueries().build();vault=TestVault();state=DeviceState(vault);keys=MemoryKeys()
        repo=ParserRepository(state,keys,db.queue(),vault,object:ParserApi{override fun pair(body:ByteArray)=reply;override fun send(request:SignedRequest):ApiReply{requests+=request;return reply}})}
    @After fun close(){db.close()}
    @Test fun pairedStateNeverPersistsTokenAndRotationSwitchesOnlyAfterSuccess()=runBlocking {
        assertTrue(repo.pair(device,0,token));val first=state.load().identity!!;assertEquals(1,first.keyVersion);assertNull(state.load().pending)
        assertFalse(vault.persisted.values.any{it.contains(token)});assertFalse(vault.read("state.v1")!!.toString(Charsets.UTF_8).contains(token))
        reply=ApiReply(400,"invalid_pairing");assertFalse(repo.pair(device,1,token));assertEquals(first,state.load().identity)
        reply=ApiReply(200,deviceId=device,keyVersion=2);assertTrue(repo.pair(device,1,token));assertEquals(2,state.load().identity!!.keyVersion)
    }
    @Test fun queueDurabilityIdempotencyAndFreshNonceOnRetry()=runBlocking {
        assertTrue(repo.pair(device,0,token));val id=repo.enqueue(Protocol.HEARTBEAT,Protocol.heartbeatBody("sandbox"));reply=ApiReply(503,"internal_error");repo.sync()
        val item=db.queue().all().single();assertEquals(id,item.ingestionId);assertEquals("pending",item.status);assertEquals(1,item.attempts)
        db.queue().result(id,1,0,"pending",null);reply=ApiReply(200);repo.sync();assertEquals("accepted",db.queue().all().single().status)
        assertEquals(id,requests[0].headers["X-EkPay-Ingestion-Id"]);assertEquals(id,requests[1].headers["X-EkPay-Ingestion-Id"]);assertNotEquals(requests[0].headers["X-EkPay-Nonce"],requests[1].headers["X-EkPay-Nonce"])
        val recreated=DeviceState(vault);assertEquals(state.load().identity,recreated.load().identity);assertFalse(item.encryptedBody.contains("sandbox"))
    }
    @Test fun authenticationFailurePausesAllAndRevokedNeverAutoRecovers()=runBlocking {
        assertTrue(repo.pair(device,0,token));repeat(2){repo.enqueue(Protocol.HEARTBEAT,Protocol.heartbeatBody("sandbox"))};reply=ApiReply(401,"invalid_device_request");repo.sync()
        assertEquals("identity_unavailable",state.load().identity!!.state);assertTrue(db.queue().all().all{it.status=="paused"});val n=requests.size;repo.sync();assertEquals(n,requests.size)
        repo.markRevoked();assertEquals("revoked",state.load().identity!!.state)
    }
    @Test fun missingKeyFailsClosedWithoutGeneratingNewActiveKey()=runBlocking {
        assertTrue(repo.pair(device,0,token));repo.enqueue(Protocol.HEARTBEAT,Protocol.heartbeatBody("sandbox"));val alias=state.load().identity!!.alias;keys.delete(alias);repo.sync()
        assertEquals("re_pair_required",state.load().identity!!.state);assertEquals(alias,state.load().identity!!.alias);assertTrue(requests.isEmpty())
    }
}
