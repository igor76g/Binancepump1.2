package com.example.binancepump

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.binancepump.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // label shown in the spinner -> value stored in settings
    private val directions = listOf(
        "Growth (+)" to "up",
        "Drop (-), mirror mode for shorts" to "down",
        "Growth and drop" to "both"
    )

    private lateinit var binding: ActivityMainBinding

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                toast("Notifications are blocked - you will not see alerts. Enable them in system settings.")
            }
            startMonitoring()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.directionSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, directions.map { it.first }
        )
        showSettings(SettingsStore.load(this))

        binding.saveButton.setOnClickListener { readAndSaveSettings() }
        binding.startButton.setOnClickListener {
            if (readAndSaveSettings() != null) ensurePermissionThenStart()
        }
        binding.stopButton.setOnClickListener {
            stopService(Intent(this, MonitorService::class.java))
        }

        // Live status from the service
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MonitorService.status.collect { binding.statusText.text = it }
            }
        }
    }

    private fun showSettings(s: Settings) {
        binding.volumeInput.setText(s.minVolumeMillionUsdt.toString())
        binding.volumeRefreshInput.setText(s.volumeRefreshSec.toString())
        binding.priceChangeInput.setText(s.priceChangePercent.toString())
        binding.directionSpinner.setSelection(directions.indexOfFirst { it.second == s.alertDirection }.coerceAtLeast(0))
        binding.windowInput.setText(s.windowMinutes.toString())
        binding.checkIntervalInput.setText(s.checkIntervalSec.toString())
        binding.cooldownInput.setText(s.cooldownMinutes.toString())
    }

    /** Validates the form and stores the settings. Returns null if some field is invalid. */
    private fun readAndSaveSettings(): Settings? {
        val volume = binding.volumeInput.text.toString().trim().toDoubleOrNull()
        val volumeRefresh = binding.volumeRefreshInput.text.toString().trim().toIntOrNull()
        val priceChange = binding.priceChangeInput.text.toString().trim().toDoubleOrNull()
        val window = binding.windowInput.text.toString().trim().toIntOrNull()
        val checkInterval = binding.checkIntervalInput.text.toString().trim().toIntOrNull()
        val cooldown = binding.cooldownInput.text.toString().trim().toIntOrNull()

        if (volume == null || volume < 0 ||
            volumeRefresh == null || volumeRefresh <= 0 ||
            priceChange == null || priceChange <= 0 ||
            window == null || window <= 0 ||
            checkInterval == null || checkInterval <= 0 ||
            cooldown == null || cooldown < 0
        ) {
            toast("Please fill in all fields with valid numbers")
            return null
        }

        val settings = Settings(
            minVolumeMillionUsdt = volume,
            volumeRefreshSec = volumeRefresh,
            alertDirection = directions[binding.directionSpinner.selectedItemPosition].second,
            priceChangePercent = priceChange,
            windowMinutes = window,
            checkIntervalSec = checkInterval,
            cooldownMinutes = cooldown
        )
        SettingsStore.save(this, settings)
        toast("Settings saved")
        return settings
    }

    private fun ensurePermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMonitoring()
        }
    }

    /** Without this exemption Android (Doze / battery saver) stops monitoring when the screen is off. */
    private fun requestBatteryExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                toast("Open Settings > Apps > Binance Pump > Battery > Unrestricted")
            }
        }
    }

    private fun startMonitoring() {
        requestBatteryExemption()
        ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
