package com.ekbotix.ekpayparser.acquisition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.ekbotix.ekpayparser.ParserApplication
import com.ekbotix.ekpayparser.sms.*
import kotlin.math.max

data class LearningCandidate(val sender:String,val senderDisplay:String,val receivedAt:Long,val redactedPreview:String)
data class RecoveryResult(val inspected:Int,val queued:Int,val completed:Boolean)

class SmsInboxRecoveryReader(private val context:Context) {
    companion object { const val MAX_RECOVERY_WINDOW_MS=30*60*1000L;const val OVERLAP_MS=3*60*1000L;const val MAX_ROWS=100 }
    private val app=context.applicationContext as ParserApplication
    suspend fun recover(now:Long=System.currentTimeMillis(),fullWindow:Boolean=false):RecoveryResult {
        check(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED)
        val state=app.repository.state.load()
        val start=if(fullWindow)now-MAX_RECOVERY_WINDOW_MS else max((state.lastSmsRecoveryTimestamp?:now)-OVERLAP_MS,now-MAX_RECOVERY_WINDOW_MS)
        var inspected=0;var queued=0;var cursor=start
        query(start).forEach { row ->
            inspected++
            val outcome=app.evidenceProcessor.process(AcquiredPaymentMessage(AcquisitionSource.SMS_INBOX_RECOVERY,row.sender,row.body,row.receivedAt))
            if(outcome.queued)queued++
            cursor=max(cursor,row.receivedAt)
            app.repository.state.update { it.copy(lastSmsRecoveryTimestamp=cursor) }
        }
        app.repository.state.update { it.copy(lastSuccessfulRecoveryAt=now) }
        return RecoveryResult(inspected,queued,true)
    }
    fun learningCandidates(now:Long=System.currentTimeMillis()):List<LearningCandidate> {
        check(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED)
        return query(now-MAX_RECOVERY_WINDOW_MS).asSequence()
            .filter { SmsSafety.direction(it.body)!in setOf(TransactionDirection.AUTHENTICATION,TransactionDirection.PROMOTIONAL) }
            .filter { FormatAnalyzer.amountCandidates(it.body).isNotEmpty() || ProviderRules.all.any { rule -> FormatAnalyzer.transactionCandidates(it.body,rule).isNotEmpty() } }
            .map { LearningCandidate(it.sender,redactSender(it.sender),it.receivedAt,redact(it.body)) }.take(25).toList()
    }
    private data class InboxRow(val sender:String,val body:String,val receivedAt:Long)
    private fun query(start:Long):List<InboxRow> {
        val args=Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,"${Telephony.Sms.DATE} >= ?")
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,arrayOf(start.toString()))
            putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,arrayOf(Telephony.Sms.DATE))
            putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT,MAX_ROWS)
        }
        val rows=mutableListOf<InboxRow>()
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,arrayOf(Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE),args,null)?.use { c ->
            val address=c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);val body=c.getColumnIndexOrThrow(Telephony.Sms.BODY);val date=c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while(c.moveToNext() && rows.size<MAX_ROWS) {
                val sender=c.getString(address)?:continue;val text=c.getString(body)?:continue
                rows+=InboxRow(sender,text,c.getLong(date))
            }
        }
        return rows
    }
    private fun redact(body:String):String = SmsText.normalized(body)
        .replace(Regex("(?<![A-Za-z])[+0-9][0-9 ()-]{7,}[0-9]"),"[PHONE]")
        .replace(Regex("(?i)(balance|bal)\\s*[:=]?\\s*(?:tk|bdt)?\\s*[0-9,.]+"),"$1 [REDACTED]")
        .replace(Regex("(?i)(txn(?:id)?|trxid|reference)\\s*:?\\s*([A-Z0-9._-]{4,})")) { "${it.groupValues[1]} ${it.groupValues[2].take(3)}***${it.groupValues[2].takeLast(2)}" }
        .take(240)
    private fun redactSender(sender:String):String {
        val value=SenderRegistry.normalize(sender)
        return if(value.matches(Regex("\\+?[0-9]{8,}")))value.take(3)+"***"+value.takeLast(2) else value.take(3)+"***"
    }
}
