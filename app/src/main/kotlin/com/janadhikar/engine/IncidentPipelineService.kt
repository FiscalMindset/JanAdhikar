package com.janadhikar.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.janadhikar.JanadhikarApp
import com.janadhikar.MainActivity
import com.janadhikar.R

/**
 * Foreground service that keeps the mic + inference pipeline alive if the user
 * locks the screen mid-incident. Started when capture begins, stopped when the
 * engine returns to Idle or reaches Resolution.
 *
 * The notification is deliberately terse and neutral — the phone may be
 * visible to a hostile party.
 */
class IncidentPipelineService : Service() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW, // silent — no sound, no vibration
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        // Defence in depth: even though the caller only starts this for a
        // permission-granted mic session, a SecurityException here must never
        // crash the app — stop cleanly instead.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        }
        // If the process was killed and restarted, there is no incident to
        // resume (audio is never persisted) — do not restart with stale intent.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Belt-and-braces: never leave the mic pipeline running without its
        // foreground service.
        (application as JanadhikarApp).edgeStack?.engine?.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.active_listening))
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "incident_pipeline"
        private const val NOTIFICATION_ID = 1
    }
}
