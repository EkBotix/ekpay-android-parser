package com.ekbotix.ekpayparser.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.*
import com.ekbotix.ekpayparser.workers.SmsProcessingWorker

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val groupedMessages = messages.groupBy { it.originatingAddress }
        val pipeline = SmsParserPipeline(listOf(BkashSmsParser(), NagadSmsParser(), RocketSmsParser(), UpaySmsParser()))

        groupedMessages.forEach { (sender, msgs) ->
            if (sender == null) return@forEach
            val body = msgs.joinToString("") { it.messageBody ?: "" }
            val receivedAt = msgs.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            val message = SmsMessageInput(sender, body, receivedAt)
            val result = pipeline.process(message)

            if (result.status == ParseStatus.PARSED && result.transactionId != null && result.provider != null && result.amountMinor != null) {
                val inputData = workDataOf(
                    "provider" to result.provider,
                    "transactionId" to result.transactionId,
                    "amountMinor" to result.amountMinor,
                    "messageHash" to (result.messageHash ?: ""),
                    "receivedAt" to receivedAt,
                    "providerTimestamp" to (result.providerTimestamp ?: "")
                )

                val workRequest = OneTimeWorkRequestBuilder<SmsProcessingWorker>()
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }
}
