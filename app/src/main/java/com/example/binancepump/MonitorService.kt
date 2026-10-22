package com.example.binancepump

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that continuously monitors all Binance USDT-M futures:
 *   1. keeps a list of symbols whose 24h quote volume >= minimum volume,
 *   2. polls prices and tracks them over a rolling window,
 *   3. sends a phone notification when growth within the window >= threshold.
 *
 * Settings are re-read on every loop iteration, so changes saved in the app apply
 * without restarting the service.
 */
class MonitorService : Service() {

    companion object {
        private const val TAG = "MonitorService"

        /** Human-readable status shown in the app UI. */
        val status = MutableStateFlow("Stopped")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /** Keep the CPU and Wi-Fi awake while the screen is off, otherwise Doze pauses monitoring. */
    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BinancePump:monitor")
                .apply { setReferenceCounted(false); acquire() }
        }
        if (wifiLock?.isHeld != true) {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BinancePump:wifi")
                .apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseLocks() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        try { wifiLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        wakeLock = null
        wifiLock = null
    }

    private val tracker = PriceTracker()
    private var allSymbols: Set<String> = emptySet()
    private var qualifying: Set<String> = emptySet()
    private var lastVolumeRefreshMs = 0L
    private val lastAlertMs = HashMap<String, Long>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            Notifier.FOREGROUND_ID,
            Notifier.buildServiceNotification(this, "Starting..."),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        acquireLocks()
        publishStatus("Starting...")

        if (job?.isActive != true) {
            job = scope.launch {
                while (isActive) {
                    val settings = SettingsStore.load(applicationContext)
                    try {
                        tick(settings)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Check failed", e)
                        publishStatus("Error: ${e.message ?: e.javaClass.simpleName} (retrying)")
                    }
                    delay(settings.checkIntervalSec.coerceAtLeast(1) * 1000L)
                }
            }
        }
        return START_STICKY
    }

    /** One monitoring iteration (blocking network calls, runs on Dispatchers.IO). */
    private fun tick(settings: Settings) {
        val nowMs = System.currentTimeMillis()

        // 1) Periodically refresh which symbols pass the 24h volume filter
        val refreshMs = settings.volumeRefreshSec.coerceAtLeast(1) * 1000L
        if (qualifying.isEmpty() || nowMs - lastVolumeRefreshMs >= refreshMs) {
            allSymbols = BinanceApi.fetchUsdtPerpetualSymbols()
            val volumes = BinanceApi.fetchQuoteVolumes()
            qualifying = allSymbols
                .filter { (volumes[it] ?: 0.0) >= settings.minVolumeUsdt }
                .toSet()
            tracker.retainOnly(qualifying)
            lastVolumeRefreshMs = nowMs
        }

        // 2) Poll prices and detect pumps (growth) and dumps (drop, mirror mode)
        val prices = BinanceApi.fetchPrices()
        val windowMs = settings.windowMinutes.coerceAtLeast(1) * 60_000L
        val cooldownMs = settings.cooldownMinutes.coerceAtLeast(0) * 60_000L

        for (symbol in qualifying) {
            val price = prices[symbol] ?: continue
            tracker.add(symbol, price, nowMs, windowMs)
            val change = tracker.changePercent(symbol) ?: continue

            val direction = when {
                change >= settings.priceChangePercent && settings.alertDirection != "down" -> "up"
                change <= -settings.priceChangePercent && settings.alertDirection != "up" -> "down"
                else -> continue
            }
            val key = "$symbol|$direction"
            val last = lastAlertMs[key]
            if (last != null && nowMs - last < cooldownMs) continue

            Log.i(TAG, "ALERT $symbol ${"%+.2f".format(change)}%")
            Notifier.sendPumpAlert(this, symbol, change, price, settings.windowMinutes)
            lastAlertMs[key] = nowMs
        }

        publishStatus(
            "Watching ${qualifying.size} coins (24h volume >= ${settings.minVolumeMillionUsdt} M USDT). " +
                "Last check: ${timeFormat.format(Date())}"
        )
    }

    private fun publishStatus(text: String) {
        status.value = text
        Notifier.updateServiceNotification(this, text)
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        releaseLocks()
        status.value = "Stopped"
        super.onDestroy()
    }
}
