package ir.jrweb.musicplayer

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

object AudioRepository {

    private val artCache = LruCache<Long, Bitmap>(60)
    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadAllSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "بدون عنوان"
                val rawArtist = cursor.getString(artistCol)
                val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "ناشناس" else rawArtist
                val album = cursor.getString(albumCol) ?: ""
                val albumId = cursor.getLong(albumIdCol)
                val duration = cursor.getLong(durCol)
                val dataPath = cursor.getString(dataCol) ?: ""
                val folderName = try {
                    File(dataPath).parentFile?.name ?: "حافظه داخلی"
                } catch (_: Exception) {
                    "حافظه داخلی"
                }
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                songs.add(Song(id, title, artist, album, albumId, duration, contentUri, folderName))
            }
        }
        return songs
    }

    fun getAlbumArtSync(context: Context, albumId: Long): Bitmap? {
        artCache.get(albumId)?.let { return it }
        return try {
            val uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) artCache.put(albumId, bmp)
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadAlbumArt(context: Context, albumId: Long, imageView: ImageView) {
        imageView.tag = albumId
        val cached = artCache.get(albumId)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }
        imageView.setImageResource(R.drawable.ic_music_note)
        executor.execute {
            val bmp = getAlbumArtSync(context.applicationContext, albumId)
            mainHandler.post {
                if (imageView.tag == albumId && bmp != null) {
                    imageView.setImageBitmap(bmp)
                }
            }
        }
    }
}