
package com.ekbotix.ekpayparser.network
import org.junit.Test
import org.junit.Assume.assumeTrue
import com.ekbotix.ekpayparser.protocol.Protocol
import okhttp3.OkHttpClient

class SandboxApiTest {
    @Test
    fun liveSandboxApiIsOptIn() {
        val baseUrl = System.getenv("EKPAY_SANDBOX_API_TEST_URL")
        assumeTrue("Live sandbox API test is opt-in", !baseUrl.isNullOrBlank())
        val client = SandboxApi.secureClient(true)
        val api = SandboxApi(baseUrl!!, true, true, client)
        api.pair(ByteArray(0))
    }
}
