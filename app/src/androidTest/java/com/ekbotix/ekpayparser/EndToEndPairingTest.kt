package com.ekbotix.ekpayparser

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class EndToEndPairingTest {
    @Test fun performPairingAndEvidence() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ParserApplication>()
        val repository = app.repository

        val args = InstrumentationRegistry.getArguments()
        val deviceId = args.getString("deviceId")
        val token = args.getString("token")
        assumeTrue("Fresh instrumentation deviceId and token arguments are required", !deviceId.isNullOrBlank() && !token.isNullOrBlank())

        repository.state.save(repository.state.load().copy(softwareConsent = true))

        val success = repository.pair(deviceId!!, 0, token!!)
        val summary = repository.state.load().summary
        assertTrue("Pairing failed! Summary: $summary", success)
    }
}
