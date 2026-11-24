package com.phyll.filmhub.subtitle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.phyll.filmhub.MainActivity
import com.phyll.filmhub.R

/**
 * #8 Resilient Foreground Service for Uninterrupted Background AI Subtitle Translation:
 * Keeps Full Episode Audio/SRT translation, Surgical Gap Repair, and Cinema AI Polish alive
 * when the phone screen turns off or the user switches apps, showing live % progress in the notification shade.
 */
class SubtitleTranslationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "ChillFilm AI Sub" }
                val message = intent?.getStringExtra(EXTRA_MESSAGE).orEmpty().ifBlank { "Đang dịch phụ đề AI ngầm..." }
                val percent = intent?.getIntExtra(EXTRA_PERCENT, 0)?.coerceIn(0, 100) ?: 0
                ensureChannel()
                val notif = buildNotification(title, message, percent)
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notif,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notif)
                    }
                }
                return START_NOT_STICKY
            }
        }
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "AI Subtitle Translation",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Giữ tiến trình dịch phụ đề AI chạy nền bền bỉ khi khóa màn hình"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(title: String, message: String, percent: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val contentPi = PendingIntent.getActivity(this, 101, openIntent, piFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPi)
            .setProgress(100, percent, percent <= 0)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "chillfilm_ai_sub_bg"
        private const val NOTIFICATION_ID = 4360
        private const val ACTION_UPDATE = "com.phyll.filmhub.action.AI_SUB_UPDATE"
        private const val ACTION_STOP = "com.phyll.filmhub.action.AI_SUB_STOP"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_PERCENT = "percent"

        fun updateProgress(
            context: Context,
            title: String,
            message: String,
            percent: Int,
            isActive: Boolean
        ) {
            runCatching {
                val appCtx = context.applicationContext
                if (!isActive) {
                    appCtx.stopService(Intent(appCtx, SubtitleTranslationService::class.java))
                    return
                }
                val intent = Intent(appCtx, SubtitleTranslationService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_MESSAGE, message)
                    putExtra(EXTRA_PERCENT, percent.coerceIn(0, 100))
                }
                ContextCompat.startForegroundService(appCtx, intent)
            }
        }
    }
}
