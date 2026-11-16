package com.unofficial.kodakremote

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kodak_remote")

data class AppSettings(
    val onboarded: Boolean = false,
    val language: String = "",
    val haptics: Boolean = true,
    val mode: String = "wifi",
    val tvHost: String? = null,
    val tvName: String? = null,
    val irAddress: Int = 0,
    val irCodes: Map<String, Int> = emptyMap(),
)

object PK {
    val onboarded = booleanPreferencesKey("onboarded")
    val language = stringPreferencesKey("language")
    val haptics = booleanPreferencesKey("haptics")
    val mode = stringPreferencesKey("mode")
    val tvHost = stringPreferencesKey("tv_host")
    val tvName = stringPreferencesKey("tv_name")
    val irAddress = intPreferencesKey("ir_address")
    val irCodes = stringPreferencesKey("ir_codes")
}

class Prefs(private val context: Context) {

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboarded = p[PK.onboarded] ?: false,
            language = p[PK.language] ?: "",
            haptics = p[PK.haptics] ?: true,
            mode = p[PK.mode] ?: "wifi",
            tvHost = p[PK.tvHost],
            tvName = p[PK.tvName],
            irAddress = p[PK.irAddress] ?: 0,
            irCodes = decode(p[PK.irCodes]),
        )
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun remove(vararg keys: Preferences.Key<*>) {
        context.dataStore.edit { p -> keys.forEach { p.remove(it) } }
    }

    /** Atomic read-modify-write so quick edits of different keys never overwrite each other. */
    suspend fun updateIrCode(name: String, code: Int?) {
        context.dataStore.edit { p ->
            val m = decode(p[PK.irCodes]).toMutableMap()
            if (code == null) m.remove(name) else m[name] = code
            p[PK.irCodes] = m.entries.joinToString(";") { it.key + "=" + it.value.toString(16) }
        }
    }

    private fun decode(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        raw.split(";").forEach { part ->
            val kv = part.split("=")
            if (kv.size == 2) kv[1].toIntOrNull(16)?.let { out[kv[0]] = it }
        }
        return out
    }
}
