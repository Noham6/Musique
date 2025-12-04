package com.example.musique

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class MusicService : Service() {

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_PREVIOUS = "ACTION_PREVIOUS"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_UPDATE = "ACTION_UPDATE"

        const val CHANNEL_ID = "music_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                MusicPlayerManager.resumeMusic()
                showNotification(isPlaying = true)
            }
            ACTION_PAUSE -> {
                MusicPlayerManager.pauseMusic()
                showNotification(isPlaying = false)
            }
            ACTION_NEXT -> {
                MusicPlayerManager.playNextMusic(this) { error ->
                    // Gérer l'erreur si nécessaire
                }
                showNotification(isPlaying = true)
            }
            ACTION_PREVIOUS -> {
                MusicPlayerManager.playPreviousMusic(this) { error ->
                    // Gérer l'erreur si nécessaire
                }
                showNotification(isPlaying = true)
            }
            ACTION_STOP -> {
                MusicPlayerManager.stopMusic()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE -> {
                // Juste mettre à jour la notification
                showNotification(MusicPlayerManager.isPlaying())
            }
        }
        return START_STICKY
    }

    private fun showNotification(isPlaying: Boolean) {
        val currentMusic = MusicPlayerManager.getCurrentMusic()

        if (currentMusic == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // Intent pour ouvrir l'app
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent pour Play/Pause
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPauseIntent = Intent(this, MusicService::class.java).setAction(playPauseAction)
        val playPausePendingIntent = PendingIntent.getService(
            this, 0, playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent pour Next
        val nextIntent = Intent(this, MusicService::class.java).setAction(ACTION_NEXT)
        val nextPendingIntent = PendingIntent.getService(
            this, 1, nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent pour Previous
        val previousIntent = Intent(this, MusicService::class.java).setAction(ACTION_PREVIOUS)
        val previousPendingIntent = PendingIntent.getService(
            this, 2, previousIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Construire la notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentMusic.title)
            .setContentText(currentMusic.artist)
            .setSmallIcon(android.R.drawable.ic_media_play) // Icône temporaire
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(createStopIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            // Boutons
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                previousPendingIntent
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                nextPendingIntent
            )
            // Style média
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createStopIntent(): PendingIntent {
        val stopIntent = Intent(this, MusicService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lecture de musique",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Contrôles de lecture"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}