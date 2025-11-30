package com.example.musique

import android.Manifest
import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
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

    private val PERMISSION_REQUEST_CODE = 101

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

        // Initialiser les listes
        musicList = mutableListOf()
        filteredList = mutableListOf()

        // Configuration de l'adaptateur
        musicAdapter = MusicAdapter(filteredList, object : MusicAdapter.OnMusicClickListener {
            override fun onMusicClick(music: Music) {
                // Lancer la lecture de la musique
                MusicPlayerManager.playMusic(this@MainActivity, music) { error ->
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
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

        // Boutons
        setupButtons()

        // Vérifier et demander les permissions
        checkPermissions()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusicFromDevice()
            } else {
                Toast.makeText(this, "Permission refusée. Impossible de charger les musiques.", Toast.LENGTH_LONG).show()
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
        }

        filteredList.clear()
        filteredList.addAll(musicList)
        musicAdapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Arrêter la musique quand l'activité est détruite
        MusicPlayerManager.stopMusic()
    }

    private fun showMusicMenu(music: Music, position: Int) {
        val popupMenu = PopupMenu(this, recyclerView.findViewHolderForAdapterPosition(position)?.itemView)

        popupMenu.menu.add(0, 1, 0, "Supprimer")
        popupMenu.menu.add(0, 2, 1, "Informations")
        popupMenu.menu.add(0, 3, 2, "Partager")

        popupMenu.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                1 -> {
                    // Supprimer la musique
                    showDeleteConfirmation(music, position)
                    true
                }
                2 -> {
                    // Afficher les informations
                    showMusicInfo(music)
                    true
                }
                3 -> {
                    // Partager
                    Toast.makeText(this, "Fonctionnalité de partage à venir", Toast.LENGTH_SHORT).show()
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
                // Android 10+ : Utiliser le MediaStore avec gestion de la permission
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    music.id
                )

                try {
                    val deletedRows = contentResolver.delete(uri, null, null)

                    if (deletedRows > 0) {
                        // Retirer de la liste
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

                        // Demander à l'utilisateur l'autorisation de supprimer
                        pendingDeleteMusic = music
                        pendingDeletePosition = position

                        val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                        val request = IntentSenderRequest.Builder(intentSender).build()
                        deleteResultLauncher.launch(request)
                    } else {
                        throw securityException
                    }
                }
            } else {
                // Android 9 et inférieur : Suppression directe
                val file = File(music.filePath)
                if (file.exists() && file.delete()) {
                    // Mettre à jour la MediaStore
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        music.id
                    )
                    contentResolver.delete(uri, null, null)

                    // Retirer de la liste
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
        musicAdapter.updateList(filteredList)
    }

    private fun setupButtons() {
        // Bouton Favoris
        findViewById<LinearLayout>(R.id.btnFavoris).setOnClickListener {
            Toast.makeText(this, "Page Favoris", Toast.LENGTH_SHORT).show()
            // TODO: Ouvrir la page Favoris
        }

        // Bouton Créer
        findViewById<LinearLayout>(R.id.btnCreer).setOnClickListener {
            Toast.makeText(this, "Page Créer", Toast.LENGTH_SHORT).show()
            // TODO: Ouvrir la page Créer
        }

        // Lecture aléatoire
        findViewById<LinearLayout>(R.id.btnLectureAleatoire).setOnClickListener {
            if (musicList.isNotEmpty()) {
                val randomMusic = musicList.random()
                MusicPlayerManager.playMusic(this, randomMusic) { error ->
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
                Toast.makeText(this, "Lecture aléatoire: ${randomMusic.title}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Aucune musique disponible", Toast.LENGTH_SHORT).show()
            }
        }

        // Bouton Accueil (navigation bas)
        findViewById<LinearLayout>(R.id.btnHome).setOnClickListener {
            Toast.makeText(this, "Déjà sur l'accueil", Toast.LENGTH_SHORT).show()
        }

        // Bouton Milieu (navigation bas)
        findViewById<LinearLayout>(R.id.btnMiddle).setOnClickListener {
            Toast.makeText(this, "Page Albums", Toast.LENGTH_SHORT).show()
            // TODO: Ouvrir la page Albums/Playlists
        }
    }
}