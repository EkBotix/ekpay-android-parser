package com.ekbotix.ekpayparser.acquisition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ekbotix.ekpayparser.workers.RecoveryWorker

class BootReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action==Intent.ACTION_BOOT_COMPLETED)RecoveryWorker.now(context)
    }
}
