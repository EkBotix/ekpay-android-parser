package com.ekbotix.ekpayparser

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekbotix.ekpayparser.data.QueueDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueMigrationTest {
    @Test fun migration2To3PreservesQueueAndLegacyDedupe()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="phase8-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).apply {
            execSQL("CREATE TABLE queue (ingestionId TEXT NOT NULL,deviceId TEXT NOT NULL,path TEXT NOT NULL,encryptedBody TEXT NOT NULL,createdAt INTEGER NOT NULL,attempts INTEGER NOT NULL,nextAttempt INTEGER NOT NULL,status TEXT NOT NULL,safeError TEXT,PRIMARY KEY(ingestionId))")
            execSQL("CREATE TABLE sms_dedupe (messageHash TEXT NOT NULL,transactionId TEXT NOT NULL,receivedAt INTEGER NOT NULL,PRIMARY KEY(messageHash))")
            execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            execSQL("INSERT INTO room_master_table (id,identity_hash) VALUES(42,'51e2c0680143e8a563d5fd2ffc69740c')")
            execSQL("INSERT INTO queue VALUES ('ingestion','device','path','encrypted',1,0,0,'pending',NULL)")
            execSQL("INSERT INTO sms_dedupe VALUES ('${"a".repeat(64)}','TEST_OLD',2)")
            version=2
            close()
        }
        val migrated=Room.databaseBuilder(context,QueueDatabase::class.java,name)
            .addMigrations(ParserApplication.MIGRATION_2_3).build()
        try {
            assertEquals(1,migrated.queue().totalCount())
            assertEquals("encrypted",migrated.queue().all().single().encryptedBody)
            assertEquals(1,migrated.queue().dedupeCount())
            assertTrue(migrated.queue().hasDedupe("legacy:${"a".repeat(64)}","TEST_OLD","a".repeat(64)))
        } finally {
            migrated.close();context.deleteDatabase(name)
        }
    }
}
