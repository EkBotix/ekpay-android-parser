package com.ekbotix.ekpayparser.repository

enum class Disposition { ACCEPTED, RETRY, PAUSE_IDENTITY, PERMANENT }
object RetryPolicy {
    const val MAX_ATTEMPTS = 8
    fun classify(status: Int?, error: String?) = when {
        status == null || status >= 500 || (status == 409 && error == "retry_request") -> Disposition.RETRY
        status in 200..299 -> Disposition.ACCEPTED
        status == 401 || error == "device_revoked" -> Disposition.PAUSE_IDENTITY
        else -> Disposition.PERMANENT
    }
    fun backoff(attempt: Int): Long { require(attempt in 1..MAX_ATTEMPTS); return (30_000L * (1L shl (attempt - 1))).coerceAtMost(3_600_000L) }
}
