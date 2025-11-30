package com.example.musique

import android.content.Context
import android.media.MediaPlayer
import java.io.IOException

object MusicPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var currentMusic: Music? = null
    private var isPlaying = false

    fun playMusic(context: Context, music: Music, onError: (String) -> Unit) {
        try {
            // Arrêter la musique précédente si elle joue
            stopMusic()

            // Créer un nouveau MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(music.filePath)
                prepare()
                start()
            }

            currentMusic = music
            isPlaying = true

            // Listener pour savoir quand la musique se termine
            mediaPlayer?.setOnCompletionListener {
                stopMusic()
            }

        } catch (e: IOException) {
            onError("Erreur de lecture: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            onError("Erreur: ${e.message}")
            e.printStackTrace()
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
        currentMusic = null
        isPlaying = false
    }

    fun isPlaying(): Boolean = isPlaying

    fun getCurrentMusic(): Music? = currentMusic

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }
}