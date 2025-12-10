package com.example.musique

import android.Manifest
import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var musicAdapter: MusicAdapter
    private lateinit var musicList: MutableList<Music>
    private lateinit var filteredList: MutableList<Music>
    private lateinit var searchBar: EditText
    private lateinit var btnLectureAleatoire: LinearLayout
    private lateinit var iconPlayPause: ImageView

    // Lecteur audio en bas
    private lateinit var musicPlayerBar: LinearLayout
    private lateinit var playerTitle: TextView
    private lateinit var playerArtist: TextView
    private lateinit var btnPlayPausePlayer: ImageView
    private lateinit var btnPrevious: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView

    // Visualiseur audio
    private lateinit var audioVisualizer: AudioVisualizerView

    private val PERMISSION_REQUEST_CODE = 101
    private val AUDIO_PERMISSION_REQUEST = 102

    // Mode lecture aléatoire
    private var isShuffleEnabled = false

    // Handler pour mettre à jour la SeekBar
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBar = object : Runnable {
        override fun run() {
            if (MusicPlayerManager.isPlaying()) {
                val current = MusicPlayerManager.getCurrentPosition()
                val duration = MusicPlayerManager.getDuration()

                seekBar.max = duration
                seekBar.progress = current

                currentTime.text = formatTime(current)
                totalTime.text = formatTime(duration)

                handler.postDelayed(this, 1000)
            }
        }
    }

    // Pour gérer la suppression sur Android 10+
    private var pendingDeleteMusic: Music? = null
    private var pendingDeletePosition: Int = -1

    private val deleteResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingDeleteMusic?.let { music ->
                musicList.remove(music)
                filteredList.remove(music)
                if (pendingDeletePosition != -1) {
                    musicAdapter.notifyItemRemoved(pendingDeletePosition)
                }
                Toast.makeText(this, "Musique supprimée", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Suppression annulée", Toast.LENGTH_SHORT).show()
        }
        pendingDeleteMusic = null
        pendingDeletePosition = -1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialisation
        searchBar = findViewById(R.id.searchBar)
        recyclerView = findViewById(R.id.recyclerViewMusics)
        recyclerView.layoutManager = LinearLayoutManager(this)
        btnLectureAleatoire = findViewById(R.id.btnLectureAleatoire)
        iconPlayPause = findViewById(R.id.iconPlayPause)

        // Lecteur audio
        musicPlayerBar = findViewById(R.id.musicPlayerBar)
        playerTitle = findViewById(R.id.playerTitle)
        playerArtist = findViewById(R.id.playerArtist)
        btnPlayPausePlayer = findViewById(R.id.btnPlayPausePlayer)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        seekBar = findViewById(R.id.seekBar)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)

        // Visualiseur audio
        audioVisualizer = findViewById(R.id.audioVisualizer)

        // Initialiser les listes
        musicList = mutableListOf()
        filteredList = mutableListOf()

        // Configurer le listener pour la fin de lecture automatique
        MusicPlayerManager.setOnMusicCompleteListener {
            runOnUiThread {
                updateShuffleButtonUI()
                updatePlayerUI()

                // Redémarrer le visualiseur pour la nouvelle musique
                val sessionId = MusicPlayerManager.getAudioSessionId()
                if (sessionId != 0) {
                    audioVisualizer.startVisualization(sessionId)
                }
            }
        }

        // Configuration de l'adaptateur
        musicAdapter = MusicAdapter(filteredList, object : MusicAdapter.OnMusicClickListener {
            override fun onMusicClick(music: Music) {
                // Définir la playlist et lancer la musique
                MusicPlayerManager.setPlaylist(filteredList)
                MusicPlayerManager.playMusic(this@MainActivity, music) { error ->
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                }

                // Afficher le lecteur
                showMusicPlayer(music)

                // Démarrer la visualisation audio avec l'audio session ID
                val sessionId = MusicPlayerManager.getAudioSessionId()
                if (sessionId != 0) {
                    audioVisualizer.startVisualization(sessionId)
                } else {
                    // Fallback en mode aléatoire si pas d'audio session
                    audioVisualizer.startAnimation()
                }

                Toast.makeText(this@MainActivity, "Lecture: ${music.title}", Toast.LENGTH_SHORT).show()
            }

            override fun onBookmarkClick(music: Music, position: Int) {
                music.toggleBookmark()
                musicAdapter.notifyItemChanged(position)
                val message = if (music.isBookmarked) "Ajouté aux favoris" else "Retiré des favoris"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }

            override fun onMenuClick(music: Music, position: Int) {
                showMusicMenu(music, position)
            }
        })

        recyclerView.adapter = musicAdapter

        // Recherche
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMusic(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Boutons du lecteur
        setupPlayerButtons()

        // Boutons
        setupButtons()

        // SeekBar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    MusicPlayerManager.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Vérifier et demander les permissions
        checkPermissions()
        checkAudioPermission()
    }

    private fun setupPlayerButtons() {
        // Play/Pause
        btnPlayPausePlayer.setOnClickListener {
            if (MusicPlayerManager.isPlaying()) {
                MusicPlayerManager.pauseMusic()
                btnPlayPausePlayer.setImageResource(android.R.drawable.ic_media_play)
                audioVisualizer.stopVisualization()
            } else {
                MusicPlayerManager.resumeMusic()
                btnPlayPausePlayer.setImageResource(android.R.drawable.ic_media_pause)

                // Redémarrer la visualisation
                val sessionId = MusicPlayerManager.getAudioSessionId()
                if (sessionId != 0) {
                    audioVisualizer.startVisualization(sessionId)
                } else {
                    audioVisualizer.startAnimation()
                }

                handler.post(updateSeekBar)
            }
        }

        // Précédent
        btnPrevious.setOnClickListener {
            MusicPlayerManager.playPreviousMusic(this) { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }

            // Redémarrer la visualisation pour la nouvelle musique
            val sessionId = MusicPlayerManager.getAudioSessionId()
            if (sessionId != 0) {
                audioVisualizer.startVisualization(sessionId)
            }
        }

        // Suivant
        btnNext.setOnClickListener {
            MusicPlayerManager.playNextMusic(this) { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }

            // Redémarrer la visualisation pour la nouvelle musique
            val sessionId = MusicPlayerManager.getAudioSessionId()
            if (sessionId != 0) {
                audioVisualizer.startVisualization(sessionId)
            }
        }
    }

    private fun showMusicPlayer(music: Music) {
        musicPlayerBar.visibility = View.VISIBLE
        playerTitle.text = music.title
        playerArtist.text = music.artist
        btnPlayPausePlayer.setImageResource(android.R.drawable.ic_media_pause)

        // Démarrer la mise à jour de la SeekBar
        handler.post(updateSeekBar)
    }

    private fun updatePlayerUI() {
        val currentMusic = MusicPlayerManager.getCurrentMusic()
        if (currentMusic != null) {
            playerTitle.text = currentMusic.title
            playerArtist.text = currentMusic.artist

            if (MusicPlayerManager.isPlaying()) {
                btnPlayPausePlayer.setImageResource(android.R.drawable.ic_media_pause)
                handler.post(updateSeekBar)
            } else {
                btnPlayPausePlayer.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / 1000) / 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        } else {
            loadMusicFromDevice()
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadMusicFromDevice()
                } else {
                    Toast.makeText(this, "Permission refusée. Impossible de charger les musiques.", Toast.LENGTH_LONG).show()
                }
            }
            AUDIO_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission audio accordée", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Permission audio refusée. Le visualiseur utilisera un mode simple.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadMusicFromDevice() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val title = it.getString(titleColumn)
                val artist = it.getString(artistColumn) ?: "Artiste inconnu"
                val filePath = it.getString(dataColumn)

                musicList.add(Music(title, artist, filePath, id))
            }
        }

        if (musicList.isEmpty()) {
            Toast.makeText(this, "Aucune musique trouvée", Toast.LENGTH_SHORT).show()
        } else {
            MusicPlayerManager.setPlaylist(musicList)
        }

        filteredList.clear()
        filteredList.addAll(musicList)
        musicAdapter.notifyDataSetChanged()
    }

    private fun filterMusic(query: String) {
        filteredList.clear()
        if (query.isEmpty()) {
            filteredList.addAll(musicList)
        } else {
            musicList.forEach { music ->
                if (music.title.lowercase().contains(query.lowercase())) {
                    filteredList.add(music)
                }
            }
        }
        MusicPlayerManager.setPlaylist(filteredList)
        musicAdapter.updateList(filteredList)
    }

    private fun setupButtons() {
        findViewById<LinearLayout>(R.id.btnFavoris).setOnClickListener {
            Toast.makeText(this, "Page Favoris", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.btnCreer).setOnClickListener {
            Toast.makeText(this, "Page Créer", Toast.LENGTH_SHORT).show()
        }

        btnLectureAleatoire.setOnClickListener {
            isShuffleEnabled = !isShuffleEnabled
            MusicPlayerManager.setShuffleMode(isShuffleEnabled)

            updateShuffleButtonUI()

            if (isShuffleEnabled && musicList.isNotEmpty()) {
                val randomMusic = musicList.random()
                MusicPlayerManager.setPlaylist(musicList)
                MusicPlayerManager.playMusic(this, randomMusic) { error ->
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
                showMusicPlayer(randomMusic)

                // Démarrer la visualisation
                val sessionId = MusicPlayerManager.getAudioSessionId()
                if (sessionId != 0) {
                    audioVisualizer.startVisualization(sessionId)
                } else {
                    audioVisualizer.startAnimation()
                }

                Toast.makeText(this, "Lecture aléatoire: ${randomMusic.title}", Toast.LENGTH_SHORT).show()
            } else if (musicList.isEmpty()) {
                Toast.makeText(this, "Aucune musique disponible", Toast.LENGTH_SHORT).show()
            } else {
                audioVisualizer.stopVisualization()
                Toast.makeText(this, "Mode aléatoire désactivé", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<LinearLayout>(R.id.btnHome).setOnClickListener {
            Toast.makeText(this, "Déjà sur l'accueil", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.btnMiddle).setOnClickListener {
            Toast.makeText(this, "Page Albums", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBar)
        audioVisualizer.stopVisualization()
        MusicPlayerManager.stopMusic()
    }

    private fun updateShuffleButtonUI() {
        if (isShuffleEnabled) {
            iconPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            iconPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun showMusicMenu(music: Music, position: Int) {
        val popupMenu = PopupMenu(this, recyclerView.findViewHolderForAdapterPosition(position)?.itemView)

        popupMenu.menu.add(0, 1, 0, "Supprimer")
        popupMenu.menu.add(0, 2, 1, "Informations")

        popupMenu.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                1 -> {
                    showDeleteConfirmation(music, position)
                    true
                }
                2 -> {
                    showMusicInfo(music)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun showDeleteConfirmation(music: Music, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer la musique")
            .setMessage("Voulez-vous vraiment supprimer \"${music.title}\" de votre téléphone ?")
            .setPositiveButton("Supprimer") { _, _ ->
                deleteMusic(music, position)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun deleteMusic(music: Music, position: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    music.id
                )

                try {
                    val deletedRows = contentResolver.delete(uri, null, null)

                    if (deletedRows > 0) {
                        musicList.remove(music)
                        filteredList.remove(music)
                        musicAdapter.notifyItemRemoved(position)
                        Toast.makeText(this, "Musique supprimée", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Impossible de supprimer", Toast.LENGTH_SHORT).show()
                    }
                } catch (securityException: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val recoverableSecurityException = securityException as? RecoverableSecurityException
                            ?: throw securityException

                        pendingDeleteMusic = music
                        pendingDeletePosition = position

                        val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                        val request = androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                        deleteResultLauncher.launch(request)
                    } else {
                        throw securityException
                    }
                }
            } else {
                val file = File(music.filePath)
                if (file.exists() && file.delete()) {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        music.id
                    )
                    contentResolver.delete(uri, null, null)

                    musicList.remove(music)
                    filteredList.remove(music)
                    musicAdapter.notifyItemRemoved(position)
                    Toast.makeText(this, "Musique supprimée", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Impossible de supprimer", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun showMusicInfo(music: Music) {
        val file = File(music.filePath)
        val sizeInMB = file.length() / (1024 * 1024)

        val info = """
            Titre: ${music.title}
            Artiste: ${music.artist}
            Taille: $sizeInMB MB
            Chemin: ${music.filePath}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Informations")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }
}