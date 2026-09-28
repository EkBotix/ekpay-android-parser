package com.ekbotix.ekpayparser.diagnostics
import android.util.Log
object SafeDiagnostics {
    fun status(enabled:Boolean,status:Int?,state:String) {
        if(enabled)Log.d("EkPaySandbox","http=${status?:0} queue=${state.takeIf { it in setOf("accepted","retry","paused","permanent") }?:"unknown"}")
    }
    fun pairing(enabled:Boolean,stage:String,category:String) {
        if(!enabled)return
        require(stage in setOf("VALIDATION","KEY","STATE_SAVE","SIGNING","NETWORK","HTTP","RESPONSE","FINAL_STATE"))
        require(category in setOf("ok","invalid_input","state_conflict","key_unavailable","security","io","tls","http_2xx","http_4xx","http_5xx","http_other","response_mismatch","unexpected"))
        Log.d("EkPaySandbox","pair_stage=$stage category=$category")
    }
    // No Throwable/message/body/token/key/signature logging API.
}
