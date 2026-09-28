package com.ekbotix.ekpayparser.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.*
import com.ekbotix.ekpayparser.acquisition.SmsInboxRecoveryReader
import java.util.concurrent.TimeUnit

class RecoveryWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        if(ContextCompat.checkSelfPermission(applicationContext,Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)return Result.success()
        return try { SmsInboxRecoveryReader(applicationContext).recover(fullWindow=inputData.getBoolean("fullWindow",false));Result.success() } catch (_:SecurityException) { Result.success() } catch (_:Exception) { Result.retry() }
    }
    companion object {
        private const val PERIODIC="internal-sms-recovery-periodic"
        fun schedule(context:Context)=WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecoveryWorker>(15,TimeUnit.MINUTES).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build())
        fun now(context:Context,fullWindow:Boolean=false)=WorkManager.getInstance(context).enqueueUniqueWork("internal-sms-recovery-now",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RecoveryWorker>().setInputData(workDataOf("fullWindow" to fullWindow)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
    }
}
