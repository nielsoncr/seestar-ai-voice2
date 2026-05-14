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
        val DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
        val SEESTAR_IP = stringPreferencesKey("seestar_ip")
        val TELESCOPE_PORT = intPreferencesKey("telescope_port")
        val BORTLE_SCALE = intPreferencesKey("bortle_scale")
        val WAKE_WORDS = stringPreferencesKey("wake_words")
        val SPEAK_RESPONSES = booleanPreferencesKey("speak_responses")
        val MIN_VISIBILITY_ANGLE = intPreferencesKey("min_visibility_angle")
        val ENABLE_ACTION_BUTTONS = booleanPreferencesKey("enable_action_buttons")
    }

    private val defaultWakeWords = "seestar,seastar,see star,sea star,c star,si star,sister,seaster"

    val requireWakeWord: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[REQUIRE_WAKE_WORD] ?: true
    }

    val speakResponses: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[SPEAK_RESPONSES] ?: true
    }

    val enableActionButtons: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[ENABLE_ACTION_BUTTONS] ?: false
    }

    val minVisibilityAngle: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[MIN_VISIBILITY_ANGLE] ?: 15
    }

    val wakeWords: Flow<List<String>> = appContext.dataStore.data.map { preferences ->
        val wordsString = preferences[WAKE_WORDS] ?: defaultWakeWords
        wordsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val llmEngine: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[LLM_ENGINE] ?: "qwen2.5-1.5b-instruct.litertlm"
    }

    val debugLogging: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[DEBUG_LOGGING] ?: true
    }

    val seestarIp: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[SEESTAR_IP] ?: "10.0.0.1"
    }

    val telescopePort: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[TELESCOPE_PORT] ?: 32323
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

    suspend fun setDebugLogging(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[DEBUG_LOGGING] = enabled
        }
    }

    suspend fun setSeestarIp(ip: String) {
        appContext.dataStore.edit { preferences ->
            preferences[SEESTAR_IP] = ip
        }
    }

    suspend fun setTelescopePort(port: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[TELESCOPE_PORT] = port
        }
    }

    suspend fun setBortleScale(scale: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[BORTLE_SCALE] = scale
        }
    }

    suspend fun setWakeWords(words: List<String>) {
        appContext.dataStore.edit { preferences ->
            preferences[WAKE_WORDS] = words.joinToString(",")
        }
    }

    suspend fun setSpeakResponses(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[SPEAK_RESPONSES] = enabled
        }
    }

    suspend fun setEnableActionButtons(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[ENABLE_ACTION_BUTTONS] = enabled
        }
    }

    suspend fun setMinVisibilityAngle(angle: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[MIN_VISIBILITY_ANGLE] = angle
        }
    }
}
