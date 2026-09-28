package com.ekbotix.ekpayparser.sms

object ReceiverTelemetry {
    fun entry(action:String?,timestamp:Long)=Unit
    fun lifecycle(event:String,timestamp:Long)=Unit
    fun record(sender:String?,body:String,partCount:Int,timestamp:Long,status:ParseStatus,subscriptionMetadataPresent:Boolean)=Unit
    fun summary()="Receiver telemetry disabled in release"
    fun clear()=Unit
}
