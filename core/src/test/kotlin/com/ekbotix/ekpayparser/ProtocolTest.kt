package com.ekbotix.ekpayparser

import org.junit.Test
import org.junit.Assert.*
import com.ekbotix.ekpayparser.crypto.ParserKeyStore
import com.ekbotix.ekpayparser.model.*
import com.ekbotix.ekpayparser.protocol.Protocol
import com.ekbotix.ekpayparser.repository.*
import com.ekbotix.ekpayparser.network.SandboxApi
import com.google.gson.JsonParser
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import java.util.UUID

class TestKeys:ParserKeyStore {
    private val keys=mutableMapOf<String,Ed25519PrivateKeyParameters>()
    override fun generate():String=UUID.randomUUID().toString().also { keys[it]=Ed25519PrivateKeyParameters(SecureRandom()) }
    override fun publicKey(alias:String)=keys.getValue(alias).generatePublicKey().encoded
    override fun sign(alias:String,bytes:ByteArray)=Ed25519Signer().apply{init(true,keys.getValue(alias));update(bytes,0,bytes.size)}.generateSignature()
    override fun delete(alias:String){keys.remove(alias)}
    override fun protection(alias:String)="ephemeral test only"
}
class ProtocolTest {
    private val device="11111111-1111-4111-8111-111111111111"
    @Test fun fixedNodeVectorMatchesKotlinBytesAndEd25519(){
        val vector=JsonParser.parseString(javaClass.getResource("/parser-v1-vector.json")!!.readText()).asJsonObject
        val raw=vector["raw_body"].asString.toByteArray()
        val canonical=Protocol.canonical(device,1,1790550000000,"AAAAAAAAAAAAAAAAAAAAAA","22222222-2222-4222-8222-222222222222",vector["message_hash"].asString,Protocol.EVIDENCE,raw)
        assertEquals(vector["canonical"].asString,canonical.toString(Charsets.UTF_8));assertFalse(canonical.last()==10.toByte())
        assertEquals(vector["body_sha256"].asString,Protocol.sha(raw))
        val pub=Ed25519PublicKeyParameters(hex(vector["public_key_hex"].asString),0)
        val signature=hex(vector["signature_hex"].asString)
        assertTrue(Ed25519Signer().apply {init(false,pub);update(canonical,0,canonical.size)}.verifySignature(signature))
        val changed=canonical.copyOf().also{it[0]=0};assertFalse(Ed25519Signer().apply{init(false,pub);update(changed,0,changed.size)}.verifySignature(signature))
    }
    @Test fun rfc8032Ed25519KnownVector(){
        // Publicly published RFC 8032 test seed, NEVER a device/deployment secret.
        val key=Ed25519PrivateKeyParameters(hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"),0)
        assertEquals("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",Protocol.hex(key.generatePublicKey().encoded))
        assertEquals("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",Protocol.hex(Ed25519Signer().apply{init(true,key)}.generateSignature()))
    }
    @Test fun stableNormalizedMessageHash(){
        val e=Evidence("bkash","TEST_VECTOR",82000,"2026-09-28T00:00:00.000Z","a".repeat(64))
        val body=Protocol.evidenceBody(e);val v=JsonParser.parseString(javaClass.getResource("/parser-v1-vector.json")!!.readText()).asJsonObject
        assertEquals(v["raw_body"].asString,body.toString(Charsets.UTF_8));assertEquals(v["message_hash"].asString,Protocol.messageHash(Protocol.EVIDENCE,body))
        assertEquals("2026-09-28T00:00:00.000Z",Protocol.iso(1790553600000))
    }
    @Test fun requestHeadersNonceAndStableRetryId(){
        val keys=TestKeys();val identity=DeviceIdentity(device,1,keys.generate(),0);val id=Protocol.ingestionId();val raw=Protocol.heartbeatBody("sandbox")
        val one=Protocol.signed(identity,keys,Protocol.HEARTBEAT,raw,id);val two=Protocol.signed(identity,keys,Protocol.HEARTBEAT,raw,id)
        assertEquals(id,one.headers["X-EkPay-Ingestion-Id"]);assertEquals(id,two.headers["X-EkPay-Ingestion-Id"])
        assertNotEquals(one.headers["X-EkPay-Nonce"],two.headers["X-EkPay-Nonce"]);assertEquals(22,one.headers.getValue("X-EkPay-Nonce").length)
        assertEquals("1",one.headers["EkPay-Parser-Protocol"]);assertArrayEquals(raw,one.body)
        assertEquals(43,Protocol.publicEncoding(keys.publicKey(identity.alias)).length)
        assertTrue(ParserKeyStore::class.java.methods.none{it.name.contains("export",true)||it.name.contains("private",true)})
    }
    @Test fun retryClassifierAndBackoff(){
        for(status in listOf(null,500,503))assertEquals(Disposition.RETRY,RetryPolicy.classify(status,null))
        assertEquals(Disposition.RETRY,RetryPolicy.classify(409,"retry_request"));assertEquals(Disposition.PAUSE_IDENTITY,RetryPolicy.classify(401,"invalid_device_request"))
        for(status in listOf(400,404,409,413))assertEquals(Disposition.PERMANENT,RetryPolicy.classify(status,"request_conflict"))
        assertEquals(Disposition.ACCEPTED,RetryPolicy.classify(200,null));assertEquals(30000L,RetryPolicy.backoff(1));assertEquals(3600000L,RetryPolicy.backoff(8))
    }
    @Test fun acceptsNormalizedInternalReferencesButRejectsUnsafeDataAndDestinations(){
        Evidence("bkash","REAL_REFERENCE",1,Protocol.iso(System.currentTimeMillis()),"a".repeat(64))
        assertThrows(IllegalArgumentException::class.java){Evidence("bkash","lowercase",1,Protocol.iso(System.currentTimeMillis()),"a".repeat(64))}
        assertThrows(IllegalArgumentException::class.java){Evidence("bkash","TEST_NEGATIVE",-1,Protocol.iso(System.currentTimeMillis()),"a".repeat(64))}
        for(url in listOf("http://10.0.2.2:3000","https://example.com","https://user@localhost:3443","https://localhost:3443?secret=x"))assertThrows(IllegalArgumentException::class.java){SandboxApi.validateUrl(url,true)}
        SandboxApi.validateUrl("https://10.0.2.2:3443",true);assertThrows(IllegalArgumentException::class.java){SandboxApi.validateUrl("https://localhost:3443",false)}
    }
    private fun hex(s:String)=s.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
}
