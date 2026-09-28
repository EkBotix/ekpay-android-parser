package com.ekbotix.ekpayparser.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.ListenableWorker
import com.ekbotix.ekpayparser.ParserApplication
import com.ekbotix.ekpayparser.model.Evidence
import com.ekbotix.ekpayparser.protocol.Protocol
import com.ekbotix.ekpayparser.data.DedupeItem

class SmsProcessingWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = SmsWorkProcessor.run(inputData, (applicationContext as ParserApplication).repository)
}

internal object SmsWorkProcessor {
    suspend fun run(inputData: Data, repository: com.ekbotix.ekpayparser.repository.ParserRepository): ListenableWorker.Result {
        val provider = inputData.getString("provider") ?: return ListenableWorker.Result.failure()
        val txnId = inputData.getString("transactionId") ?: return ListenableWorker.Result.failure()
        val amountMinor = inputData.getLong("amountMinor", -1L)
        if (amountMinor == -1L) return ListenableWorker.Result.failure()

        val msgHash = inputData.getString("messageHash") ?: ""
        val receivedAt = inputData.getLong("receivedAt", System.currentTimeMillis())
        val providerTimestamp = inputData.getString("providerTimestamp")?.takeIf { it.isNotBlank() }
            ?: return ListenableWorker.Result.failure()

        val evidence = Evidence(
            provider = provider,
            transactionId = txnId,
            amountMinor = amountMinor,
            providerTimestamp = providerTimestamp,
            receiverHash = null,
            senderHash = null
        )

        return try {
            repository.enqueueSms(Protocol.EVIDENCE, Protocol.evidenceBody(evidence), DedupeItem(provider, txnId, msgHash, receivedAt))
            ListenableWorker.Result.success()
        } catch (_: Exception) {
            ListenableWorker.Result.retry()
        }
    }
}
