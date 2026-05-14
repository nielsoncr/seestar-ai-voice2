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
    private val settingsManager: SettingsManager
) : AndroidViewModel(application) {
    
    val logs = mutableStateListOf<String>()
    var isModelLoading by mutableStateOf(true)
        private set
    
    var requireWakeWord by mutableStateOf(true)
        private set

    var debugLogging by mutableStateOf(true)
        private set
    
    var currentLlmEngine by mutableStateOf("qwen2.5-1.5b-instruct.litertlm")
        private set

    var seestarIp by mutableStateOf("10.0.0.1")
        private set

    var telescopePort by mutableStateOf(32323)
        private set

    var bortleScale by mutableStateOf(5)
        private set

    var minVisibilityAngle by mutableStateOf(15)
        private set

    var wakeWords by mutableStateOf<List<String>>(emptyList())
        private set

    var speakResponses by mutableStateOf(true)
        private set

    var enableActionButtons by mutableStateOf(false)
        private set

    val assistantResponses = mutableStateListOf<String>()

    var capturedImageUrl by mutableStateOf<String?>(null)
        private set

    var currentLocation by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    private val modelFileName: String
        get() = currentLlmEngine

    private var intentProcessor: IntentProcessor? = null
    private val dateFormatter = SimpleDateFormat("yyyy-MMM-dd HH:mm:ss", Locale.getDefault())
    private val db = AppDatabase.getDatabase(application)
    private val ttsManager = TtsManager(application)
    private val telescopeController = TelescopeController(
        port = 32323,
        onLog = { if (debugLogging) addLog("HTTP: $it") },
        onConnect = { name -> 
            val msg = "Connected to $name at $seestarIp"
            addLog(msg)
            speakAssistantResponse(msg)
        }
    )

    private val voiceRecognizer = VoiceRecognizer(
        context = application,
        onResult = { text -> onUserSpeechInput(text) },
        onError = { error -> addLog("Voice Error: $error") }
    )

    private var lastWakeWordTimestamp: Long = 0
    private val WAKE_WORD_GRACE_PERIOD_MS = 8000 // 8 seconds

    init {
        addLog("Build Version: ${com.example.seestarvoice2.BuildConfig.VERSION_NAME}")

        // Load initial settings synchronously for initialization
        runBlocking {
            requireWakeWord = settingsManager.requireWakeWord.first()
            debugLogging = settingsManager.debugLogging.first()
            currentLlmEngine = settingsManager.llmEngine.first()
            seestarIp = settingsManager.seestarIp.first()
            telescopePort = settingsManager.telescopePort.first()
            telescopeController.port = telescopePort
            bortleScale = settingsManager.bortleScale.first()
            minVisibilityAngle = settingsManager.minVisibilityAngle.first()
            wakeWords = settingsManager.wakeWords.first()
            speakResponses = settingsManager.speakResponses.first()
            enableActionButtons = settingsManager.enableActionButtons.first()
        }
        
        // Observe settings changes
        viewModelScope.launch {
            settingsManager.requireWakeWord.collect { requireWakeWord = it }
        }
        viewModelScope.launch {
            settingsManager.speakResponses.collect { speakResponses = it }
        }
        viewModelScope.launch {
            settingsManager.enableActionButtons.collect { enableActionButtons = it }
        }
        viewModelScope.launch {
            settingsManager.minVisibilityAngle.collect { minVisibilityAngle = it }
        }
        viewModelScope.launch {
            settingsManager.debugLogging.collect { debugLogging = it }
        }
        viewModelScope.launch {
            settingsManager.seestarIp.collect { 
                seestarIp = it
                checkTelescopeConnection()
            }
        }
        viewModelScope.launch {
            settingsManager.telescopePort.collect { 
                telescopePort = it
                telescopeController.port = it
                checkTelescopeConnection()
            }
        }
        viewModelScope.launch {
            settingsManager.bortleScale.collect { bortleScale = it }
        }
        viewModelScope.launch {
            settingsManager.wakeWords.collect { wakeWords = it }
        }
        
        seedDatabaseIfNeeded()
        initializeIntelligence()
        checkTelescopeConnection()
    }

    fun updateRequireWakeWord(required: Boolean) {
        viewModelScope.launch {
            settingsManager.setRequireWakeWord(required)
        }
    }

    fun updateDebugLogging(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setDebugLogging(enabled)
        }
    }

    fun updateSeestarIp(ip: String) {
        viewModelScope.launch {
            settingsManager.setSeestarIp(ip)
        }
    }

    fun updateTelescopePort(port: String) {
        val portInt = port.toIntOrNull() ?: 32323
        viewModelScope.launch {
            settingsManager.setTelescopePort(portInt)
        }
    }

    fun updateBortleScale(scale: Int) {
        viewModelScope.launch {
            settingsManager.setBortleScale(scale)
        }
    }

    fun updateMinVisibilityAngle(angle: Int) {
        viewModelScope.launch {
            settingsManager.setMinVisibilityAngle(angle)
        }
    }

    fun updateSpeakResponses(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSpeakResponses(enabled)
        }
    }

    fun updateEnableActionButtons(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setEnableActionButtons(enabled)
        }
    }

    fun addWakeWord(word: String) {
        val trimmed = word.trim().lowercase()
        if (trimmed.isNotEmpty() && !wakeWords.contains(trimmed)) {
            viewModelScope.launch {
                settingsManager.setWakeWords(wakeWords + trimmed)
            }
        }
    }

    fun removeWakeWord(word: String) {
        viewModelScope.launch {
            settingsManager.setWakeWords(wakeWords - word)
        }
    }

    fun updateCurrentLocation(lat: Double, lon: Double) {
        currentLocation = Pair(lat, lon)
        addLog("Location updated: $lat, $lon")
    }

    private fun checkTelescopeConnection(force: Boolean = false) {
        telescopeController.isConnected(seestarIp, deviceType = "telescope", forceCheck = force, onResult = { connected ->
            if (connected) {
                telescopeController.getTelescopeName(seestarIp, { name ->
                    val msg = "Connected to $name at $seestarIp"
                    addLog(msg)
                    speakAssistantResponse(msg)
                }, {
                    addLog("Connected to $seestarIp (Name fetch failed)")
                })
            } else {
                telescopeController.connect(seestarIp, deviceType = "telescope", onComplete = {
                    telescopeController.getTelescopeName(seestarIp, { name ->
                        val msg = "Connected to $name at $seestarIp"
                        addLog(msg)
                        speakAssistantResponse(msg)
                    }, {
                        addLog("Connected to $seestarIp")
                    })
                }, onError = { error ->
                    addLog("Connection check failed: $error")
                })
            }
        }, onError = { error ->
            // Try connecting anyway
            telescopeController.connect(seestarIp, deviceType = "telescope", onComplete = {
                telescopeController.getTelescopeName(seestarIp, { name ->
                    val msg = "Connected to $name at $seestarIp"
                    addLog(msg)
                    speakAssistantResponse(msg)
                }, {
                    addLog("Connected to $seestarIp")
                })
            }, onError = {
                addLog("Status check error: $error")
            })
        })
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
                    objects.add(NgcObject("SUN", "The Sun", 0.0, 0.0, "SUN", "Varies", -26.7))
                    objects.add(NgcObject("MOON", "The Moon", 0.0, 0.0, "MOON", "Varies", -12.74))

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
            speakAssistantResponse("System ready.")
            voiceRecognizer.startListening()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecognizer.destroy()
        ttsManager.shutdown()
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

    private fun speakAssistantResponse(text: String) {
        viewModelScope.launch(Dispatchers.Main) {
            assistantResponses.add(0, text)
            if (speakResponses) {
                ttsManager.speak(text)
            }
        }
    }

    fun onUserSpeechInput(text: String) {
        val normalized = text.lowercase()
        // Wake words are now loaded from settings
        val currentWakeWords = wakeWords
        val now = System.currentTimeMillis()
        val isWithinGracePeriod = now - lastWakeWordTimestamp < WAKE_WORD_GRACE_PERIOD_MS
        
        val foundWakeWord = currentWakeWords.find { normalized.contains(it) }
        
        // Scenario 1: Wake word is in this phrase
        if (foundWakeWord != null) {
            lastWakeWordTimestamp = now
            var command = text
            currentWakeWords.forEach { word ->
                command = command.replace(Regex(word, RegexOption.IGNORE_CASE), "")
            }
            command = command.trim().replace(Regex("^[,.?! ]+"), "").trim()
            
            if (command.isNotEmpty()) {
                addLog("Wake word detected: '$foundWakeWord'")
                processCommand(command, text)
            } else {
                addLog("Wake word detected. Listening for command...")
                speakAssistantResponse("Yes?") 
            }
            return
        }

        // Scenario 2: No wake word in this phrase, but maybe we don't need it
        if (!requireWakeWord || isWithinGracePeriod) {
            if (text.trim().isNotEmpty()) {
                if (isWithinGracePeriod && requireWakeWord) {
                     addLog("Processing (within grace period): \"$text\"")
                }
                processCommand(text, text)
            }
        } else {
            // Scenario 3: Wake word required and missing
            if (debugLogging) {
                addLog("Ignored (no wake word): \"$text\"")
            }
        }
    }

    private fun processCommand(command: String, rawText: String) {
        addLog("Input: $rawText")
        if (isModelLoading) {
            speakAssistantResponse("System is still loading. Please wait.")
            return
        }
        
        val intent = intentProcessor?.processIntent(command) ?: TelescopeIntent.Unknown(command)
        addLog("Detected Intent: $intent")
        handleIntent(intent)
    }

    fun handleIntent(intent: TelescopeIntent) {
        when (intent) {
            is TelescopeIntent.VisibilityQuery -> {
                queryObjectVisibility(intent)
            }
            is TelescopeIntent.Connect -> {
                addLog("Action: Connecting to telescope...")
                checkTelescopeConnection()
            }
            is TelescopeIntent.OpenArm -> controlArm(true)
            is TelescopeIntent.CloseArm -> controlArm(false)
            is TelescopeIntent.PowerDown -> powerDown()
            is TelescopeIntent.PointAndCapture -> pointAndCapture(intent.target)
            is TelescopeIntent.Move -> {
                val response = "Moving telescope ${intent.direction}."
                addLog("Intent: Move (${intent.direction})")
                speakAssistantResponse(response)
            }
            is TelescopeIntent.GOTO -> {
                gotoObject(intent.target)
            }
            is TelescopeIntent.Capture -> {
                capture()
            }
            is TelescopeIntent.QuickCapture -> {
                quickPik()
            }
            is TelescopeIntent.Stop -> {
                val response = "Stopping all movement."
                addLog("Intent: Stop")
                speakAssistantResponse(response)
            }
            else -> { // TelescopeIntent.Unknown
                val response = "I'm sorry I didn't understand you"
                addLog("Intent: Unknown (${intent})")
                speakAssistantResponse(response)
            }
        }
    }

    private fun controlArm(open: Boolean) {
        val action = if (open) "Opening" else "Closing"
        addLog("Action: $action telescope arm at $seestarIp")
        speakAssistantResponse("$action arm.")
        if (open) {
            val now = Calendar.getInstance()
            val lat = currentLocation?.first ?: 51.5
            val lon = currentLocation?.second ?: -0.1
            val zenith = AstroUtils.getZenithCoordinates(lat, lon, now)
            
            // Open arm by slewing to Zenith coordinates using Equatorial slew (more compatible)
            telescopeController.openArm(seestarIp, zenith.first, zenith.second, {
                addLog("Arm opened successfully via Zenith slew.") 
            }, { 
                addLog("Error: $it") 
            })
        } else {
            telescopeController.closeArm(seestarIp, { addLog("Arm closed successfully.") }, { addLog("Error: $it") })
        }
    }

    private fun powerDown() {
        addLog("Action: Powering down telescope at $seestarIp")
        speakAssistantResponse("Powering down.")
        telescopeController.powerDown(seestarIp, { addLog("Shutdown signal sent.") }, { addLog("Note: $it") })
    }

    private fun quickPik() {
        addLog("Action: Performing quick capture at $seestarIp")
        speakAssistantResponse("Taking a quick picture.")
        
        // Clear previous capture image
        capturedImageUrl = null
        
        telescopeController.quickCapture(seestarIp, { progress ->
            addLog("Quick Capture: $progress")
        }, { result, imageUrl ->
            addLog(result)
            viewModelScope.launch(Dispatchers.Main) {
                capturedImageUrl = imageUrl
                addLog("Retrieved quick image from: $imageUrl")
                speakAssistantResponse("Quick picture displayed.")
            }
        }, { error ->
            addLog("Quick Capture Error: $error")
            speakAssistantResponse("Sorry, I couldn't take a quick picture.")
        })
    }

    private fun capture() {
        addLog("Action: Performing standard capture at $seestarIp")
        speakAssistantResponse("Capturing image.")
        
        // Clear previous capture image
        capturedImageUrl = null
        
        // We need a dummy ra/dec if we want to reuse slewAndCapture, 
        // OR we should add a capture-only method to TelescopeController.
        // For now, let's assume we capture the current position.
        // I'll add a 'captureOnly' method to TelescopeController.
        
        telescopeController.captureOnly(seestarIp, { progress ->
            addLog("Capture: $progress")
        }, { result, imageUrl ->
            addLog(result)
            viewModelScope.launch(Dispatchers.Main) {
                capturedImageUrl = imageUrl
                addLog("Retrieved image from: $imageUrl")
                speakAssistantResponse("Image captured and displayed.")
            }
        }, { error ->
            addLog("Capture Error: $error")
            speakAssistantResponse("Sorry, I couldn't capture the image.")
        })
    }

    private fun gotoObject(targetName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ngcObject = db.ngcDao().getObjectByName(targetName)
                ?: db.ngcDao().searchObjects(targetName).firstOrNull()

            if (ngcObject != null) {
                addLog("Action: Pointing to ${ngcObject.name} at $seestarIp")
                speakAssistantResponse("Pointing to ${ngcObject.name}.")
                
                // Clear previous capture image while moving
                capturedImageUrl = null
                
                addLog("Starting slew to ${ngcObject.name}...")
                telescopeController.gotoCoordinates(seestarIp, ngcObject.ra, ngcObject.dec, {
                    addLog("Successfully slewed to ${ngcObject.name}.")
                }, { error ->
                    addLog("Slew error: $error")
                    speakAssistantResponse("Sorry, I couldn't move to ${ngcObject.name} due to an error.")
                })
            } else {
                val response = "I couldn't find $targetName in my database."
                addLog("Error: $response")
                speakAssistantResponse(response)
            }
        }
    }

    private fun pointAndCapture(targetName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ngcObject = db.ngcDao().getObjectByName(targetName)
                ?: db.ngcDao().searchObjects(targetName).firstOrNull()

            if (ngcObject != null) {
                addLog("Action: Pointing to ${ngcObject.name} and capturing image.")
                speakAssistantResponse("Pointing to ${ngcObject.name} and taking a photo.")
                
                // Clear previous capture image while moving/capturing
                capturedImageUrl = null
                
                telescopeController.slewAndCapture(seestarIp, ngcObject.ra, ngcObject.dec, { progress ->
                    addLog("Capture: $progress")
                }, { result, imageUrl ->
                    addLog(result)
                    
                    // Update state with the new image URL
                    viewModelScope.launch(Dispatchers.Main) {
                        capturedImageUrl = imageUrl
                        addLog("Retrieved image from: $imageUrl")
                        speakAssistantResponse("Image of ${ngcObject.name} captured and displayed.")
                    }
                }, { error ->
                    addLog("Error during capture: $error")
                    speakAssistantResponse("Sorry, I encountered an error while trying to capture ${ngcObject.name}.")
                })
            } else {
                addLog("Error: Could not find $targetName in catalog.")
                speakAssistantResponse("I couldn't find $targetName in my database.")
            }
        }
    }

    private fun queryObjectVisibility(intent: TelescopeIntent.VisibilityQuery) {
        viewModelScope.launch(Dispatchers.IO) {
            // Clean target of any lingering wake words just in case
            var target = intent.target
            val currentWakeWords = wakeWords
            currentWakeWords.forEach { word ->
                target = target.replace(Regex(word, RegexOption.IGNORE_CASE), "")
            }
            target = target.trim().replace(Regex("^[,.?! ]+"), "").trim()

            val ngcObject = db.ngcDao().getObjectByName(target)
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
                speakAssistantResponse(response)
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
            bortle = bortleScale,
            minAngle = minVisibilityAngle
        )
        
        val altAz = AstroUtils.calculateAltAz(ngcObject.ra, ngcObject.dec, lat, lon, requestedTime)
        val timePrefix = if (timeSpec != null) "At ${timeSpec.uppercase()}, " else ""
        
        val response = if (visibilityCheck.first) {
            "${timePrefix}$spokenName is ${visibilityCheck.second} in $locationName at an altitude of ${altAz.altitude.toInt()} degrees."
        } else {
            "${timePrefix}$spokenName is ${visibilityCheck.second} in $locationName."
        }
        
        addLog("Visibility: $response")
        speakAssistantResponse(response)
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
                bortle = bortleScale,
                minAngle = minVisibilityAngle
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
            speakAssistantResponse(response)
        } else {
            val response = "$spokenName will not be visible in $locationName in the next 24 hours (due to daylight or light pollution)."
            addLog("Predictive: $response")
            speakAssistantResponse(response)
        }
    }
}
