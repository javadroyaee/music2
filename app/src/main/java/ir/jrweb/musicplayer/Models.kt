package ir.jrweb.musicplayer

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: Uri,
    val folderName: String
)

data class SongGroup(
    val name: String,
    val songs: List<Song>,
    val isFolder: Boolean
)

enum class RepeatMode {
    OFF, ALL, ONE
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}