package com.ekbotix.ekpayparser.repository

import com.ekbotix.ekpayparser.crypto.ParserKeyStore
import com.ekbotix.ekpayparser.data.*
import com.ekbotix.ekpayparser.diagnostics.SafeDiagnostics
import com.ekbotix.ekpayparser.model.*
import com.ekbotix.ekpayparser.network.ParserApi
import com.ekbotix.ekpayparser.protocol.Protocol
import com.ekbotix.ekpayparser.storage.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ParserRepository(val state:DeviceState, val keys:ParserKeyStore, val queue:QueueDao, private val protected:SecureVault, private val api:ParserApi) {
    private val mutex=Mutex()
    suspend fun pair(device:String,version:Int,token:String):Boolean=mutex.withLock {
        val current=try {
            require(token.matches(Regex("[a-f0-9]{64}")));require(version in 0..2147483646)
            val loaded=state.load()
            check(loaded.identity?.state!="revoked" || loaded.identity.deviceId!=device) { "Revoked identity requires new registration" }
            check(loaded.recoveryAliases.size<4) { "Recovery history requires operator review" }
            SafeDiagnostics.pairing(loaded.debugLogging,"VALIDATION","ok")
            loaded
        } catch(e:Exception) {
            SafeDiagnostics.pairing(runCatching { state.load().debugLogging }.getOrDefault(false),"VALIDATION",pairingCategory(e))
            throw e
        }
        // Pairing crash/retry preserves a candidate alias, never creates silent active replacement.
        val pending=try {
            current.pending?.takeIf { it.deviceId==device && it.expectedVersion==version }
                ?: PendingKey(device,version,keys.generate())
        } catch(e:Exception) {
            SafeDiagnostics.pairing(current.debugLogging,"KEY",pairingCategory(e))
            throw e
        }
        val recovery=current.recoveryAliases+listOfNotNull(current.pending?.takeIf { it.alias!=pending.alias }?.alias)
        try {
            state.save(current.copy(pending=pending,recoveryAliases=recovery,summary="Pairing in progress; no ingestion"))
            SafeDiagnostics.pairing(current.debugLogging,"STATE_SAVE","ok")
        } catch(e:Exception) {
            SafeDiagnostics.pairing(current.debugLogging,"STATE_SAVE",pairingCategory(e))
            throw e
        }
        val raw=try {
            Protocol.pairBody(device,version,token,pending.alias,keys).also { SafeDiagnostics.pairing(current.debugLogging,"SIGNING","ok") }
        } catch(e:Exception) {
            SafeDiagnostics.pairing(current.debugLogging,"SIGNING",pairingCategory(e))
            throw e
        }
        val reply=try { api.pair(raw) } catch (e:Exception) {
            SafeDiagnostics.pairing(current.debugLogging,"NETWORK",pairingCategory(e))
            try { state.save(state.load().copy(summary="Network unavailable; candidate preserved; re-pair token required")) }
            catch(save:Exception) { SafeDiagnostics.pairing(current.debugLogging,"STATE_SAVE",pairingCategory(save)) }
            return@withLock false
        } finally { raw.fill(0) }
        val httpCategory=when(reply.status) { in 200..299->"http_2xx"; in 400..499->"http_4xx"; in 500..599->"http_5xx"; else->"http_other" }
        SafeDiagnostics.pairing(current.debugLogging,"HTTP",httpCategory)
        if(reply.status !in 200..299 || reply.deviceId!=device || reply.keyVersion!=version+1){
            SafeDiagnostics.pairing(current.debugLogging,"RESPONSE","response_mismatch")
            try { state.save(state.load().copy(summary="Pairing unavailable; token invalid/expired/used or request rejected")) }
            catch(save:Exception) { SafeDiagnostics.pairing(current.debugLogging,"STATE_SAVE",pairingCategory(save));throw save }
            return@withLock false
        }
        SafeDiagnostics.pairing(current.debugLogging,"RESPONSE","ok")
        val old=current.identity
        try {
            state.save(current.copy(identity=DeviceIdentity(device,version+1,pending.alias,System.currentTimeMillis(),provider=reply.provider,accountDisplay=reply.accountDisplay),pending=null,recoveryAliases=emptyList(),summary="Paired in TEST environment"))
            SafeDiagnostics.pairing(current.debugLogging,"FINAL_STATE","ok")
        } catch(e:Exception) {
            SafeDiagnostics.pairing(current.debugLogging,"FINAL_STATE",pairingCategory(e))
            throw e
        }
        if(old!=null && old.alias!=pending.alias)runCatching { keys.delete(old.alias) }
        recovery.filter{it!=pending.alias}.forEach { runCatching { keys.delete(it) } }
        true
    }
    private fun pairingCategory(error:Exception):String=when(error) {
        is IllegalArgumentException->"invalid_input"
        is IllegalStateException->"state_conflict"
        is SecurityException->"security"
        is javax.net.ssl.SSLException->"tls"
        is java.io.IOException->"io"
        else->"unexpected"
    }
    suspend fun enqueue(path:String,body:ByteArray):String=mutex.withLock {
        val current=state.load();val identity=current.identity?:error("Pairing required")
        check(current.pending==null && identity.state=="active" && identity.environment=="test")
        keys.publicKey(identity.alias);queue.prune(System.currentTimeMillis()-86_400_000);check(queue.totalCount()<100)
        val id=Protocol.ingestionId();queue.add(QueueItem(id,identity.deviceId,path,protected.encrypt("queue:$id",body),System.currentTimeMillis()));id
    }
    suspend fun enqueueSms(path:String,body:ByteArray,dedupe:DedupeItem):Boolean=mutex.withLock {
        val current=state.load();val identity=current.identity?:error("Pairing required")
        check(current.pending==null && identity.state=="active" && identity.environment=="test")
        keys.publicKey(identity.alias)
        val now=System.currentTimeMillis();queue.prune(now-86_400_000);queue.pruneDedupe(now-7*24*60*60*1000L)
        val id=Protocol.ingestionId()
        val item=QueueItem(id,identity.deviceId,path,protected.encrypt("queue:$id",body),now)
        queue.enqueueAndDedupeIfNew(item,dedupe.copy(ingestionId=id))
    }
    suspend fun sync()=mutex.withLock {
        val current=state.load();val identity=current.identity
        if(identity==null || identity.state!="active" || current.pending!=null)return@withLock
        try { keys.publicKey(identity.alias) } catch (_:Exception) { stop("re_pair_required");return@withLock }
        val now=System.currentTimeMillis();queue.prune(now-86_400_000)
        for(item in queue.due(now)){
            if(item.deviceId!=identity.deviceId || now-item.createdAt>86_400_000 || item.attempts>=RetryPolicy.MAX_ATTEMPTS){queue.result(item.ingestionId,item.attempts,0,"permanent","expired_or_wrong_identity");continue}
            val reply=try {
                val body=protected.decrypt("queue:${item.ingestionId}",item.encryptedBody)
                val request=Protocol.signed(identity,keys,item.path,body,item.ingestionId)
                api.send(request)
            }catch (_:java.io.IOException){null}catch (_:Exception){stop("re_pair_required");return@withLock}
            val attempts=item.attempts+1
            val disposition=RetryPolicy.classify(reply?.status,reply?.error)
            val outcome=when(disposition){Disposition.ACCEPTED->"accepted";Disposition.RETRY->if(attempts<RetryPolicy.MAX_ATTEMPTS)"pending" else "permanent";Disposition.PAUSE_IDENTITY->"paused";Disposition.PERMANENT->"permanent"}
            val next=if(outcome=="pending")System.currentTimeMillis()+RetryPolicy.backoff(attempts) else 0
            queue.result(item.ingestionId,attempts,next,outcome,reply?.error?.take(40)?:if(reply==null)"network_unavailable" else null)
            val loaded=state.load();state.save(loaded.copy(lastSuccess=if(outcome=="accepted")System.currentTimeMillis() else loaded.lastSuccess,
                lastFailure=if(outcome!="accepted")System.currentTimeMillis() else loaded.lastFailure,summary="${item.path.substringAfterLast('/')}: HTTP ${reply?.status?:0} · $outcome"))
            SafeDiagnostics.status(loaded.debugLogging,reply?.status,outcome)
            if(disposition==Disposition.PAUSE_IDENTITY){stop(if(reply?.error=="device_revoked")"revoked" else "identity_unavailable");return@withLock}
        }
    }
    private suspend fun stop(reason:String){val s=state.load();state.save(s.copy(identity=s.identity?.copy(state=reason),summary="Stopped: $reason. New authorized pairing required."));queue.pauseAll()}
    suspend fun markRevoked()=mutex.withLock { stop("revoked") }
    suspend fun settings(consent:Boolean,logging:Boolean)=mutex.withLock { val s=state.load();state.save(s.copy(softwareConsent=consent,debugLogging=logging)) }
}
