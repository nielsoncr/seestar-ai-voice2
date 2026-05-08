package com.example.seestarvoice2.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.seestarvoice2.astronomy.AstroUtils
import com.example.seestarvoice2.data.AppDatabase
import com.example.seestarvoice2.data.NgcObject
import com.example.seestarvoice2.intelligence.IntentProcessor
import com.example.seestarvoice2.intelligence.TelescopeController
import com.example.seestarvoice2.intelligence.TelescopeIntent
import com.example.seestarvoice2.intelligence.TtsManager
import com.example.seestarvoice2.settings.SettingsManager
import com.example.seestarvoice2.voice.VoiceRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(
    application: Application,
    private val ttsManager: TtsManager,
    private val settingsManager: SettingsManager
) : AndroidViewModel(application) {
    
    val logs = mutableStateListOf<String>()
    var isModelLoading by mutableStateOf(true)
        private set
    
    var requireWakeWord by mutableStateOf(true)
        private set
    
    var currentLlmEngine by mutableStateOf("gemma-2b-it-cpu-int4.bin")
        private set

    var seestarIp by mutableStateOf("10.0.0.1")
        private set

    var bortleScale by mutableStateOf(5)
        private set

    var capturedImageUrl by mutableStateOf<String?>(null)
        private set

    var currentLocation by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    private val modelFileName: String
        get() = currentLlmEngine

    private var intentProcessor: IntentProcessor? = null
    private val dateFormatter = SimpleDateFormat("yyyy-MMM-dd HH:mm:ss", Locale.getDefault())
    private val db = AppDatabase.getDatabase(application)
    private val telescopeController = TelescopeController()

    private val voiceRecognizer = VoiceRecognizer(
        context = application,
        onResult = { text -> onUserSpeechInput(text) },
        onError = { error -> addLog("Voice Error: $error") }
    )

    init {
        // Load initial settings synchronously for initialization
        runBlocking {
            requireWakeWord = settingsManager.requireWakeWord.first()
            currentLlmEngine = settingsManager.llmEngine.first()
            seestarIp = settingsManager.seestarIp.first()
            bortleScale = settingsManager.bortleScale.first()
        }
        
        // Observe settings changes
        viewModelScope.launch {
            settingsManager.requireWakeWord.collect { requireWakeWord = it }
        }
        viewModelScope.launch {
            settingsManager.seestarIp.collect { seestarIp = it }
        }
        viewModelScope.launch {
            settingsManager.bortleScale.collect { bortleScale = it }
        }
        
        seedDatabaseIfNeeded()
        initializeIntelligence()
    }

    fun updateRequireWakeWord(required: Boolean) {
        viewModelScope.launch {
            settingsManager.setRequireWakeWord(required)
        }
    }

    fun updateSeestarIp(ip: String) {
        viewModelScope.launch {
            settingsManager.setSeestarIp(ip)
        }
    }

    fun updateBortleScale(scale: Int) {
        viewModelScope.launch {
            settingsManager.setBortleScale(scale)
        }
    }

    fun updateCurrentLocation(lat: Double, lon: Double) {
        currentLocation = Pair(lat, lon)
        addLog("Location updated: $lat, $lon")
    }

    private fun seedDatabaseIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = db.ngcDao().getCount()
            val sunMissing = db.ngcDao().getObjectByName("SUN") == null
            val moonMissing = db.ngcDao().getObjectByName("MOON") == null
            addLog("Checking database (Current count: $count)...")
            
            // Re-seed if count is low or schema changed or special objects are missing
            if (count < 1000 || sunMissing || moonMissing) {
                addLog("Wiping and seeding full OpenNGC database from CSV (v2.2)...")
                try {
                    val objects = mutableListOf<NgcObject>()
                    getApplication<Application>().assets.open("ngc-ic-messier-catalog.csv").bufferedReader().use { reader ->
                        reader.readLine() // Skip header
                        reader.forEachLine { line ->
                            val columns = line.split(";")
                            if (columns.size >= 26) {
                                val name = columns[1].ifEmpty { columns[0] }
                                val commonNames = columns[25].ifEmpty { null }
                                
                                if (name.isNotEmpty()) {
                                    val ra = AstroUtils.parseRA(columns[7])
                                    val dec = AstroUtils.parseDec(columns[8])
                                    val type = columns[6]
                                    val constellation = columns[9]
                                    val mag = columns[14].toDoubleOrNull()
                                    
                                    objects.add(NgcObject(name.uppercase(), commonNames, ra, dec, type, constellation, mag))
                                }
                            }
                        }
                    }

                    // Add a few planets as special placeholders
                    objects.add(NgcObject("MERCURY", "The Swift Planet", 0.0, 0.0, "Planet", "Varies", -0.4))
                    objects.add(NgcObject("VENUS", "The Morning Star", 0.0, 0.0, "Planet", "Varies", -4.4))
                    objects.add(NgcObject("MARS", "The Red Planet", 0.0, 0.0, "Planet", "Varies", -2.0))
                    objects.add(NgcObject("JUPITER", "King of Planets", 0.0, 0.0, "Planet", "Varies", -2.5))
                    objects.add(NgcObject("SATURN", "The Ringed Planet", 0.0, 0.0, "Planet", "Varies", 0.5))
                    objects.add(NgcObject("URANUS", "The Ice Giant", 0.0, 0.0, "Planet", "Varies", 5.7))
                    objects.add(NgcObject("NEPTUNE", "The Distant Giant", 0.0, 0.0, "Planet", "Varies", 7.8))
                    objects.add(NgcObject("PLUTO", "The Dwarf Planet", 0.0, 0.0, "Planet", "Varies", 14.5))
                    objects.add(NgcObject("SUN", "The Sun", 0.0, 0.0, "Star", "Varies", -26.7))
                    objects.add(NgcObject("MOON", "The Moon", 0.0, 0.0, "Moon", "Varies", -12.74))

                    if (objects.isNotEmpty()) {
                        db.clearAllTables()
                        objects.chunked(1000).forEach { chunk ->
                            db.ngcDao().insertAll(chunk)
                        }
                        addLog("Database successfully seeded with ${objects.size} objects.")
                    }
                } catch (e: Exception) {
                    addLog("Error seeding database: ${e.message}")
                }
            } else {
                addLog("Database already initialized with $count objects.")
            }
        }
    }

    private fun initializeIntelligence() {
        viewModelScope.launch {
            addLog("Initializing intelligence (Engine: $modelFileName)...")
            
            val modelFile = File(getApplication<Application>().filesDir, modelFileName)
            
            if (!modelFile.exists()) {
                addLog("Copying model from assets...")
                withContext(Dispatchers.IO) {
                    copyModelFromAssets(modelFile)
                }
            }

            if (modelFile.exists()) {
                withContext(Dispatchers.IO) {
                    try {
                        intentProcessor = IntentProcessor(getApplication(), modelFile.absolutePath)
                        addLog("LLM model loaded.")
                    } catch (e: Exception) {
                        addLog("Error loading LLM: ${e.message}")
                    }
                }
            } else {
                addLog("Error: Model file not found.")
            }

            isModelLoading = false
            addLog("System ready.")
            ttsManager.speak("System ready.")
            voiceRecognizer.startListening()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecognizer.destroy()
    }

    private suspend fun copyModelFromAssets(targetFile: File) = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().assets.open(modelFileName).use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            addLog("Error copying model: ${e.message}")
        }
    }

    fun addLog(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val timestamp = dateFormatter.format(Date())
            logs.add(0, "[$timestamp] $message")
        }
    }

    fun onUserSpeechInput(text: String) {
        val normalized = text.lowercase()
        
        if (requireWakeWord) {
            if (normalized.contains("seestar")) {
                val command = text.replace("seestar", "", ignoreCase = true).trim()
                if (command.isNotEmpty()) {
                    processCommand(command, text)
                } else {
                    addLog("Wake word 'SeeStar' detected, but no command followed.")
                }
            }
        } else {
            processCommand(text, text)
        }
    }

    private fun processCommand(command: String, rawText: String) {
        addLog("Input: $rawText")
        if (isModelLoading) {
            ttsManager.speak("System is still loading. Please wait.")
            return
        }
        
        val intent = intentProcessor?.processIntent(command) ?: TelescopeIntent.Unknown(command)
        handleIntent(intent)
    }

    private fun handleIntent(intent: TelescopeIntent) {
        when (intent) {
            is TelescopeIntent.VisibilityQuery -> {
                queryObjectVisibility(intent)
            }
            is TelescopeIntent.OpenArm -> controlArm(true)
            is TelescopeIntent.CloseArm -> controlArm(false)
            is TelescopeIntent.PowerDown -> powerDown()
            is TelescopeIntent.PointAndCapture -> pointAndCapture(intent.target)
            is TelescopeIntent.Move -> {
                val response = "Moving telescope ${intent.direction}."
                addLog("Intent: Move (${intent.direction})")
                ttsManager.speak(response)
            }
            is TelescopeIntent.GOTO -> {
                val response = "Pointing to ${intent.target}."
                addLog("Intent: GOTO (${intent.target})")
                ttsManager.speak(response)
            }
            is TelescopeIntent.Capture -> {
                val response = "Capturing image."
                addLog("Intent: Capture")
                ttsManager.speak(response)
            }
            is TelescopeIntent.Stop -> {
                val response = "Stopping all movement."
                addLog("Intent: Stop")
                ttsManager.speak(response)
            }
            else -> { // TelescopeIntent.Unknown
                val response = "I'm sorry, I didn't understand that command."
                addLog("Intent: Unknown (${intent})")
                ttsManager.speak(response)
            }
        }
    }

    private fun controlArm(open: Boolean) {
        val action = if (open) "Opening" else "Closing"
        addLog("Action: $action telescope arm at $seestarIp")
        ttsManager.speak("$action arm.")
        if (open) {
            telescopeController.openArm(seestarIp, { addLog("Arm opened successfully.") }, { addLog("Error: $it") })
        } else {
            telescopeController.closeArm(seestarIp, { addLog("Arm closed successfully.") }, { addLog("Error: $it") })
        }
    }

    private fun powerDown() {
        addLog("Action: Powering down telescope at $seestarIp")
        ttsManager.speak("Powering down.")
        telescopeController.powerDown(seestarIp, { addLog("Shutdown signal sent.") }, { addLog("Note: $it") })
    }

    private fun pointAndCapture(targetName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ngcObject = db.ngcDao().getObjectByName(targetName.uppercase())
                ?: db.ngcDao().searchObjects(targetName).firstOrNull()

            if (ngcObject != null) {
                addLog("Action: Pointing to ${ngcObject.name} and capturing image.")
                ttsManager.speak("Pointing to ${ngcObject.name} and taking a photo.")
                
                telescopeController.slewAndCapture(seestarIp, ngcObject.ra, ngcObject.dec, { result ->
                    addLog(result)
                    // Mock image display logic
                    capturedImageUrl = "https://via.placeholder.com/800x600.png?text=${ngcObject.name}+Captured"
                    ttsManager.speak("Image of ${ngcObject.name} captured and displayed.")
                }, { error ->
                    addLog("Error during capture: $error")
                    ttsManager.speak("Sorry, I encountered an error while trying to capture ${ngcObject.name}.")
                })
            } else {
                addLog("Error: Could not find $targetName in catalog.")
                ttsManager.speak("I couldn't find $targetName in my database.")
            }
        }
    }

    private fun queryObjectVisibility(intent: TelescopeIntent.VisibilityQuery) {
        viewModelScope.launch(Dispatchers.IO) {
            // Clean target of any lingering "SeeStar" just in case
            val target = intent.target.replace("seestar", "", ignoreCase = true).trim()
            val ngcObject = db.ngcDao().getObjectByName(target.uppercase())
                ?: db.ngcDao().searchObjects(target).firstOrNull()

            if (ngcObject != null) {
                // Default to current GPS location, fallback to London
                var lat = currentLocation?.first ?: 51.5
                var lon = currentLocation?.second ?: -0.1
                var locationName = if (currentLocation != null) "current location" else "London"

                intent.locationSpec?.let { spec ->
                    // Very basic parser for "Lat, Lon" or known names
                    if (spec.contains(",")) {
                        val coords = spec.split(",")
                        lat = coords[0].trim().toDoubleOrNull() ?: lat
                        lon = coords[1].trim().toDoubleOrNull() ?: lon
                        locationName = "specified coordinates"
                    } else if (spec.lowercase().contains("new york")) {
                        lat = 40.7
                        lon = -74.0
                        locationName = "New York"
                    } else if (spec.lowercase().contains("tokyo")) {
                        lat = 35.6
                        lon = 139.6
                        locationName = "Tokyo"
                    }
                }

                if (intent.predictive) {
                    findNextVisibility(ngcObject, lat, lon, locationName, target)
                } else {
                    checkCurrentVisibility(ngcObject, lat, lon, locationName, target, intent.timeSpec)
                }
            } else {
                val response = "I couldn't find $target in my database."
                addLog("Visibility: $response")
                ttsManager.speak(response)
            }
        }
    }

    private fun checkCurrentVisibility(ngcObject: NgcObject, lat: Double, lon: Double, locationName: String, spokenName: String, timeSpec: String? = null) {
        val requestedTime = AstroUtils.parseTimeSpec(timeSpec) ?: Calendar.getInstance()
        val visibilityCheck = AstroUtils.isVisibleToSeeStar(
            objRa = ngcObject.ra,
            objDec = ngcObject.dec,
            objType = ngcObject.type,
            objMag = ngcObject.magnitude,
            lat = lat,
            lon = lon,
            time = requestedTime,
            bortle = bortleScale
        )
        
        val altAz = AstroUtils.calculateAltAz(ngcObject.ra, ngcObject.dec, lat, lon, requestedTime)
        val timePrefix = if (timeSpec != null) "At ${timeSpec.uppercase()}, " else ""
        
        val response = if (visibilityCheck.first) {
            "${timePrefix}$spokenName is ${visibilityCheck.second} in $locationName at an altitude of ${altAz.altitude.toInt()} degrees."
        } else {
            "${timePrefix}$spokenName is ${visibilityCheck.second} in $locationName."
        }
        
        addLog("Visibility: $response")
        ttsManager.speak(response)
    }

    private fun findNextVisibility(ngcObject: NgcObject, lat: Double, lon: Double, locationName: String, spokenName: String) {
        val searchTime = Calendar.getInstance()
        var bestAlt = -90.0
        var bestTime: Calendar? = null
        var bestVisibilityInfo = ""

        // Scan the next 24 hours in 15-minute increments
        for (i in 0 until (24 * 4)) {
            val vCheck = AstroUtils.isVisibleToSeeStar(
                objRa = ngcObject.ra,
                objDec = ngcObject.dec,
                objType = ngcObject.type,
                objMag = ngcObject.magnitude,
                lat = lat,
                lon = lon,
                time = searchTime,
                bortle = bortleScale
            )

            if (vCheck.first) {
                val altAz = AstroUtils.calculateAltAz(ngcObject.ra, ngcObject.dec, lat, lon, searchTime)
                if (altAz.altitude > bestAlt) {
                    bestAlt = altAz.altitude
                    bestTime = searchTime.clone() as Calendar
                    bestVisibilityInfo = vCheck.second
                }
            }
            searchTime.add(Calendar.MINUTE, 15)
        }

        if (bestTime != null && bestAlt > 0) {
            val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(bestTime.time)
            val response = "$spokenName will be $bestVisibilityInfo in $locationName at $timeString, reaching ${bestAlt.toInt()} degrees."
            addLog("Predictive: $response")
            ttsManager.speak(response)
        } else {
            val response = "$spokenName will not be visible in $locationName in the next 24 hours (due to daylight or light pollution)."
            addLog("Predictive: $response")
            ttsManager.speak(response)
        }
    }
}
