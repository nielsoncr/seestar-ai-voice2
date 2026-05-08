package com.example.seestarvoice2.intelligence

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TtsManager", "Language not supported")
                } else {
                    isInitialized = true
                    Log.d("TtsManager", "TTS Initialized successfully")
                }
            } else {
                Log.e("TtsManager", "Initialization failed")
            }
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w("TtsManager", "TTS not initialized yet")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
