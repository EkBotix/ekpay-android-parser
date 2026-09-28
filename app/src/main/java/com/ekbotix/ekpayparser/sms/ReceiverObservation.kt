package com.ekbotix.ekpayparser.sms

data class ReceiverObservation(
    val receiverTriggered:Boolean,
    val senderCategory:String,
    val senderHashPrefix:String,
    val bodyHashPrefix:String,
    val messageLength:Int,
    val partCount:Int,
    val timestamp:Long,
    val parserStatus:String,
    val subscriptionMetadataPresent:Boolean
)
