package com.example.musique

data class Music(
    var title: String,
    var artist: String,
    var filePath: String,
    var id: Long = 0L,  // ID du MediaStore
    var isBookmarked: Boolean = false
) {
    fun toggleBookmark() {
        isBookmarked = !isBookmarked
    }
}