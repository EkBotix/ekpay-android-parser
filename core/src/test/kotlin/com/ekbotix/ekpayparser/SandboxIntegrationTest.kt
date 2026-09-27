package com.ekbotix.ekpayparser

import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import com.ekbotix.ekpayparser.network.SandboxApi
import com.ekbotix.ekpayparser.model.*
import com.ekbotix.ekpayparser.protocol.Protocol
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.*

class SandboxIntegrationTest {
    @Test fun realKotlinClientPairsSignsRetriesRotatesAndRejectsAdversarialRequests(){
        val base=System.getenv("EKPAY_SANDBOX_INTEGRATION_URL");assumeTrue("Run backend test:phase7 for disposable TLS + native PostgreSQL integration",base!=null)
        val certificate=CertificateFactory.getInstance("X.509").generateCertificate(File(System.getenv("EKPAY_SANDBOX_CA")).inputStream())
        val trust=KeyStore.getInstance(KeyStore.getDefaultType()).apply{load(null);setCertificateEntry("disposable-sandbox-only",certificate)}
        val tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply{init(trust)}.trustManagers.filterIsInstance<X509TrustManager>().single()
        val tls=SSLContext.getInstance("TLS").apply{init(null,arrayOf(tm),null)}
        // Trust this exact generated CA; keep standard TLS hostname verification. Never trust-all.
        val client=SandboxApi.secureClient().newBuilder().sslSocketFactory(tls.socketFactory,tm).build()
        val api=SandboxApi(base!!,true,true,client)
        fun control(path:String,body:String="{}"):com.google.gson.JsonObject {
            val request=Request.Builder().url(base+path).post(body.toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use{r->assertEquals(200,r.code);return JsonParser.parseString(r.body!!.string()).asJsonObject}
        }
        val registration=control("/sandbox/test/register");val device=registration["device_id"].asString;val token=registration["pairing_token"].asString
        val keys=TestKeys();val alias=keys.generate()
        val paired=api.pair(Protocol.pairBody(device,0,token,alias,keys));assertEquals(200,paired.status);assertEquals(1,paired.keyVersion)
        assertEquals(400,api.pair(Protocol.pairBody(device,0,token,alias,keys)).status)
        val identity=DeviceIdentity(device,1,alias,System.currentTimeMillis());val beat=Protocol.signed(identity,keys,Protocol.HEARTBEAT,Protocol.heartbeatBody("synthetic-kotlin"),Protocol.ingestionId())
        assertEquals(200,api.send(beat).status)
        val evidence=Protocol.evidenceBody(Evidence("bkash","TEST_ANDROID_INTEGRATION",82000,Protocol.iso(System.currentTimeMillis()),"a".repeat(64)));val id=Protocol.ingestionId()
        val signed=Protocol.signed(identity,keys,Protocol.EVIDENCE,evidence,id);val accepted=api.send(signed)
        assertEquals(200,accepted.status);assertNotNull(accepted.evidenceId)
        assertEquals(accepted.evidenceId,api.send(Protocol.signed(identity,keys,Protocol.EVIDENCE,evidence,id)).evidenceId)
        assertEquals(accepted.evidenceId,api.send(signed).evidenceId) // exact replay is cached, no second acceptance
        val bad=signed.copy(headers=signed.headers+("X-EkPay-Signature" to "0".repeat(128)));assertEquals(401,api.send(bad).status)
        assertEquals(401,api.send(Protocol.signed(identity,keys,Protocol.EVIDENCE,evidence,Protocol.ingestionId(),System.currentTimeMillis()-301000)).status)
        assertEquals(401,api.send(Protocol.signed(identity.copy(keyVersion=2),keys,Protocol.EVIDENCE,evidence,Protocol.ingestionId())).status)
        val changed=Protocol.evidenceBody(Evidence("bkash","TEST_OTHER",82001,Protocol.iso(System.currentTimeMillis()),"a".repeat(64)))
        assertEquals(409,api.send(Protocol.signed(identity,keys,Protocol.EVIDENCE,changed,Protocol.ingestionId(),nonce=signed.headers.getValue("X-EkPay-Nonce"))).status)
        val rotation=control("/sandbox/test/rotation","{\"device_id\":\"$device\",\"expected_version\":1}");val next=keys.generate()
        assertEquals(200,api.pair(Protocol.pairBody(device,1,rotation["pairing_token"].asString,next,keys)).status)
        assertEquals(401,api.send(signed).status)
        val rotated=identity.copy(alias=next,keyVersion=2);assertEquals(200,api.send(Protocol.signed(rotated,keys,Protocol.HEARTBEAT,Protocol.heartbeatBody("rotated"),Protocol.ingestionId())).status)
        control("/sandbox/test/revoke","{\"device_id\":\"$device\"}")
        assertEquals(401,api.send(Protocol.signed(rotated,keys,Protocol.HEARTBEAT,Protocol.heartbeatBody("revoked"),Protocol.ingestionId())).status)
        assertEquals(401,api.send(Protocol.signed(rotated,keys,Protocol.EVIDENCE,evidence,Protocol.ingestionId())).status)
    }
}
