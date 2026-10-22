package com.example.binancepump

import android.content.Context

/**
 * All user-editable settings in one place.
 * Defaults match the requested conditions: 24h volume > 30M USDT, price growth > 4% in 5 minutes.
 */
data class Settings(
    // --- Volume filter ---
    val minVolumeMillionUsdt: Double = 30.0,   // minimum 24h quote volume, in millions of USDT
    val volumeRefreshSec: Int = 60,            // how often to refresh the list of qualifying symbols

    // --- Price move filter ---
    val alertDirection: String = "both",       // "up" = growth, "down" = drop (mirror mode for shorts), "both"
    val priceChangePercent: Double = 4.0,      // minimum price move within the window, in %
    val windowMinutes: Int = 5,                // time window used to measure price growth

    // --- General ---
    val checkIntervalSec: Int = 15,            // how often to poll current prices
    val cooldownMinutes: Int = 15              // don't re-alert the same symbol within this time
) {
    val minVolumeUsdt: Double get() = minVolumeMillionUsdt * 1_000_000.0
}

object SettingsStore {
    private const val PREFS = "binance_pump_settings"

    fun load(context: Context): Settings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = Settings()
        return Settings(
            minVolumeMillionUsdt = prefs.getString("minVolumeMillionUsdt", null)?.toDoubleOrNull() ?: d.minVolumeMillionUsdt,
            volumeRefreshSec = prefs.getInt("volumeRefreshSec", d.volumeRefreshSec),
            alertDirection = prefs.getString("alertDirection", null)?.takeIf { it in listOf("up", "down", "both") } ?: d.alertDirection,
            priceChangePercent = prefs.getString("priceChangePercent", null)?.toDoubleOrNull() ?: d.priceChangePercent,
            windowMinutes = prefs.getInt("windowMinutes", d.windowMinutes),
            checkIntervalSec = prefs.getInt("checkIntervalSec", d.checkIntervalSec),
            cooldownMinutes = prefs.getInt("cooldownMinutes", d.cooldownMinutes)
        )
    }

    fun save(context: Context, s: Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("minVolumeMillionUsdt", s.minVolumeMillionUsdt.toString())
            .putInt("volumeRefreshSec", s.volumeRefreshSec)
            .putString("alertDirection", s.alertDirection)
            .putString("priceChangePercent", s.priceChangePercent.toString())
            .putInt("windowMinutes", s.windowMinutes)
            .putInt("checkIntervalSec", s.checkIntervalSec)
            .putInt("cooldownMinutes", s.cooldownMinutes)
            .apply()
    }
}
