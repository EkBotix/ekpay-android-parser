package com.ekbotix.ekpayparser.acquisition

import com.ekbotix.ekpayparser.sms.RegistryState
import com.ekbotix.ekpayparser.sms.SenderRegistry
import com.ekbotix.ekpayparser.storage.SecureVault
import com.google.gson.Gson

data class InternalRegistryEntry(
    val identity:String,
    val provider:String,
    val state:RegistryState,
    val kind:String,
    val observedAt:Long
)

private data class RegistryDocument(val entries:List<InternalRegistryEntry> = emptyList())

class InternalProviderRegistry(private val vault:SecureVault) {
    private val gson=Gson()
    private val label="provider-registry.v1"
    @Synchronized private fun read():RegistryDocument = vault.read(label)?.let {
        runCatching { gson.fromJson(it.toString(Charsets.UTF_8),RegistryDocument::class.java) }.getOrNull()
    } ?: RegistryDocument()
    @Synchronized private fun write(value:RegistryDocument)=vault.write(label,gson.toJson(value).toByteArray())
    fun entries():List<InternalRegistryEntry> = read().entries.sortedByDescending { it.observedAt }
    fun senderProvider(sender:String):String? {
        val normalized=SenderRegistry.normalize(sender)
        SenderRegistry.testEntries.firstOrNull { it.normalizedSender==normalized }?.let { return it.provider }
        return read().entries.firstOrNull { it.kind=="sender" && it.identity==normalized && it.state==RegistryState.INTERNAL_APPROVED }?.provider
    }
    fun packageProvider(packageName:String):String? = read().entries.firstOrNull {
        it.kind=="package" && it.identity==packageName && it.state==RegistryState.INTERNAL_APPROVED
    }?.provider
    fun observePackage(packageName:String,now:Long) = put(packageName,"",RegistryState.OBSERVED,"package",now,false)
    fun observeSender(sender:String,provider:String,now:Long) = put(SenderRegistry.normalize(sender),provider,RegistryState.OBSERVED,"sender",now,true)
    fun approveSender(sender:String,provider:String,now:Long) = put(SenderRegistry.normalize(sender),provider,RegistryState.INTERNAL_APPROVED,"sender",now,true)
    fun approvePackage(packageName:String,provider:String,now:Long) = put(packageName,provider,RegistryState.INTERNAL_APPROVED,"package",now,true)
    fun disable(identity:String,kind:String,now:Long) = put(if(kind=="sender")SenderRegistry.normalize(identity) else identity,"",RegistryState.DISABLED,kind,now,false)
    @Synchronized private fun put(identity:String,provider:String,state:RegistryState,kind:String,now:Long,requireProvider:Boolean) {
        require(identity.isNotBlank() && identity.length<=200)
        if(requireProvider)require(provider in setOf("bkash","nagad","rocket","upay"))
        val old=read().entries.filterNot { it.identity==identity && it.kind==kind }
        write(RegistryDocument((old+InternalRegistryEntry(identity,provider,state,kind,now)).takeLast(100)))
    }
}
