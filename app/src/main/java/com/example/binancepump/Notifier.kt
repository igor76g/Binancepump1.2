package com.example.binancepump

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.math.BigDecimal

object Notifier {
    private const val CHANNEL_SERVICE = "monitor_service"
    private const val CHANNEL_ALERTS_ID = "pump_alerts_v2"
    const val FOREGROUND_ID = 1

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Monitoring status", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS_ID, "Pump alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when a futures coin pumps"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Persistent notification required for the foreground service. */
    fun buildServiceNotification(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Binance Pump Alerts")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .build()

    @SuppressLint("MissingPermission")
    fun updateServiceNotification(context: Context, text: String) {
        if (!canNotify(context)) return
        NotificationManagerCompat.from(context)
            .notify(FOREGROUND_ID, buildServiceNotification(context, text))
    }

    /** The actual pump alert shown to the user, with the coin ticker in the title. */
    @SuppressLint("MissingPermission")
    fun sendPumpAlert(
        context: Context,
        symbol: String,
        changePercent: Double,
        price: Double,
        windowMinutes: Int
    ) {
        if (!canNotify(context)) return

        val priceText = BigDecimal.valueOf(price).stripTrailingZeros().toPlainString()
        val isPump = changePercent >= 0
        val title = "${if (isPump) "\uD83D\uDE80" else "\uD83D\uDD3B"} $symbol  ${"%+.2f".format(changePercent)}%"
        val verb = if (isPump) "rose" else "fell"
        val kind = if (isPump) "PUMP (long idea)" else "DUMP (short idea)"
        val text = "$kind: price $verb ${"%.2f".format(Math.abs(changePercent))}% in $windowMinutes min. Current price: $priceText"

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        // One notification per symbol and direction: a newer alert for the same coin replaces the old one.
        NotificationManagerCompat.from(context).notify((symbol + if (isPump) "|up" else "|down").hashCode(), notification)
    }
}
