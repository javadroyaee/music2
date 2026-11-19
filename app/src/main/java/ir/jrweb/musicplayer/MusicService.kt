package ir.jrweb.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import kotlin.random.Random

class MusicService : Service() {

    interface PlaybackListener {
        fun onPlaybackChanged()
    }

    companion object {
        const val CHANNEL_ID = "jrweb_music_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "ir.jrweb.musicplayer.PLAY_PAUSE"
        const val ACTION_NEXT = "ir.jrweb.musicplayer.NEXT"
        const val ACTION_PREV = "ir.jrweb.musicplayer.PREV"
        const val ACTION_STOP = "ir.jrweb.musicplayer.STOP"

        var instance: MusicService? = null
            private set

        private val listeners = mutableSetOf<PlaybackListener>()

        fun addListener(listener: PlaybackListener) {
            listeners.add(listener)
        }

        fun removeListener(listener: PlaybackListener) {
            listeners.remove(listener)
        }

        private fun notifyListeners() {
            listeners.forEach { it.onPlaybackChanged() }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var prefs: PrefsManager

    var queue: List<Song> = emptyList()
        private set
    var currentIndex: Int = -1
        private set

    val currentSong: Song?
        get() = queue.getOrNull(currentIndex)

    val isPlaying: Boolean
        get() = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }

    val currentPosition: Int
        get() = try { mediaPlayer?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    val duration: Int
        get() = try { mediaPlayer?.duration ?: (currentSong?.duration?.toInt() ?: 0) } catch (_: Exception) { 0 }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PrefsManager(this)
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "JRwebMusicSession").apply {
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrevious()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        queue = songs
        currentIndex = startIndex.coerceIn(0, songs.lastIndex)
        startCurrentSong()
    }

    private fun startCurrentSong() {
        val song = currentSong ?: return
        try {
            releaseEffects()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(applicationContext, song.uri)
                setOnPreparedListener { mp ->
                    setupAudioEffects(mp.audioSessionId)
                    mp.start()
                    updateMediaSession()
                    showForegroundNotification()
                    notifyListeners()
                }
                setOnCompletionListener {
                    handleCompletion()
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            playNext()
        }
    }

    private fun setupAudioEffects(sessionId: Int) {
        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = prefs.isEqEnabled
                val bands = numberOfBands.toInt()
                for (i in 0 until bands) {
                    val saved = prefs.getBandLevel(i, 0).toShort()
                    setBandLevel(i.toShort(), saved)
                }
            }
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = prefs.isEqEnabled
                setStrength(prefs.bassBoostStrength.toShort())
            }
        } catch (_: Exception) {}
    }

    private fun releaseEffects() {
        try {
            equalizer?.release()
            equalizer = null
            bassBoost?.release()
            bassBoost = null
        } catch (_: Exception) {}
    }

    fun getEqualizerInstance(): Equalizer? = equalizer

    fun setEqualizerEnabled(enabled: Boolean) {
        prefs.isEqEnabled = enabled
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        } catch (_: Exception) {}
    }

    fun setEqualizerBandLevel(band: Short, level: Short) {
        prefs.setBandLevel(band.toInt(), level.toInt())
        try {
            equalizer?.setBandLevel(band, level)
        } catch (_: Exception) {}
    }

    fun setBassBoostStrength(strength: Short) {
        prefs.bassBoostStrength = strength.toInt()
        try {
            bassBoost?.setStrength(strength)
        } catch (_: Exception) {}
    }

    private fun handleCompletion() {
        when (prefs.repeatMode) {
            RepeatMode.ONE -> startCurrentSong()
            RepeatMode.ALL -> playNext(forceLoop = true)
            RepeatMode.OFF -> {
                if (prefs.isShuffle || currentIndex < queue.lastIndex) {
                    playNext(forceLoop = false)
                } else {
                    showForegroundNotification()
                    notifyListeners()
                }
            }
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
        } else {
            mp.start()
        }
        updateMediaSession()
        showForegroundNotification()
        notifyListeners()
    }

    fun playNext(forceLoop: Boolean = true) {
        if (queue.isEmpty()) return
        currentIndex = if (prefs.isShuffle && queue.size > 1) {
            var next = Random.nextInt(queue.size)
            while (next == currentIndex) {
                next = Random.nextInt(queue.size)
            }
            next
        } else {
            if (currentIndex < queue.lastIndex) currentIndex + 1
            else if (forceLoop || prefs.repeatMode == RepeatMode.ALL) 0
            else return
        }
        startCurrentSong()
    }

    fun playPrevious() {
        if (queue.isEmpty()) return
        if (currentPosition > 3000) {
            seekTo(0)
            return
        }
        currentIndex = if (currentIndex > 0) currentIndex - 1 else queue.lastIndex
        startCurrentSong()
    }

    fun seekTo(posMs: Int) {
        try {
            mediaPlayer?.seekTo(posMs)
        } catch (_: Exception) {}
    }

    fun stopPlayback() {
        releaseEffects()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifyListeners()
        stopSelf()
    }

    private fun updateMediaSession() {
        val song = currentSong ?: return
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, currentPosition.toLong(), 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                .build()
        )
    }

    private fun showForegroundNotification() {
        val song = currentSong ?: return
        val art = AudioRepository.getAlbumArtSync(this, song.albumId)

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = servicePendingIntent(ACTION_PREV, 1)
        val playPauseIntent = servicePendingIntent(ACTION_PLAY_PAUSE, 2)
        val nextIntent = servicePendingIntent(ACTION_NEXT, 3)
        val stopIntent = servicePendingIntent(ACTION_STOP, 4)

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(art)
            .setContentTitle(song.title)
            .setContentText("${song.artist} • JRweb Music")
            .setContentIntent(openIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_prev_white, "قبلی", prevIntent)
            .addAction(playPauseIcon, if (isPlaying) "توقف" else "پخش", playPauseIntent)
            .addAction(R.drawable.ic_next_white, "بعدی", nextIntent)
            .addAction(R.drawable.ic_close, "بستن", stopIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun servicePendingIntent(action: String, reqCode: Int): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JRweb Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "کنترل پخش موزیک پلیر جی‌آروب"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseEffects()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}