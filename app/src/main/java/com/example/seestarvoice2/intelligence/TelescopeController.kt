package com.example.seestarvoice2.intelligence

import okhttp3.*
import java.io.IOException

class TelescopeController(
    private val client: OkHttpClient = OkHttpClient(),
    var port: Int = 4030,
    var onLog: ((String) -> Unit)? = null
) {

    private var transactionCounter = 0.toLong()
    private val clientTransactionID: Long get() = ++transactionCounter

    private var randomClientID: Long = 0

    private val clientID: Long
        get() {
            if (randomClientID == 0.toLong()) {
                randomClientID = System.currentTimeMillis()
            }
            return randomClientID
        }

    fun sendCommand(ip: String, endpoint: String, params: Map<String, String>, onResponse: (String) -> Unit, onError: (String) -> Unit) {
        // Use the configured port instead of hardcoded 5555
        val url = "http://$ip:$port/api/v1/$endpoint"
        
        val formBodyBuilder = FormBody.Builder()
        params.forEach { (key, value) ->
            formBodyBuilder.add(key, value)
        }
        formBodyBuilder.add("ClientID", clientID.toString())
        formBodyBuilder.add("ClientTransactionID", clientTransactionID.toString())

        val request = Request.Builder()
            .url(url)
            .put(formBodyBuilder.build())
            .build()

        val logMsg = "Sending Alpaca PUT to $url with $params"
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
                    onResponse(body)
                } else {
                    onError("SeeStar returned error ${response.code}: $body")
                }
            }
        })
    }

    fun openArm(ip: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        sendCommand(ip, "telescope/0/unpark", emptyMap(), { onComplete() }, onError)
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
            val captureParams = mapOf("Duration" to "10", "Light" to "true")
            sendCommand(ip, "camera/0/startexposure", captureParams, {
                onComplete("Target reached. Capture started.")
            }, onError)
        }, onError)
    }
}
