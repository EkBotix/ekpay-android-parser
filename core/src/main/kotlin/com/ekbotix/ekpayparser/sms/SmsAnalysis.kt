package com.ekbotix.ekpayparser.sms

import java.math.BigDecimal

object SmsText {
    fun normalized(value:String)=buildString(value.length) {
        value.forEach { c -> append(when(c) {
            in '০'..'৯' -> ('0'.code+(c.code-'০'.code)).toChar()
            '\u00a0','\u202f' -> ' '
            '：' -> ':'
            '–','—','−' -> '-'
            else -> c
        }) }
    }.replace(Regex("[ \\t]+")," ").trim()
}

object SmsSafety {
    private val authentication=setOf("otp","one-time password","one time password","verification code","authentication code","pin","code expires","do not share","login code","ওটিপি","যাচাইকরণ কোড","পিন","শেয়ার করবেন না","শেয়ার করবেন না")
    private val promotion=setOf("cashback","discount","offer","voucher","reward","campaign","promotional","ছাড়","অফার","ভাউচার","পুরস্কার","ক্যাশব্যাক")
    private val reversal=setOf("refund","refunded","reversal","reversed","failed","cancelled","canceled","ফেরত","ব্যর্থ","বাতিল")
    private val outgoing=setOf("sent money","money sent","cash out","cash-out","payment made","টাকা পাঠানো","ক্যাশ আউট")
    private val balance=setOf("current balance","available balance","balance updated","বর্তমান ব্যালেন্স","ব্যালেন্স আপডেট")
    private val incoming=setOf("received","money received","cash in","payment received","পেয়েছেন","পেয়েছেন","টাকা গ্রহণ")
    fun direction(body:String):TransactionDirection {
        val text=SmsText.normalized(body).lowercase()
        return when {
            authentication.any(text::contains) -> TransactionDirection.AUTHENTICATION
            promotion.any(text::contains) -> TransactionDirection.PROMOTIONAL
            reversal.any(text::contains) -> TransactionDirection.REFUND_OR_REVERSAL
            outgoing.any(text::contains) -> TransactionDirection.OUTGOING
            balance.any(text::contains) && incoming.none(text::contains) -> TransactionDirection.BALANCE_ONLY
            incoming.any(text::contains) -> TransactionDirection.INCOMING
            else -> TransactionDirection.UNKNOWN
        }
    }
}

object FormatAnalyzer {
    private val amountRegex=Regex("(?i)(?:tk|bdt|৳)\\s*[:=]?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)")
    private val excludedContext=Regex("(?i)(fee|charge|balance|cashback|ফি|চার্জ|ব্যালেন্স|ক্যাশব্যাক)[^.;]{0,16}$")
    fun amountCandidates(body:String):List<String> {
        val text=SmsText.normalized(body)
        return amountRegex.findAll(text).filter { match ->
            val prefix=text.substring(maxOf(0,match.range.first-32),match.range.first)
            !excludedContext.containsMatchIn(prefix)
        }.map { it.groupValues[1].replace(",","") }.filter { parseMinor(it)!=null }.distinct().toList()
    }
    fun transactionCandidates(body:String,rule:ProviderRuleSet):List<String> {
        val text=SmsText.normalized(body)
        return rule.transactionIdLabels.flatMap { label ->
            Regex("(?i)${Regex.escape(label)}\\s*:?\\s*([A-Za-z0-9_-]{4,64})").findAll(text).map { it.groupValues[1] }.toList()
        }.distinct()
    }
    fun analyze(provider:String,body:String):FormatAnalysis {
        val rule=ProviderRules.all.firstOrNull { it.provider==provider }
            ?: return FormatAnalysis(emptyList(),emptyList(),null,emptyList(),ParseStatus.INVALID,listOf("Unknown provider"))
        val amounts=amountCandidates(body);val ids=transactionCandidates(body,rule);val direction=SmsSafety.direction(body)
        val warnings=buildList {
            if(amounts.size>1)add("Multiple plausible payment amounts")
            if(ids.size>1)add("Multiple transaction IDs")
            if(direction!=TransactionDirection.INCOMING)add("Incoming-payment direction not established")
            if(rule.timestampPatterns.isEmpty())add("Provider timestamp format is not approved")
        }
        val status=when {
            direction in setOf(TransactionDirection.AUTHENTICATION,TransactionDirection.PROMOTIONAL,TransactionDirection.BALANCE_ONLY) -> ParseStatus.IGNORED
            direction in setOf(TransactionDirection.OUTGOING,TransactionDirection.REFUND_OR_REVERSAL) -> ParseStatus.UNSUPPORTED_TYPE
            amounts.size>1 || ids.size>1 -> ParseStatus.AMBIGUOUS
            amounts.size==1 && ids.size==1 && direction==TransactionDirection.INCOMING -> ParseStatus.MANUAL_REVIEW
            else -> ParseStatus.PARTIAL
        }
        return FormatAnalysis(amounts,ids,null,rule.incomingKeywords.filter { SmsText.normalized(body).contains(it,true) },status,warnings)
    }
    fun parseMinor(value:String):Long?=try {
        val decimal=BigDecimal(value.replace(",",""));if(decimal.scale()>2||decimal<=BigDecimal.ZERO)null else decimal.movePointRight(2).longValueExact()
    } catch (_:Exception) { null }
}

object MultipartAssembler {
    const val MAX_PARTS=10
    const val MAX_BODY_LENGTH=4096
    fun assemble(parts:List<SmsPart>):SmsMessageInput? {
        if(parts.isEmpty()||parts.size>MAX_PARTS)return null
        val sender=parts.first().sender?.takeIf { it.isNotBlank() } ?: return null
        if(parts.any { it.sender==null || SenderRegistry.normalize(it.sender)!=SenderRegistry.normalize(sender) })return null
        val times=parts.map { it.timestamp };if((times.maxOrNull()!!-times.minOrNull()!!)>120_000)return null
        val bodies=parts.map { it.body ?: return null };if(bodies.sumOf { it.length }>MAX_BODY_LENGTH)return null
        return SmsMessageInput(sender,bodies.joinToString(""),times.minOrNull()!!)
    }
}
