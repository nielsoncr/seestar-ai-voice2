package com.example.seestarvoice2.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        val REQUIRE_WAKE_WORD = booleanPreferencesKey("require_wake_word")
        val LLM_ENGINE = stringPreferencesKey("llm_engine")
        val SEESTAR_IP = stringPreferencesKey("seestar_ip")
        val BORTLE_SCALE = intPreferencesKey("bortle_scale")
    }

    val requireWakeWord: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[REQUIRE_WAKE_WORD] ?: true
    }

    val llmEngine: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[LLM_ENGINE] ?: "gemma-2b-it-cpu-int4.bin"
    }

    val seestarIp: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[SEESTAR_IP] ?: "10.0.0.1"
    }

    val bortleScale: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[BORTLE_SCALE] ?: 5
    }

    suspend fun setRequireWakeWord(required: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[REQUIRE_WAKE_WORD] = required
        }
    }

    suspend fun setLlmEngine(engine: String) {
        appContext.dataStore.edit { preferences ->
            preferences[LLM_ENGINE] = engine
        }
    }

    suspend fun setSeestarIp(ip: String) {
        appContext.dataStore.edit { preferences ->
            preferences[SEESTAR_IP] = ip
        }
    }

    suspend fun setBortleScale(scale: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[BORTLE_SCALE] = scale
        }
    }
}
