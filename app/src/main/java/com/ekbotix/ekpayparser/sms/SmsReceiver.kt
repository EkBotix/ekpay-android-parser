package com.ekbotix.ekpayparser.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.*
import com.ekbotix.ekpayparser.workers.SmsProcessingWorker

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReceiverTelemetry.entry(intent.action,System.currentTimeMillis())
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val hasSubscriptionMetadata=intent.hasExtra("subscription")||intent.hasExtra("subscription_id")||intent.hasExtra("slot")||intent.hasExtra("slot_id")
        if (messages.isNullOrEmpty()) {
            ReceiverTelemetry.record(null,"",0,System.currentTimeMillis(),ParseStatus.INVALID,hasSubscriptionMetadata)
            return
        }

        val pipeline = SmsParserPipeline(listOf(BkashSmsParser(), NagadSmsParser(), RocketSmsParser(), UpaySmsParser()))
        val parts=messages.map { SmsPart(it.originatingAddress,it.messageBody,it.timestampMillis) }
        val message=MultipartAssembler.assemble(parts)
        if(message==null) {
            ReceiverTelemetry.record(null,"",messages.size,System.currentTimeMillis(),ParseStatus.INVALID,hasSubscriptionMetadata)
            return
        }
        val result=pipeline.process(message)
        ReceiverTelemetry.record(message.sender,message.body,messages.size,message.receivedAt,result.status,hasSubscriptionMetadata)

        if (result.status == ParseStatus.PARSED && result.transactionId != null && result.provider != null && result.amountMinor != null && result.providerTimestamp != null) {
                val inputData = workDataOf(
                    "provider" to result.provider,
                    "transactionId" to result.transactionId,
                    "amountMinor" to result.amountMinor,
                    "messageHash" to (result.messageHash ?: ""),
                    "receivedAt" to message.receivedAt,
                    "providerTimestamp" to result.providerTimestamp
                )

                val workRequest = OneTimeWorkRequestBuilder<SmsProcessingWorker>()
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
