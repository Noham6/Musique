package com.example.musique

import android.content.Context
import android.media.MediaPlayer
import java.io.IOException

object MusicPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var currentMusic: Music? = null
    private var isPlaying = false
    private var isShuffleMode = false

    // Playlist et callback pour la fin de lecture
    private var playlist: List<Music> = emptyList()
    private var currentIndex: Int = -1
    private var onMusicComplete: (() -> Unit)? = null

    fun setPlaylist(musicList: List<Music>) {
        playlist = musicList
    }

    fun setShuffleMode(enabled: Boolean) {
        isShuffleMode = enabled
    }

    fun isShuffleMode(): Boolean = isShuffleMode

    fun setOnMusicCompleteListener(listener: () -> Unit) {
        onMusicComplete = listener
    }

    fun playMusic(context: Context, music: Music, onError: (String) -> Unit) {
        try {
            // Arrêter la musique précédente si elle joue
            stopMusic()

            // Trouver l'index de la musique dans la playlist
            currentIndex = playlist.indexOf(music)
            if (currentIndex == -1) {
                // Si la musique n'est pas dans la playlist, on l'ajoute
                currentIndex = 0
            }

            // Créer un nouveau MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(music.filePath)
                prepare()
                start()
            }

            currentMusic = music
            isPlaying = true

            // Listener pour lancer la musique suivante automatiquement
            mediaPlayer?.setOnCompletionListener {
                playNextMusic(context, onError)
            }

        } catch (e: IOException) {
            onError("Erreur de lecture: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            onError("Erreur: ${e.message}")
            e.printStackTrace()
        }
    }

    fun playNextMusic(context: Context, onError: (String) -> Unit) {
        if (playlist.isEmpty()) return

        val nextIndex = if (isShuffleMode) {
            // Mode aléatoire : choisir une musique au hasard
            playlist.indices.random()
        } else {
            // Mode séquentiel : musique suivante
            if (currentIndex + 1 < playlist.size) {
                currentIndex + 1
            } else {
                0 // Retour au début
            }
        }

        val nextMusic = playlist[nextIndex]
        playMusic(context, nextMusic, onError)

        // Notifier l'UI et mettre à jour la notification
        onMusicComplete?.invoke()
        updateNotification(context)
    }

    fun playPreviousMusic(context: Context, onError: (String) -> Unit) {
        if (playlist.isEmpty()) return

        val prevIndex = if (isShuffleMode) {
            // Mode aléatoire : choisir une musique au hasard
            playlist.indices.random()
        } else {
            // Mode séquentiel : musique précédente
            if (currentIndex - 1 >= 0) {
                currentIndex - 1
            } else {
                playlist.size - 1 // Aller à la fin
            }
        }

        val prevMusic = playlist[prevIndex]
        playMusic(context, prevMusic, onError)

        // Notifier l'UI et mettre à jour la notification
        onMusicComplete?.invoke()
        updateNotification(context)
    }

    private fun updateNotification(context: Context) {
        // Envoyer une intention pour mettre à jour la notification
        val updateIntent = android.content.Intent(context, com.example.musique.MusicService::class.java)
        updateIntent.action = "ACTION_UPDATE"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(updateIntent)
        } else {
            context.startService(updateIntent)
        }
    }

    fun pauseMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
            }
        }
    }

    fun resumeMusic() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                isPlaying = true
            }
        }
    }

    fun stopMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        isPlaying = false
    }

    fun isPlaying(): Boolean = isPlaying

    fun getCurrentMusic(): Music? = currentMusic

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun getCurrentIndex(): Int = currentIndex
    fun getAudioSessionId(): Int {
        return mediaPlayer?.audioSessionId ?: 0
    }
}