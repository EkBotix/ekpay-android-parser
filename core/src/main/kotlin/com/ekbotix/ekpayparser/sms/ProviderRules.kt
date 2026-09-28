package com.ekbotix.ekpayparser.sms

data class ProviderRuleSet(
    val provider:String,
    val testSenders:Set<String>,
    val incomingKeywords:Set<String>,
    val amountLabels:Set<String>,
    val transactionIdLabels:Set<String>,
    val timestampPatterns:List<Regex> = emptyList(),
    val accountReferenceLabels:Set<String> = emptySet(),
    val rejectionPatterns:Set<String>,
    val parserVersion:String
)

object ProviderRules {
    val bkash=ProviderRuleSet("bkash",setOf("TEST_BKASH"),setOf("received"),setOf("tk","bdt","৳"),setOf("txnid"),rejectionPatterns=rejectionPatterns(),parserVersion="bkash-test-v2")
    val nagad=ProviderRuleSet("nagad",setOf("TEST_NAGAD"),setOf("received"),setOf("amount","tk","bdt","৳"),setOf("txnid"),rejectionPatterns=rejectionPatterns(),parserVersion="nagad-test-v2")
    val rocket=ProviderRuleSet("rocket",setOf("TEST_ROCKET"),setOf("received"),setOf("tk","bdt","৳"),setOf("txnid"),rejectionPatterns=rejectionPatterns(),parserVersion="rocket-test-v2")
    val upay=ProviderRuleSet("upay",setOf("TEST_UPAY"),setOf("received"),setOf("tk","bdt","৳"),setOf("trxid"),rejectionPatterns=rejectionPatterns(),parserVersion="upay-test-v2")
    val all=listOf(bkash,nagad,rocket,upay)
    private fun rejectionPatterns()=setOf("fee","balance","cashback","refund","reversed","failed","cancelled")
}

data class SenderRegistryEntry(val provider:String,val normalizedSender:String,val state:SenderRegistryState,val evidence:String)
object SenderRegistry {
    val testEntries=ProviderRules.all.flatMap { rule -> rule.testSenders.map { SenderRegistryEntry(rule.provider,it,SenderRegistryState.APPROVED,"synthetic test sender") } }
    val realEntries:List<SenderRegistryEntry> = emptyList() // Human-reviewed observations only; never inferred or auto-approved.
    fun normalize(sender:String):String {
        val trimmed=sender.trim()
        if(trimmed.matches(Regex("[+0-9 ()-]+"))) {
            val compact=trimmed.replace(Regex("[ ()-]"),"")
            return if(compact.startsWith("880")) "+$compact" else compact
        }
        return trimmed.uppercase()
    }
    fun state(sender:String)= (testEntries+realEntries).firstOrNull { it.normalizedSender==normalize(sender) }?.state ?: SenderRegistryState.UNVERIFIED
}
