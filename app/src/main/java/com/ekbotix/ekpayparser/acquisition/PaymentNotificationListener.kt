package com.ekbotix.ekpayparser.acquisition

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ekbotix.ekpayparser.ParserApplication
import com.ekbotix.ekpayparser.sms.AcquiredPaymentMessage
import com.ekbotix.ekpayparser.sms.AcquisitionSource
import kotlinx.coroutines.*

class PaymentNotificationListener:NotificationListenerService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onNotificationPosted(sbn:StatusBarNotification) {
        val app=application as ParserApplication
        val packageName=sbn.packageName.take(200); android.util.Log.i("EkPayNotification", "listener_saw_package=$packageName")
        app.providerRegistry.observePackage(packageName,System.currentTimeMillis())
        if(app.providerRegistry.packageProvider(packageName)==null)return
        val extras=sbn.notification.extras
        val title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(200)
        val text=extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(2000) ?: return
        val parserText=listOfNotNull(title,text).joinToString("\n").take(2200)
        scope.launch {
            runCatching {
                val outcome = app.evidenceProcessor.process(AcquiredPaymentMessage(AcquisitionSource.NOTIFICATION_LISTENER,packageName,parserText,sbn.postTime,title))
                android.util.Log.i("EkPayNotification", "receiver_triggered=true package_hash_prefix=${com.ekbotix.ekpayparser.protocol.Protocol.sha(packageName.toByteArray()).take(12)} body_hash_prefix=${com.ekbotix.ekpayparser.protocol.Protocol.sha(parserText.toByteArray()).take(12)} message_length=${parserText.length} timestamp=${sbn.postTime} parser_status=${outcome.status.name} provider=${outcome.provider}")
            }
        }
    }
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
