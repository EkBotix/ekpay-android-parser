package com.ekbotix.ekpayparser

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekbotix.ekpayparser.protocol.Protocol
import com.ekbotix.ekpayparser.model.Evidence
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import kotlinx.coroutines.delay

@RunWith(AndroidJUnit4::class)
class SyntheticIngestionTest {
    @Test fun generateAndSyncSyntheticEvidence() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ParserApplication>()
        val repo = app.repository

        // 1. Check if paired
        val state = repo.state.load()
        assertNotNull("Not paired!", state.identity)

        // 2. Generate exactly ONE unique synthetic TEST evidence item
        val transactionId = "TEST_" + Protocol.ingestionId().replace("-", "").uppercase().take(12)
        val timestamp = Protocol.iso(System.currentTimeMillis())
        val evidence = Evidence(
            provider = "bkash",
            transactionId = transactionId,
            amountMinor = 15000, // 150.00 BDT
            providerTimestamp = timestamp,
            receiverHash = "a".repeat(64),
            senderHash = null
        )

        // 3. Enqueue FIRST TIME
        val ingestionId1 = repo.enqueue(Protocol.EVIDENCE, Protocol.evidenceBody(evidence))
        repo.sync()

        // 4. Enqueue SECOND TIME (Idempotency check)
        val ingestionId2 = repo.enqueue(Protocol.EVIDENCE, Protocol.evidenceBody(evidence))
        repo.sync()

        // 5. Verify the queue is now empty or marked as success
        val all = repo.queue.all()
        val item1 = all.find { it.ingestionId == ingestionId1 }
        val item2 = all.find { it.ingestionId == ingestionId2 }

        assertNotNull("Evidence 1 not found in queue", item1)
        assertNotNull("Evidence 2 not found in queue", item2)
        assertEquals("Ingestion 1 failed! Error: ${item1?.safeError}", "accepted", item1?.status)
        assertEquals("Ingestion 2 failed! Error: ${item2?.safeError}", "accepted", item2?.status)

        println("SYNTHETIC_TRANSACTION_ID: $transactionId")
    }
}
