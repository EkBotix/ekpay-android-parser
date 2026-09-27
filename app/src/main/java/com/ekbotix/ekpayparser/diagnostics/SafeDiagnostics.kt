package com.ekbotix.ekpayparser.diagnostics
import android.util.Log
object SafeDiagnostics {
    fun status(enabled:Boolean,status:Int?,state:String) {
        if(enabled)Log.d("EkPaySandbox","http=${status?:0} queue=${state.takeIf { it in setOf("accepted","retry","paused","permanent") }?:"unknown"}")
    }
    // No Throwable/message/body/token/key/signature logging API.
}
