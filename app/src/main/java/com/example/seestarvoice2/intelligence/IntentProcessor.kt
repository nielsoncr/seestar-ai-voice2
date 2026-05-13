package com.example.seestarvoice2.intelligence

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import java.io.File
import android.util.Log

sealed class TelescopeIntent {
    data class Move(val direction: String) : TelescopeIntent()
    data class GOTO(val target: String) : TelescopeIntent()
    data class VisibilityQuery(
        val target: String,
        val timeSpec: String? = null,
        val locationSpec: String? = null,
        val predictive: Boolean = false
    ) : TelescopeIntent()
    object Capture : TelescopeIntent()
    object Stop : TelescopeIntent()
    object OpenArm : TelescopeIntent()
    object CloseArm : TelescopeIntent()
    object PowerDown : TelescopeIntent()
    object Connect : TelescopeIntent()
    data class PointAndCapture(val target: String) : TelescopeIntent()
    data class Unknown(val rawText: String) : TelescopeIntent()
}

class IntentProcessor(private val context: Context, private val modelPath: String) {
    private var engine: Engine? = null

    init {
        val modelFile = File(modelPath)
        if (modelFile.exists()) {
            val config = EngineConfig(
                modelPath = modelPath,
                maxNumTokens = 128,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path
            )
            engine = Engine(config)
            try {
                engine?.initialize()
            } catch (e: Exception) {
                Log.e("IntentProcessor", "Failed to initialize LiteRT-LM engine", e)
            }
        } else {
            Log.e("IntentProcessor", "Model file not found at $modelPath")
        }
    }

    fun processIntent(text: String): TelescopeIntent {
        val normalized = text.lowercase()

        // Detect complex "Point to X and take a picture"
        if ((normalized.contains("point to") || normalized.contains("find") || normalized.contains("go to")) && 
            (normalized.contains("capture") || normalized.contains("take a picture") || normalized.contains("photo") || normalized.contains("image"))) {
            val target = extractTarget(text, listOf("point", "to", "find", "go", "and", "take", "a", "picture", "photo", "image", "capture", "display", "show"))
            return TelescopeIntent.PointAndCapture(target)
        }

        // Refined time and location extraction
        var timeSpec: String? = null
        var locationSpec: String? = null
        var cleanedText = text

        // Extract time (e.g., "at 11 PM", "at 10:30")
        val timeRegex = Regex("\\bat\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b", RegexOption.IGNORE_CASE)
        timeRegex.find(normalized)?.let {
            timeSpec = it.groupValues[1]
            cleanedText = cleanedText.replace(it.value, " ", ignoreCase = true)
        }

        // Extract location (e.g., "in New York")
        val locationRegex = Regex("\\bin\\s+([^?.]+)\\b", RegexOption.IGNORE_CASE)
        locationRegex.find(cleanedText.lowercase())?.let {
            locationSpec = it.groupValues[1].trim()
            cleanedText = cleanedText.replace(it.value, " ", ignoreCase = true)
        }

        val finalNormalized = cleanedText.lowercase()

        return when {
            finalNormalized.contains("open") && finalNormalized.contains("arm") -> TelescopeIntent.OpenArm
            finalNormalized.contains("close") && finalNormalized.contains("arm") -> TelescopeIntent.CloseArm
            finalNormalized.contains("power down") || finalNormalized.contains("shut down") || finalNormalized.contains("turn off") -> TelescopeIntent.PowerDown
            finalNormalized.contains("connect") -> TelescopeIntent.Connect
            
            // Priority 1: Direct target commands (GOTO)
            finalNormalized.contains("show me") || finalNormalized.contains("move to") || 
                    finalNormalized.contains("go to") || finalNormalized.contains("find") || 
                    finalNormalized.contains("point to") -> {
                val target = extractTarget(cleanedText, listOf("go", "to", "find", "point", "show", "me", "move", "the"))
                TelescopeIntent.GOTO(target)
            }

            // Priority 2: Visibility queries
            (finalNormalized.contains("when") && (finalNormalized.contains("visible") || finalNormalized.contains("up"))) ||
                    finalNormalized.contains("next visible") -> {
                val target = extractTarget(cleanedText, listOf("when", "visible", "up", "next", "is", "will", "be"))
                TelescopeIntent.VisibilityQuery(target, predictive = true, locationSpec = locationSpec, timeSpec = timeSpec)
            }
            finalNormalized.contains("is") && (finalNormalized.contains("visible") || finalNormalized.contains("up")) -> {
                val target = extractTarget(cleanedText, listOf("is", "visible", "up"))
                TelescopeIntent.VisibilityQuery(target, locationSpec = locationSpec, timeSpec = timeSpec)
            }
            finalNormalized.contains("show") || finalNormalized.contains("view") -> {
                val target = extractTarget(cleanedText, listOf("show", "view"))
                TelescopeIntent.VisibilityQuery(target, locationSpec = locationSpec, timeSpec = timeSpec)
            }

            // Priority 3: Movement and generic commands
            finalNormalized.contains("move") || finalNormalized.contains("slew") -> {
                val direction = when {
                    finalNormalized.contains("left") -> "left"
                    finalNormalized.contains("right") -> "right"
                    finalNormalized.contains("up") -> "up"
                    finalNormalized.contains("down") -> "down"
                    else -> "unknown"
                }
                TelescopeIntent.Move(direction)
            }
            finalNormalized.contains("capture") || finalNormalized.contains("take a picture") || finalNormalized.contains("photo") -> {
                TelescopeIntent.Capture
            }
            finalNormalized.contains("stop") || finalNormalized.contains("halt") -> {
                TelescopeIntent.Stop
            }
            else -> {
                TelescopeIntent.Unknown(text)
            }
        }
    }

    private fun extractTarget(text: String, stopWords: List<String>): String {
        var result = text.lowercase()
        // Sort stopWords by length descending to replace longer phrases first
        // and use word boundaries to avoid partial matches (e.g., 'is' in 'visible')
        stopWords.sortedByDescending { it.length }.forEach { word ->
            result = result.replace(Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE), " ")
        }
        return result.replace("?", "").trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
