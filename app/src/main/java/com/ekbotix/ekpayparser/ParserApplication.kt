package com.ekbotix.ekpayparser
import android.app.Application
import androidx.room.Room
import com.ekbotix.ekpayparser.crypto.AndroidParserKeyStore
import com.ekbotix.ekpayparser.data.QueueDatabase
import com.ekbotix.ekpayparser.network.SandboxApi
import com.ekbotix.ekpayparser.repository.ParserRepository
import com.ekbotix.ekpayparser.storage.*
import com.ekbotix.ekpayparser.workers.SyncWorker
class ParserApplication:Application(){
    val repository:ParserRepository by lazy {
        val protected=ProtectedStorage(this);val state=DeviceState(protected)
        val database=Room.databaseBuilder(this,QueueDatabase::class.java,"synthetic-queue.db").build()
        ParserRepository(state,AndroidParserKeyStore(protected){state.load().softwareConsent},database.queue(),protected,SandboxApi(BuildConfig.TEST_BASE_URL,BuildConfig.SANDBOX_NETWORKING,BuildConfig.DEBUG))
    }
    override fun onCreate(){super.onCreate();if(BuildConfig.DEBUG && BuildConfig.SANDBOX_NETWORKING)SyncWorker.schedule(this)}
}
