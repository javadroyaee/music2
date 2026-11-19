package ir.jrweb.musicplayer

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

class PlayerActivity : AppCompatActivity(), MusicService.PlaybackListener {

    private lateinit var prefs: PrefsManager
    private lateinit var btnBack: ImageButton
    private lateinit var btnEqualizer: ImageButton
    private lateinit var btnFavorite: ImageButton
    private lateinit var ivAlbumArt: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvFolder: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnRepeat: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private var isTrackingSeek = false

    private val progressUpdater = object : Runnable {
        override fun run() {
            val service = MusicService.instance
            if (service != null && !isTrackingSeek) {
                val pos = service.currentPosition
                val dur = service.duration
                if (dur > 0) {
                    seekBar.max = dur
                    seekBar.progress = pos
                    tvCurrentTime.text = formatDuration(pos.toLong())
                    tvTotalTime.text = formatDuration(dur.toLong())
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        prefs = PrefsManager(this)
        bindViews()
        setupListeners()
        updateUI()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        btnEqualizer = findViewById(R.id.btnEqualizer)
        btnFavorite = findViewById(R.id.btnFavorite)
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        tvTitle = findViewById(R.id.tvTitle)
        tvArtist = findViewById(R.id.tvArtist)
        tvFolder = findViewById(R.id.tvFolder)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnPrev = findViewById(R.id.btnPrev)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnRepeat = findViewById(R.id.btnRepeat)
        tvTitle.isSelected = true
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnEqualizer.setOnClickListener { showEqualizerDialog() }

        btnFavorite.setOnClickListener {
            val song = MusicService.instance?.currentSong ?: return@setOnClickListener
            val added = prefs.toggleFavorite(song.id)
            updateFavoriteIcon(added)
        }

        btnPlayPause.setOnClickListener {
            MusicService.instance?.togglePlayPause()
        }

        btnNext.setOnClickListener {
            MusicService.instance?.playNext()
        }

        btnPrev.setOnClickListener {
            MusicService.instance?.playPrevious()
        }

        btnShuffle.setOnClickListener {
            prefs.isShuffle = !prefs.isShuffle
            updateModeIcons()
            Toast.makeText(
                this,
                if (prefs.isShuffle) "پخش تصادفی روشن شد" else "پخش تصادفی خاموش شد",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnRepeat.setOnClickListener {
            prefs.repeatMode = when (prefs.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            updateModeIcons()
            val msg = when (prefs.repeatMode) {
                RepeatMode.OFF -> "تکرار خاموش"
                RepeatMode.ALL -> "تکرار همه آهنگ‌ها"
                RepeatMode.ONE -> "تکرار همین آهنگ"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatDuration(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {
                isTrackingSeek = true
            }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isTrackingSeek = false
                sb?.let { MusicService.instance?.seekTo(it.progress) }
            }
        })
    }

    private fun showEqualizerDialog() {
        val service = MusicService.instance
        val eq = service?.getEqualizerInstance()
        if (eq == null) {
            Toast.makeText(this, "اکولایزر در دسترس نیست", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_equalizer, null)
        dialog.setContentView(view)

        val switchEq = view.findViewById<SwitchCompat>(R.id.switchEq)
        val presetContainer = view.findViewById<LinearLayout>(R.id.presetContainer)
        val bandsContainer = view.findViewById<LinearLayout>(R.id.bandsContainer)
        val seekBass = view.findViewById<SeekBar>(R.id.seekBass)

        switchEq.isChecked = prefs.isEqEnabled
        switchEq.setOnCheckedChangeListener { _, isChecked ->
            service.setEqualizerEnabled(isChecked)
        }

        val minLevel = eq.bandLevelRange[0].toInt()
        val maxLevel = eq.bandLevelRange[1].toInt()
        val numBands = eq.numberOfBands.toInt()
        val bandSeekBars = mutableListOf<SeekBar>()

        for (i in 0 until numBands) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val freqHz = eq.getCenterFreq(i.toShort()) / 1000
            val labelText = if (freqHz >= 1000) "${freqHz / 1000}kHz" else "${freqHz}Hz"

            val tvFreq = TextView(this).apply {
                text = labelText
                width = 160
                setTextColor(ContextCompat.getColor(this@PlayerActivity, R.color.text_dark))
                textSize = 13f
            }

            val sb = SeekBar(this).apply {
                max = maxLevel - minLevel
                progress = prefs.getBandLevel(i, 0) - minLevel
                progressTintList = ColorStateList.valueOf(ContextCompat.getColor(this@PlayerActivity, R.color.orange_accent))
                thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(this@PlayerActivity, R.color.purple_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val level = (p + minLevel).toShort()
                            service.setEqualizerBandLevel(i.toShort(), level)
                        }
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }

            bandSeekBars.add(sb)
            row.addView(tvFreq)
            row.addView(sb)
            bandsContainer.addView(row)
        }

        // Presets
        val presets = listOf(
            "عادی" to intArrayOf(0, 0, 0, 0, 0),
            "پاپ" to intArrayOf(-100, 200, 400, 200, -100),
            "راک" to intArrayOf(400, 200, -100, 200, 400),
            "بیس قوی" to intArrayOf(600, 400, 100, 0, 0),
            "کلاسیک" to intArrayOf(300, 200, 0, 200, 300)
        )

        presets.forEach { (name, levels) ->
            val btn = Button(this).apply {
                text = name
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@PlayerActivity, R.color.white))
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@PlayerActivity, R.color.purple_primary))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = 12
                layoutParams = lp
                setOnClickListener {
                    switchEq.isChecked = true
                    service.setEqualizerEnabled(true)
                    for (i in 0 until minOf(numBands, levels.size)) {
                        val lvl = levels[i].coerceIn(minLevel, maxLevel)
                        service.setEqualizerBandLevel(i.toShort(), lvl.toShort())
                        bandSeekBars[i].progress = lvl - minLevel
                    }
                }
            }
            presetContainer.addView(btn)
        }

        seekBass.progress = prefs.bassBoostStrength
        seekBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    service.setBassBoostStrength(p.toShort())
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        dialog.show()
    }

    private fun updateUI() {
        val service = MusicService.instance
        val song = service?.currentSong
        if (song == null) {
            finish()
            return
        }

        tvTitle.text = song.title
        tvArtist.text = song.artist
        tvFolder.text = "پوشه: ${song.folderName}"
        tvTotalTime.text = formatDuration(song.duration)
        btnPlayPause.setImageResource(if (service.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        AudioRepository.loadAlbumArt(this, song.albumId, ivAlbumArt)

        updateFavoriteIcon(prefs.isFavorite(song.id))
        updateModeIcons()
    }

    private fun updateFavoriteIcon(isFav: Boolean) {
        btnFavorite.setImageResource(if (isFav) R.drawable.ic_heart else R.drawable.ic_heart_outline)
    }

    private fun updateModeIcons() {
        val orange = ContextCompat.getColor(this, R.color.orange_accent)
        val muted = ContextCompat.getColor(this, R.color.text_muted)

        btnShuffle.setColorFilter(if (prefs.isShuffle) orange else muted)

        when (prefs.repeatMode) {
            RepeatMode.OFF -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat)
                btnRepeat.setColorFilter(muted)
            }
            RepeatMode.ALL -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat)
                btnRepeat.setColorFilter(orange)
            }
            RepeatMode.ONE -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                btnRepeat.setColorFilter(orange)
            }
        }
    }

    override fun onPlaybackChanged() {
        runOnUiThread { updateUI() }
    }

    override fun onResume() {
        super.onResume()
        MusicService.addListener(this)
        updateUI()
        handler.post(progressUpdater)
    }

    override fun onPause() {
        super.onPause()
        MusicService.removeListener(this)
        handler.removeCallbacks(progressUpdater)
    }
}