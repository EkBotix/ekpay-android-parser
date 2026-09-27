package com.ekbotix.ekpayparser.data

import androidx.room.*

@Entity(tableName="queue")
data class QueueItem(@PrimaryKey val ingestionId: String, val deviceId: String, val path: String, val encryptedBody: String,
    val createdAt: Long, val attempts: Int=0, val nextAttempt: Long=0, val status: String="pending", val safeError: String?=null)
@Dao interface QueueDao {
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun add(item: QueueItem)
    @Query("SELECT * FROM queue WHERE status='pending' AND nextAttempt<=:now ORDER BY createdAt LIMIT 20") suspend fun due(now: Long): List<QueueItem>
    @Query("SELECT * FROM queue ORDER BY createdAt DESC LIMIT 100") suspend fun all(): List<QueueItem>
    @Query("SELECT count(*) FROM queue WHERE status='pending'") suspend fun pendingCount(): Int
    @Query("SELECT count(*) FROM queue") suspend fun totalCount(): Int
    @Query("UPDATE queue SET attempts=:attempts,nextAttempt=:next,status=:status,safeError=:error WHERE ingestionId=:id") suspend fun result(id:String,attempts:Int,next:Long,status:String,error:String?)
    @Query("UPDATE queue SET status='paused',safeError='identity_unavailable' WHERE status='pending'") suspend fun pauseAll()
    @Query("DELETE FROM queue WHERE status!='pending' AND createdAt<:before") suspend fun prune(before:Long)
}
@Database(entities=[QueueItem::class],version=1,exportSchema=true)
abstract class QueueDatabase:RoomDatabase(){ abstract fun queue():QueueDao }
