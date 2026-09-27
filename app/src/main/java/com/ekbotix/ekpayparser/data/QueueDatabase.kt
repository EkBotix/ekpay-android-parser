package com.ekbotix.ekpayparser.data

import androidx.room.*

@Entity(tableName="queue")
data class QueueItem(@PrimaryKey val ingestionId: String, val deviceId: String, val path: String, val encryptedBody: String,
    val createdAt: Long, val attempts: Int=0, val nextAttempt: Long=0, val status: String="pending", val safeError: String?=null)

@Entity(tableName="sms_dedupe", primaryKeys = ["provider", "transactionId"])
data class DedupeItem(val provider: String, val transactionId: String, val messageHash: String, val receivedAt: Long)

@Dao interface QueueDao {
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun add(item: QueueItem)
    @Query("SELECT * FROM queue WHERE status='pending' AND nextAttempt<=:now ORDER BY createdAt LIMIT 20") suspend fun due(now: Long): List<QueueItem>
    @Query("SELECT * FROM queue ORDER BY createdAt DESC LIMIT 100") suspend fun all(): List<QueueItem>
    @Query("SELECT count(*) FROM queue WHERE status='pending'") suspend fun pendingCount(): Int
    @Query("SELECT count(*) FROM queue") suspend fun totalCount(): Int
    @Query("UPDATE queue SET attempts=:attempts,nextAttempt=:next,status=:status,safeError=:error WHERE ingestionId=:id") suspend fun result(id:String,attempts:Int,next:Long,status:String,error:String?)
    @Query("UPDATE queue SET status='paused',safeError='identity_unavailable' WHERE status='pending'") suspend fun pauseAll()
    @Query("DELETE FROM queue WHERE status!='pending' AND createdAt<:before") suspend fun prune(before:Long)

    // Dedupe
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertDedupe(item: DedupeItem)
    @Query("SELECT EXISTS(SELECT 1 FROM sms_dedupe WHERE (provider=:provider AND transactionId=:txn) OR messageHash=:hash)") suspend fun isDuplicate(provider: String, txn: String, hash: String): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM sms_dedupe WHERE provider=:provider AND transactionId=:txn AND messageHash=:hash)") suspend fun hasDedupe(provider: String, txn: String, hash: String): Boolean
    @Query("SELECT count(*) FROM sms_dedupe") suspend fun dedupeCount(): Int
    @Query("DELETE FROM sms_dedupe WHERE receivedAt<:before") suspend fun pruneDedupe(before:Long)

    @Transaction
    suspend fun enqueueAndDedupeIfNew(item: QueueItem, dedupe: DedupeItem): Boolean {
        if (isDuplicate(dedupe.provider, dedupe.transactionId, dedupe.messageHash)) return false
        check(totalCount() < 100) { "Queue capacity reached" }
        add(item)
        insertDedupe(dedupe)
        return true
    }
}
@Database(entities=[QueueItem::class, DedupeItem::class],version=3,exportSchema=true)
abstract class QueueDatabase:RoomDatabase(){ abstract fun queue():QueueDao }
