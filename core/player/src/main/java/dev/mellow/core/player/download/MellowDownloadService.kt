package dev.mellow.core.player.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mellow.core.player.R

class MellowDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServiceEntryPoint {
        fun mellowDownloadManager(): MellowDownloadManager
    }

    private val mellowDownloadManager: MellowDownloadManager by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceEntryPoint::class.java,
        ).mellowDownloadManager()
    }

    override fun getDownloadManager(): DownloadManager = mellowDownloadManager.downloadManager

    override fun getScheduler(): androidx.media3.exoplayer.scheduler.Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        ensureNotificationChannel()

        val activeCount = downloads.count { it.state == Download.STATE_DOWNLOADING }
        val title = if (activeCount > 0) {
            "Downloading $activeCount track${if (activeCount != 1) "s" else ""}"
        } else {
            "Downloads"
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.download_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "mellow_downloads"
    }
}
