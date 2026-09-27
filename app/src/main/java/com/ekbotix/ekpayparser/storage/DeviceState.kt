package com.ekbotix.ekpayparser.storage

import com.ekbotix.ekpayparser.model.DeviceIdentity
import com.google.gson.Gson

data class PendingKey(val deviceId: String, val expectedVersion: Int, val alias: String)
data class LocalState(val identity: DeviceIdentity? = null, val pending: PendingKey? = null, val softwareConsent: Boolean = false,
    val lastSuccess: Long? = null, val lastFailure: Long? = null, val summary: String = "Not paired", val debugLogging: Boolean = false, val recoveryAliases:List<String> = emptyList())
class DeviceState(private val protected: SecureVault) {
    private val gson=Gson()
    @Synchronized fun load(): LocalState = protected.read("state.v1")?.let { gson.fromJson(it.toString(Charsets.UTF_8),LocalState::class.java) } ?: LocalState()
    @Synchronized fun save(state: LocalState) { protected.write("state.v1",gson.toJson(state).toByteArray()) }
    // Deliberately no token/proof/signature field in any persisted state.
}
