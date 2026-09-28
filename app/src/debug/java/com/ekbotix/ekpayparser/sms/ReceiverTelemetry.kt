package com.ekbotix.ekpayparser.sms

import android.util.Log
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

object ReceiverTelemetry {
    private val latest=AtomicReference<ReceiverObservation?>()
    fun entry(action:String?,timestamp:Long) {
        Log.i("EkPaySmsReceiver","event=SMS_RECEIVED_ENTRY action=${action ?: "unknown"} timestamp=$timestamp")
    }
    fun lifecycle(event:String,timestamp:Long) {
        Log.i("EkPaySmsReceiver","event=$event timestamp=$timestamp")
    }
    fun record(sender:String?,body:String,partCount:Int,timestamp:Long,status:ParseStatus,subscriptionMetadataPresent:Boolean) {
        fun prefix(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).take(6).joinToString(""){"%02x".format(it)}
        val category=when {
            sender==null -> "unknown"
            SenderRegistry.normalize(sender).matches(Regex("\\+?[0-9]+")) -> "numeric"
            sender.any(Char::isLetter) -> "alphanumeric"
            else -> "unknown"
        }
        val observation=ReceiverObservation(true,category,prefix(sender.orEmpty()),prefix(body),body.length,partCount,timestamp,status.name,subscriptionMetadataPresent)
        latest.set(observation)
        Log.i("EkPaySmsReceiver",summary(observation))
    }
    fun summary():String=latest.get()?.let(::summary) ?: "No genuine receiver observation in this process"
    fun clear(){latest.set(null)}
    private fun summary(value:ReceiverObservation)="receiver_triggered=${value.receiverTriggered} sender_category=${value.senderCategory} sender_hash_prefix=${value.senderHashPrefix} body_hash_prefix=${value.bodyHashPrefix} message_length=${value.messageLength} parts=${value.partCount} timestamp=${value.timestamp} parser_status=${value.parserStatus} subscription_metadata=${value.subscriptionMetadataPresent}"
}


