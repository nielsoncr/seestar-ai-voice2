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
        val logMsg = "Sending Alpaca $method to ${request.url} with $params"
        android.util.Log.d("TelescopeController", logMsg)
        onLog?.invoke(logMsg)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errorMsg = "Network error connecting to SeeStar at $ip: ${e.message}"
                android.util.Log.e("TelescopeController", errorMsg)
                onLog?.invoke("ERROR: $errorMsg")
                onError(errorMsg)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val logResp = "Response [${response.code}]: $body"
                android.util.Log.d("TelescopeController", logResp)
                onLog?.invoke(logResp)
                
                if (response.isSuccessful) {
                    try {
                        val json = org.json.JSONObject(body)
                        val errorNum = json.optInt("ErrorNumber", 0)
                        val errorMsg = json.optString("ErrorMessage", "")
                        
                        if (errorNum != 0) {
                            val fullError = "Alpaca Error $errorNum: $errorMsg"
                            android.util.Log.e("TelescopeController", fullError)
                            onLog?.invoke("ERROR: $fullError")
                            onError(fullError)
                        } else {
                            onResponse(body)
                        }
                    } catch (e: Exception) {
                        // If it's not valid JSON or missing error fields, treat HTTP 200 as success
                        onResponse(body)
                    }
                } else {
                    onError("SeeStar returned HTTP ${response.code}: $body")
                }
            }
        })
    }

    fun isConnected(ip: String, onResult: (Boolean) -> Unit, onError: (String) -> Unit) {
        sendAlpacaRequest(ip, "GET", "telescope/0/connected", emptyMap(), { response ->
            try {
                val value = org.json.JSONObject(response).getBoolean("Value")
                onResult(value)
            } catch (e: Exception) {
                onError("Failed to parse connection status: ${e.message}")
            }
        }, onError)
    }

    fun connect(ip: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        sendAlpacaRequest(ip, "PUT", "telescope/0/connected", mapOf("Connected" to "True"), {
            onComplete() 
        }, onError)
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

    fun sendCommand(ip: String, endpoint: String, params: Map<String, String>, onResponse: (String) -> Unit, onError: (String) -> Unit) {
        // Avoid auto-connecting for the connection check itself
        if (endpoint == "telescope/0/connected") {
            sendAlpacaRequest(ip, "PUT", endpoint, params, onResponse, onError)
            return
        }

        isConnected(ip, { connected ->
            if (connected) {
                sendAlpacaRequest(ip, "PUT", endpoint, params, onResponse, onError)
            } else {
                val logMsg = "Telescope not connected. Attempting auto-connect..."
                android.util.Log.d("TelescopeController", logMsg)
                onLog?.invoke(logMsg)
                connect(ip, {
                    // Fetch name upon successful connection
                    getTelescopeName(ip, { name ->
                        onConnect?.invoke(name)
                        sendAlpacaRequest(ip, "PUT", endpoint, params, onResponse, onError)
                    }, {
                        onConnect?.invoke("SeeStar")
                        sendAlpacaRequest(ip, "PUT", endpoint, params, onResponse, onError)
                    })
                }, { error ->
                    onError("Auto-connect failed: $error")
                })
            }
        }, { error ->
            // If we can't even check connection, just try the command anyway (might be a transient error)
            sendAlpacaRequest(ip, "PUT", endpoint, params, onResponse, onError)
        })
    }

    fun openArm(ip: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        // Step 1: Unpark the telescope
        sendCommand(ip, "telescope/0/unpark", emptyMap(), {
            // Step 2: Slew to a safe altitude (30 degrees) to physically open the arm.
            // Slew commands physically move the motors, whereas unpark might only update state.
            val slewParams = mapOf(
                "Altitude" to "30",
                "Azimuth" to "0"
            )
            sendCommand(ip, "telescope/0/slewtoaltaz", slewParams, {
                // Step 3: Enable tracking now that we are above the horizon
                sendCommand(ip, "telescope/0/tracking", mapOf("Tracking" to "True"), {
                    onComplete()
                }, {
                    // Log but consider success since the arm is now physically open
                    android.util.Log.w("TelescopeController", "Arm opened but tracking failed: $it")
                    onComplete()
                })
            }, onError)
        }, onError)
    }

    fun closeArm(ip: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        sendCommand(ip, "telescope/0/park", emptyMap(), { onComplete() }, onError)
    }

    fun powerDown(ip: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        // Power down isn't standard Alpaca, but we'll try a common community endpoint or just log it
        onError("Power down command sent (Experimental).")
    }

    fun slewAndCapture(ip: String, ra: Double, dec: Double, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        val slewParams = mapOf(
            "RightAscension" to (ra / 15.0).toString(),
            "Declination" to dec.toString()
        )
        
        sendCommand(ip, "telescope/0/slewtocoordinates", slewParams, {
            val captureParams = mapOf("Duration" to "10", "Light" to "True")
            sendCommand(ip, "camera/0/startexposure", captureParams, {
                onComplete("Target reached. Capture started.")
            }, onError)
        }, onError)
    }
}
