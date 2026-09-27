package com.ekbotix.ekpayparser.workers
import android.content.Context
import androidx.work.*
import com.ekbotix.ekpayparser.ParserApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class SyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result = withContext(Dispatchers.IO) { try {(applicationContext as ParserApplication).repository.sync();Result.success()}catch (_:Exception){Result.failure()} }
    companion object {
        fun schedule(context:Context){
            val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sandbox-sync-periodic",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<SyncWorker>(15,TimeUnit.MINUTES).setConstraints(constraints).build())
        }
        fun now(context:Context){WorkManager.getInstance(context).enqueueUniqueWork("sandbox-sync-now",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())}
    }
}
