package com.phyll.filmhub.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.phyll.filmhub.R

/**
 * Background Media3 DownloadService enabling Netflix-grade HLS (.m3u8) & MP4 offline downloads
 * with live Android progress notifications even while screen is locked or app is backgrounded.
 */
@OptIn(UnstableApi::class)
class OfflineDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    OfflineDownloadManager.NOTIFICATION_CHANNEL_ID,
    R.string.app_name,
    0
) {

    override fun onCreate() {
        ensureNotificationChannel()
        super.onCreate()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(OfflineDownloadManager.NOTIFICATION_CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    OfflineDownloadManager.NOTIFICATION_CHANNEL_ID,
                    "ChillFilm Tải phim Offline",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Hiển thị tiến trình tải phim HLS & phụ đề AI để xem ngoại tuyến"
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    override fun getDownloadManager(): DownloadManager = OfflineDownloadManager.getDownloadManager(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val helper = OfflineDownloadManager.getNotificationHelper(this)
        return helper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            "ChillFilm • Đang tải phim xem Offline",
            downloads,
            notMetRequirements
        )
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 3501
    }
}
