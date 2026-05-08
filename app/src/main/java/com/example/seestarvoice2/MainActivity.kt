package com.example.seestarvoice2

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.seestarvoice2.intelligence.TtsManager
import com.example.seestarvoice2.settings.SettingsManager
import com.example.seestarvoice2.ui.MainScreen
import com.example.seestarvoice2.ui.MainViewModel
import com.example.seestarvoice2.ui.theme.SeeStarVoice2Theme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    private lateinit var ttsManager: TtsManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mainViewModel: MainViewModel? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            // Permission granted
        }
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsManager = SettingsManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            fetchLocation()
        }

        ttsManager = TtsManager(this)

        setContent {
            SeeStarVoice2Theme {
                val viewModel: MainViewModel = viewModel {
                    MainViewModel(application, ttsManager, settingsManager)
                }
                mainViewModel = viewModel
                SeeStarVoiceApp(viewModel)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                location?.let {
                    mainViewModel?.updateCurrentLocation(it.latitude, it.longitude)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}

@Serializable
object MainRoute

@Composable
fun SeeStarVoiceApp(viewModel: MainViewModel) {
    val backStack = remember { mutableStateListOf<Any>(MainRoute) }
    
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
        entryProvider = { key: Any ->
            when (key) {
                is MainRoute -> NavEntry(key) { MainScreen(viewModel) }
                else -> NavEntry(Unit) { /* Error screen */ }
            }
        }
    )
}
