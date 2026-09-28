package com.ekbotix.ekpayparser.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ekbotix.ekpayparser.ParserApplication
import kotlinx.coroutines.*

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

        val parts=messages.map { SmsPart(it.originatingAddress,it.messageBody,it.timestampMillis) }
        val message=MultipartAssembler.assemble(parts)
        if(message==null) {
            ReceiverTelemetry.record(null,"",messages.size,System.currentTimeMillis(),ParseStatus.INVALID,hasSubscriptionMetadata)
            return
        }
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val acquired=AcquiredPaymentMessage(AcquisitionSource.SMS_RECEIVER,message.sender,message.body,message.receivedAt)
                val result=(context.applicationContext as ParserApplication).evidenceProcessor.process(acquired)
                ReceiverTelemetry.record(message.sender,message.body,messages.size,message.receivedAt,result.status,hasSubscriptionMetadata)
            } finally { pending.finish() }
        }
    }
}
