package com.example.seestarvoice2.intelligence

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

class TelescopeController(
    private val client: OkHttpClient = OkHttpClient(),
    var port: Int = 32323,
    var onLog: ((String) -> Unit)? = null,
    var onConnect: ((String) -> Unit)? = null
) {

    private var transactionCounter = kotlin.random.Random.nextInt(1, 1000)
    private val clientTransactionID: Int get() = ++transactionCounter

    private var randomClientID: Int = 0
    private var isCurrentlyConnected = false
    private var lastConnectionCheckTime = 0L
    private var isCameraConnected = false
    private var lastCameraCheckTime = 0L
    private val CONNECTION_CACHE_MS = 30000 // 30 seconds

    private val clientID: Int
        get() {
            if (randomClientID == 0) {
                // Alpaca ClientID should be a unique 32-bit integer. 
                // We'll use a random number in a safe range.
                randomClientID = kotlin.random.Random.nextInt(1, 65535)
            }
            return randomClientID
        }

    fun sendAlpacaRequest(
        ip: String,
        method: String,
        endpoint: String,
        params: Map<String, String>,
        onResponse: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "http://$ip:$port/api/v1/$endpoint"
        
        // Alpaca parameters for both Body and Query (for maximum compatibility)
        val allParams = params.toMutableMap().apply {
            put("ClientID", clientID.toString())
            put("ClientTransactionID", clientTransactionID.toString())
        }

        val httpUrlBuilder = url.toHttpUrlOrNull()?.newBuilder() ?: throw IOException("Invalid URL: $url")
        val requestBuilder = Request.Builder()

        if (method == "PUT") {
            val formBodyBuilder = FormBody.Builder()
            allParams.forEach { (key, value) -> 
                formBodyBuilder.add(key, value)
            }
            requestBuilder.url(httpUrlBuilder.build()).put(formBodyBuilder.build())
        } else {
            allParams.forEach { (key, value) -> 
                httpUrlBuilder.addQueryParameter(key, value)
            }
            requestBuilder.url(httpUrlBuilder.build()).get()
        }

        val request = requestBuilder.build()
        // Reduced log verbosity for common status checks
        val isQuietEndpoint = endpoint.contains("connected") || endpoint.contains("imageready")
        val logMsg = "Sending Alpaca $method to ${request.url} with $params"
        
        android.util.Log.d("TelescopeController", logMsg)
        if (!isQuietEndpoint) {
            onLog?.invoke(logMsg)
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errorMsg = "Network error connecting to SeeStar at $ip: ${e.message}"
                android.util.Log.e("TelescopeController", errorMsg)
                onLog?.invoke("ERROR: $errorMsg")
                isCurrentlyConnected = false
                isCameraConnected = false
                onError(errorMsg)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val logResp = "Response [${response.code}]: $body"
                android.util.Log.d("TelescopeController", logResp)
                if (!isQuietEndpoint) {
                    onLog?.invoke(logResp)
                }
                
                if (response.isSuccessful) {
                    try {
                        val json = org.json.JSONObject(body)
                        val errorNum = json.optInt("ErrorNumber", 0)
                        val errorMsg = json.optString("ErrorMessage", "")
                        
                        if (errorNum != 0) {
                            val fullError = "Alpaca Error $errorNum: $errorMsg"
                            android.util.Log.e("TelescopeController", fullError)
                            onLog?.invoke("ERROR: $fullError")
                            // 1025 or 0x400 (Invalid Value) often means state mismatch
                            if (errorNum == 0x400 || errorNum == 1025 || errorNum == 1031) {
                                if (endpoint.startsWith("camera")) isCameraConnected = false else isCurrentlyConnected = false
                            }
                            onError(fullError)
                        } else {
                            onResponse(body)
                        }
                    } catch (e: Exception) {
                        // If it's not valid JSON or missing error fields, treat HTTP 200 as success
                        onResponse(body)
                    }
                } else {
                    if (response.code == 400 || response.code == 401) isCurrentlyConnected = false
                    onError("SeeStar returned HTTP ${response.code}: $body")
                }
            }
        })
    }

    fun isConnected(ip: String, deviceType: String = "telescope", forceCheck: Boolean = false, onResult: (Boolean) -> Unit, onError: (String) -> Unit) {
        val now = System.currentTimeMillis()
        
        if (!forceCheck) {
            if (deviceType == "telescope" && isCurrentlyConnected && (now - lastConnectionCheckTime < CONNECTION_CACHE_MS)) {
                onResult(true)
                return
            }
            if (deviceType == "camera" && isCameraConnected && (now - lastCameraCheckTime < CONNECTION_CACHE_MS)) {
                onResult(true)
                return
            }
        }

        val logMsg = "Checking if $deviceType is connected..."
        android.util.Log.d("TelescopeController", logMsg)
        onLog?.invoke(logMsg)
        
        sendAlpacaRequest(ip, "GET", "$deviceType/0/connected", emptyMap(), { response ->
            try {
                val value = org.json.JSONObject(response).getBoolean("Value")
                if (deviceType == "telescope") {
                    isCurrentlyConnected = value
                    lastConnectionCheckTime = System.currentTimeMillis()
                } else {
                    isCameraConnected = value
                    lastCameraCheckTime = System.currentTimeMillis()
                }
                onResult(value)
            } catch (e: Exception) {
                if (deviceType == "telescope") isCurrentlyConnected = false else isCameraConnected = false
                onError("Failed to parse $deviceType connection status: ${e.message}")
            }
        }, {
            if (deviceType == "telescope") isCurrentlyConnected = false else isCameraConnected = false
            onError(it)
        })
    }

    fun connect(ip: String, deviceType: String = "telescope", onComplete: () -> Unit, onError: (String) -> Unit) {
        val logMsg = "Establishing connection to $deviceType..."
        android.util.Log.d("TelescopeController", logMsg)
        onLog?.invoke(logMsg)

        sendAlpacaRequest(ip, "PUT", "$deviceType/0/connected", mapOf("Connected" to "True"), {
            if (deviceType == "telescope") {
                isCurrentlyConnected = true
                lastConnectionCheckTime = System.currentTimeMillis()
            } else {
                isCameraConnected = true
                lastCameraCheckTime = System.currentTimeMillis()
            }
            onComplete() 
        }, {
            if (deviceType == "telescope") isCurrentlyConnected = false else isCameraConnected = false
            onError(it)
        })
    }

    fun getTelescopeName(ip: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        sendAlpacaRequest(ip, "GET", "telescope/0/name", emptyMap(), { response ->
            try {
                val name = org.json.JSONObject(response).getString("Value")
                onResult(name)
            } catch (e: Exception) {
                onError("Failed to parse name: ${e.message}")
            }
        }, onError)
    }

    fun sendCommand(ip: String, endpoint: String, method: String = "PUT", params: Map<String, String>, onResponse: (String) -> Unit, onError: (String) -> Unit) {
        val startLog = "Command: $endpoint ($method)"
        android.util.Log.d("TelescopeController", startLog)
        onLog?.invoke(startLog)

        // Avoid auto-connecting for the connection check itself
        if (endpoint.contains("connected")) {
            sendAlpacaRequest(ip, method, endpoint, params, onResponse, onError)
            return
        }

        // Determine device type (telescope vs camera)
        val deviceType = if (endpoint.startsWith("camera")) "camera" else "telescope"
        val connectionEndpoint = "$deviceType/0/connected"

        isConnected(ip, deviceType = deviceType, forceCheck = false, onResult = { connected ->
            if (connected) {
                sendAlpacaRequest(ip, method, endpoint, params, onResponse, onError)
            } else {
                val logMsg = "$deviceType not connected. Attempting auto-connect..."
                android.util.Log.d("TelescopeController", logMsg)
                onLog?.invoke(logMsg)
                
                // Connect the specific device
                sendAlpacaRequest(ip, "PUT", connectionEndpoint, mapOf("Connected" to "True"), {
                    if (deviceType == "telescope") {
                        isCurrentlyConnected = true
                        lastConnectionCheckTime = System.currentTimeMillis()
                        // Fetch name upon successful connection for the telescope
                        getTelescopeName(ip, { name ->
                            onConnect?.invoke(name)
                            sendAlpacaRequest(ip, method, endpoint, params, onResponse, onError)
                        }, {
                            onConnect?.invoke("SeeStar")
                            sendAlpacaRequest(ip, method, endpoint, params, onResponse, onError)
                        })
                    } else {
                        isCameraConnected = true
                        lastCameraCheckTime = System.currentTimeMillis()
                        sendAlpacaRequest(ip, method, endpoint, params, onResponse, onError)
                    }
                }, { error ->
                    onError("Auto-connect for $deviceType failed: $error")
                })
            }
        }, onError = { error ->
            // If we can't check connection, try the command anyway
            sendAlpacaRequest(ip, method, endpoint, params, onResponse, onError)
        })
    }

    fun openArm(ip: String, ra: Double, dec: Double, onComplete: () -> Unit, onError: (String) -> Unit) {
        // Step 1: Unpark the telescope
        sendCommand(ip, "telescope/0/unpark", "PUT", emptyMap(), {
            // Step 2: Slew to Zenith (or safe coordinates) to physically open the arm.
            // Using Equatorial slew since AltAz slew is not implemented in some drivers.
            val slewParams = mapOf(
                "RightAscension" to (ra / 15.0).toString(),
                "Declination" to dec.toString()
            )
            sendCommand(ip, "telescope/0/slewtocoordinates", "PUT", slewParams, {
                // Step 3: Enable tracking now that we are above the horizon
                sendCommand(ip, "telescope/0/tracking", "PUT", mapOf("Tracking" to "True"), {
                    onComplete()
                }, {
                    // Log but consider success since the arm is now physically open
                    android.util.Log.w("TelescopeController", "Arm opened via RA/Dec but tracking failed: $it")
                    onComplete()
                })
            }, onError)
        }, onError)
    }

    fun closeArm(ip: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        // Step 1: Explicitly disable tracking. 
        // Parking often fails if tracking is still engaged or if the driver tries 
        // to re-enable it during the park sequence while near the horizon.
        sendCommand(ip, "telescope/0/tracking", "PUT", mapOf("Tracking" to "False"), {
            // Step 2: Send the park command to physically close the arm
            sendCommand(ip, "telescope/0/park", "PUT", emptyMap(), { 
                onComplete() 
            }, onError)
        }, { error ->
            // If tracking disable fails, try to park anyway (it might already be off)
            android.util.Log.w("TelescopeController", "Failed to disable tracking before park: $error")
            sendCommand(ip, "telescope/0/park", "PUT", emptyMap(), { onComplete() }, onError)
        })
    }

    fun powerDown(ip: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        // Power down isn't standard Alpaca, but we'll try a common community endpoint or just log it
        onError("Power down command sent (Experimental).")
    }

    fun gotoCoordinates(ip: String, ra: Double, dec: Double, onComplete: () -> Unit, onError: (String) -> Unit) {
        val slewParams = mapOf(
            "RightAscension" to (ra / 15.0).toString(),
            "Declination" to dec.toString()
        )
        sendCommand(ip, "telescope/0/slewtocoordinates", "PUT", slewParams, { onComplete() }, onError)
    }

    fun slewAndCapture(ip: String, ra: Double, dec: Double, onProgress: (String) -> Unit, onComplete: (String, String) -> Unit, onError: (String) -> Unit) {
        val slewParams = mapOf(
            "RightAscension" to (ra / 15.0).toString(),
            "Declination" to dec.toString()
        )
        
        onProgress("Slewing to target coordinates...")
        sendCommand(ip, "telescope/0/slewtocoordinates", "PUT", slewParams, {
            onProgress("Target reached. Starting exposure...")
            val captureParams = mapOf("Duration" to "5", "Light" to "True")
            sendCommand(ip, "camera/0/startexposure", "PUT", captureParams, {
                onProgress("Exposure started. Waiting for image...")
                // Poll for image completion
                pollForImage(ip, onProgress, { imageUrl ->
                    onComplete("Capture completed.", imageUrl)
                }, onError)
            }, onError)
        }, onError)
    }

    fun quickCapture(ip: String, onProgress: (String) -> Unit, onComplete: (String, String) -> Unit, onError: (String) -> Unit) {
        onProgress("Starting quick exposure...")
        val captureParams = mapOf("Duration" to "1", "Light" to "True")
        sendCommand(ip, "camera/0/startexposure", "PUT", captureParams, {
            onProgress("Exposure started. Waiting for image...")
            pollForImage(ip, onProgress, { imageUrl ->
                onComplete("Quick capture completed.", imageUrl)
            }, onError)
        }, onError)
    }

    fun captureOnly(ip: String, onProgress: (String) -> Unit, onComplete: (String, String) -> Unit, onError: (String) -> Unit) {
        onProgress("Starting standard exposure...")
        val captureParams = mapOf("Duration" to "5", "Light" to "True")
        sendCommand(ip, "camera/0/startexposure", "PUT", captureParams, {
            onProgress("Exposure started. Waiting for image...")
            pollForImage(ip, onProgress, { imageUrl ->
                onComplete("Capture completed.", imageUrl)
            }, onError)
        }, onError)
    }

    private fun pollForImage(ip: String, onProgress: (String) -> Unit, onReady: (String) -> Unit, onError: (String) -> Unit) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val checkInterval = 2000L // 2 seconds
        val maxAttempts = 30 // 60 seconds total
        var attempts = 0

        val runnable = object : Runnable {
            override fun run() {
                attempts++
                onProgress("Checking image status (Attempt $attempts/$maxAttempts)...")
                sendCommand(ip, "camera/0/imageready", "GET", emptyMap(), { response ->
                    try {
                        val json = org.json.JSONObject(response)
                        val ready = json.optBoolean("Value", false)
                        
                        if (ready) {
                            onProgress("Image is ready! Retrieving...")
                            // Try common SeeStar Alpaca image endpoints
                            val timestamp = System.currentTimeMillis()
                            // Endpoint 1: Standard preview image
                            val imageUrl = "http://$ip:$port/api/v1/camera/0/image?t=$timestamp"
                            onReady(imageUrl)
                        } else if (attempts < maxAttempts) {
                            handler.postDelayed(this, checkInterval)
                        } else {
                            val timeoutMsg = "Exposure timed out after ${attempts * checkInterval / 1000} seconds."
                            android.util.Log.e("TelescopeController", timeoutMsg)
                            onError(timeoutMsg)
                        }
                    } catch (e: Exception) {
                        onError("Error parsing camera status: ${e.message}")
                    }
                }, { error ->
                    onError(error)
                })
            }
        }
        handler.post(runnable)
    }
}
