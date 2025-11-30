package com.example.musique

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MusicAdapter(
    private var musicList: MutableList<Music>,
    private val listener: OnMusicClickListener
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    interface OnMusicClickListener {
        fun onMusicClick(music: Music)
        fun onBookmarkClick(music: Music, position: Int)
        fun onMenuClick(music: Music, position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music, parent, false)
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val music = musicList[position]
        holder.musicTitle.text = music.title

        // Changer l'icône bookmark selon l'état
        if (music.isBookmarked) {
            holder.bookmarkIcon.setImageResource(android.R.drawable.btn_star_big_on)
        } else {
            holder.bookmarkIcon.setImageResource(android.R.drawable.btn_star_big_off)
        }

        // Clic sur la musique
        holder.itemView.setOnClickListener {
            listener.onMusicClick(music)
        }

        // Clic sur bookmark
        holder.bookmarkIcon.setOnClickListener {
            listener.onBookmarkClick(music, position)
        }

        // Clic sur menu
        holder.menuIcon.setOnClickListener {
            listener.onMenuClick(music, position)
        }
    }

    override fun getItemCount(): Int = musicList.size

    fun updateList(newList: MutableList<Music>) {
        musicList = newList
        notifyDataSetChanged()
    }

    class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val musicTitle: TextView = itemView.findViewById(R.id.musicTitle)
        val musicThumbnail: ImageView = itemView.findViewById(R.id.musicThumbnail)
        val bookmarkIcon: ImageView = itemView.findViewById(R.id.bookmarkIcon)
        val menuIcon: ImageView = itemView.findViewById(R.id.menuIcon)
    }
}