package com.ekbotix.ekpayparser.network

import com.ekbotix.ekpayparser.model.*
import com.ekbotix.ekpayparser.protocol.Protocol
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

interface ParserApi { fun pair(body: ByteArray): ApiReply; fun send(request: SignedRequest): ApiReply }
class SandboxApi(private val baseUrl: String, private val enabled: Boolean, private val debug: Boolean,
    private val client: OkHttpClient = secureClient()) : ParserApi {
    companion object {
        fun secureClient() = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
        fun validateUrl(url: String, debug: Boolean) {
            val uri = URI(url)
            require(debug && uri.scheme == "https" && uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2")) { "Only HTTPS disposable loopback sandbox is supported" }
            require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.path in listOf("", "/"))
        }
    }
    private fun post(path: String, body: ByteArray, headers: Map<String, String>): ApiReply {
        check(enabled); validateUrl(baseUrl, debug); require(body.size <= Protocol.MAX_BODY)
        val request = Request.Builder().url(baseUrl.trimEnd('/') + path).post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).apply { headers.forEach { (k,v) -> header(k,v) } }.build()
        // No HTTP logger, token/signature/response dumps, redirect or certificate override.
        client.newCall(request).execute().use { response ->
            val source = response.body?.source() ?: return ApiReply(502,"invalid_response")
            source.request(4097)
            if(source.buffer.size>4096)return ApiReply(502,"invalid_response")
            val raw=source.readUtf8()
            val value = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return ApiReply(502,"invalid_response")
            return runCatching {
                val error = value?.get("error")?.asString?.takeIf { it.matches(Regex("[a-z_]{1,40}")) }
                ApiReply(response.code, error, value?.get("device_id")?.asString, value?.get("key_version")?.asInt, value?.get("reused")?.asBoolean ?: false, value?.get("evidence_id")?.takeUnless {it.isJsonNull}?.asString,
                    value?.get("provider")?.asString?.takeIf {it in setOf("bkash","nagad","rocket","upay")},value?.get("provider_account_display")?.asString?.take(64))
            }.getOrElse { ApiReply(502, "invalid_response") }
        }
    }
    override fun pair(body: ByteArray) = post(Protocol.PAIR, body, emptyMap())
    override fun send(request: SignedRequest) = post(request.path, request.body, request.headers)
}
