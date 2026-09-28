package com.ekbotix.ekpayparser
import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import com.ekbotix.ekpayparser.crypto.AndroidParserKeyStore
import com.ekbotix.ekpayparser.data.QueueDatabase
import com.ekbotix.ekpayparser.network.SandboxApi
import com.ekbotix.ekpayparser.repository.ParserRepository
import com.ekbotix.ekpayparser.storage.*
import com.ekbotix.ekpayparser.workers.SyncWorker
import com.ekbotix.ekpayparser.workers.RecoveryWorker
import com.ekbotix.ekpayparser.acquisition.*
class ParserApplication:Application(){
    private val vault:ProtectedStorage by lazy { ProtectedStorage(this) }
    val providerRegistry:InternalProviderRegistry by lazy { InternalProviderRegistry(vault) }
    val repository:ParserRepository by lazy {
        val protected=vault;val state=DeviceState(protected)
        val database=Room.databaseBuilder(this,QueueDatabase::class.java,"synthetic-queue.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
        ParserRepository(state,AndroidParserKeyStore(protected){state.load().softwareConsent},database.queue(),protected,SandboxApi(BuildConfig.TEST_BASE_URL,BuildConfig.SANDBOX_NETWORKING,BuildConfig.DEBUG))
    }
    val evidenceProcessor:PaymentEvidenceProcessor by lazy { PaymentEvidenceProcessor(repository,providerRegistry) }
    override fun onCreate(){super.onCreate();RecoveryWorker.schedule(this);if(BuildConfig.DEBUG && BuildConfig.SANDBOX_NETWORKING)SyncWorker.schedule(this)}

    companion object {
        val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `sms_dedupe` (`messageHash` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, PRIMARY KEY(`messageHash`))")
        }
        val MIGRATION_2_3 = Migration(2, 3) { db ->
            db.execSQL("CREATE TABLE `sms_dedupe_new` (`provider` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `messageHash` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, PRIMARY KEY(`provider`, `transactionId`))")
            // v2 had no provider. A hash-derived legacy namespace preserves every old row;
            // duplicate lookup still recognizes its messageHash. The evidence queue is untouched.
            db.execSQL("INSERT INTO `sms_dedupe_new` (`provider`,`transactionId`,`messageHash`,`receivedAt`) SELECT 'legacy:' || `messageHash`, `transactionId`, `messageHash`, `receivedAt` FROM `sms_dedupe`")
            db.execSQL("DROP TABLE `sms_dedupe`")
            db.execSQL("ALTER TABLE `sms_dedupe_new` RENAME TO `sms_dedupe`")
        }
        val MIGRATION_3_4 = Migration(3, 4) { db ->
            db.execSQL("ALTER TABLE `sms_dedupe` ADD COLUMN `amountMinor` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `sms_dedupe` ADD COLUMN `acquisitionSource` TEXT NOT NULL DEFAULT 'SYNTHETIC_TEST'")
            db.execSQL("ALTER TABLE `sms_dedupe` ADD COLUMN `parserVersion` TEXT NOT NULL DEFAULT 'legacy'")
            db.execSQL("ALTER TABLE `sms_dedupe` ADD COLUMN `ingestionId` TEXT NOT NULL DEFAULT ''")
        }
    }
}
