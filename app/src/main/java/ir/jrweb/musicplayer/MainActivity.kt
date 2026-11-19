package ir.jrweb.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity(), MusicService.PlaybackListener {

    private lateinit var prefs: PrefsManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var tabLayout: TabLayout
    private lateinit var etSearch: EditText
    private lateinit var tvSongCount: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var groupHeader: LinearLayout
    private lateinit var tvGroupTitle: TextView
    private lateinit var btnGroupBack: ImageButton
    private lateinit var btnShuffleGroup: Button

    private lateinit var miniPlayer: LinearLayout
    private lateinit var ivMiniArt: ImageView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPrev: ImageButton
    private lateinit var btnMiniPlayPause: ImageButton
    private lateinit var btnMiniNext: ImageButton

    private lateinit var songAdapter: SongAdapter
    private lateinit var groupAdapter: GroupAdapter

    private var allSongs: List<Song> = emptyList()
    private var selectedGroup: SongGroup? = null
    private var currentTab = 0
    private var searchQuery = ""

    companion object {
        private const val PERMISSION_REQ_CODE = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)
        bindViews()
        setupTabs()
        setupAdapters()
        setupListeners()
        startService(Intent(this, MusicService::class.java))

        checkPermissionsAndLoad()
    }

    private fun bindViews() {
        recyclerView = findViewById(R.id.recyclerView)
        tabLayout = findViewById(R.id.tabLayout)
        etSearch = findViewById(R.id.etSearch)
        tvSongCount = findViewById(R.id.tvSongCount)
        tvEmpty = findViewById(R.id.tvEmpty)
        groupHeader = findViewById(R.id.groupHeader)
        tvGroupTitle = findViewById(R.id.tvGroupTitle)
        btnGroupBack = findViewById(R.id.btnGroupBack)
        btnShuffleGroup = findViewById(R.id.btnShuffleGroup)

        miniPlayer = findViewById(R.id.miniPlayer)
        ivMiniArt = findViewById(R.id.ivMiniArt)
        tvMiniTitle = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        btnMiniPrev = findViewById(R.id.btnMiniPrev)
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause)
        btnMiniNext = findViewById(R.id.btnMiniNext)
    }

    private fun setupTabs() {
        val tabs = listOf("همه آهنگ‌ها", "پوشه‌ها", "خوانندگان", "علاقه‌مندی‌ها")
        tabs.forEach { title ->
            tabLayout.addTab(tabLayout.newTab().setText(title))
        }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                selectedGroup = null
                refreshList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                selectedGroup = null
                refreshList()
            }
        })
    }

    private fun setupAdapters() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        songAdapter = SongAdapter(
            songs = emptyList(),
            prefs = prefs,
            onSongClick = { list, index ->
                playSongs(list, index)
            },
            onFavToggled = {
                if (currentTab == 3) refreshList()
            }
        )
        groupAdapter = GroupAdapter(
            groups = emptyList(),
            prefs = prefs,
            onGroupClick = { group ->
                selectedGroup = group
                refreshList()
            },
            onFolderCheckedChanged = { _, _ ->
                updateActiveSongCount()
            }
        )
    }

    private fun setupListeners() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                refreshList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnGroupBack.setOnClickListener {
            selectedGroup = null
            refreshList()
        }

        btnShuffleGroup.setOnClickListener {
            val songs = selectedGroup?.songs ?: return@setOnClickListener
            if (songs.isNotEmpty()) {
                prefs.isShuffle = true
                playSongs(songs, songs.indices.random())
            }
        }

        miniPlayer.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }

        btnMiniPrev.setOnClickListener {
            MusicService.instance?.playPrevious()
        }

        btnMiniPlayPause.setOnClickListener {
            MusicService.instance?.togglePlayPause()
        }

        btnMiniNext.setOnClickListener {
            MusicService.instance?.playNext()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedGroup != null) {
                    selectedGroup = null
                    refreshList()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun playSongs(songs: List<Song>, index: Int) {
        val service = MusicService.instance
        if (service != null) {
            service.playQueue(songs, index)
        } else {
            startService(Intent(this, MusicService::class.java))
            recyclerView.postDelayed({
                MusicService.instance?.playQueue(songs, index)
            }, 300)
        }
    }

    private fun checkPermissionsAndLoad() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERMISSION_REQ_CODE)
        } else {
            loadSongs()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_CODE) {
            loadSongs()
        }
    }

    private fun loadSongs() {
        Thread {
            val loaded = AudioRepository.loadAllSongs(this)
            runOnUiThread {
                allSongs = loaded
                updateActiveSongCount()
                refreshList()
                if (allSongs.isEmpty()) {
                    Toast.makeText(this, "فایل صوتی یافت نشد یا دسترسی داده نشده است", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun getActiveSongs(): List<Song> {
        val excluded = prefs.getExcludedFolders()
        return allSongs.filter { !excluded.contains(it.folderName) }
    }

    private fun updateActiveSongCount() {
        tvSongCount.text = "${getActiveSongs().size} آهنگ"
    }

    private fun filteredSongs(source: List<Song>): List<Song> {
        if (searchQuery.isEmpty()) return source
        return source.filter {
            it.title.lowercase().contains(searchQuery) ||
                it.artist.lowercase().contains(searchQuery) ||
                it.folderName.lowercase().contains(searchQuery)
        }
    }

    private fun refreshList() {
        updateActiveSongCount()
        val group = selectedGroup
        if (group != null) {
            groupHeader.visibility = View.VISIBLE
            tvGroupTitle.text = "${group.name} (${group.songs.size})"
            val list = filteredSongs(group.songs)
            recyclerView.adapter = songAdapter
            songAdapter.updateList(list)
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            return
        }

        groupHeader.visibility = View.GONE
        val active = getActiveSongs()

        when (currentTab) {
            0 -> { // All active songs (from checked folders)
                val list = filteredSongs(active)
                recyclerView.adapter = songAdapter
                songAdapter.updateList(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
            1 -> { // All folders with Checkboxes
                val groups = filteredSongs(allSongs)
                    .groupBy { it.folderName }
                    .map { (name, list) -> SongGroup(name, list, true) }
                    .sortedBy { it.name.lowercase() }
                recyclerView.adapter = groupAdapter
                groupAdapter.updateGroups(groups)
                tvEmpty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
            }
            2 -> { // Artists (from checked folders)
                val groups = filteredSongs(active)
                    .groupBy { it.artist }
                    .map { (name, list) -> SongGroup(name, list, false) }
                    .sortedBy { it.name.lowercase() }
                recyclerView.adapter = groupAdapter
                groupAdapter.updateGroups(groups)
                tvEmpty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
            }
            3 -> { // Favorites (from checked folders)
                val favIds = prefs.getFavoriteIds()
                val favs = filteredSongs(active.filter { favIds.contains(it.id.toString()) })
                recyclerView.adapter = songAdapter
                songAdapter.updateList(favs)
                tvEmpty.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateMiniPlayer() {
        val service = MusicService.instance
        val song = service?.currentSong
        if (song == null) {
            miniPlayer.visibility = View.GONE
            return
        }
        miniPlayer.visibility = View.VISIBLE
        tvMiniTitle.text = song.title
        tvMiniArtist.text = song.artist
        btnMiniPlayPause.setImageResource(if (service.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        AudioRepository.loadAlbumArt(this, song.albumId, ivMiniArt)
        if (recyclerView.adapter === songAdapter) {
            songAdapter.notifyDataSetChanged()
        }
    }

    override fun onPlaybackChanged() {
        runOnUiThread { updateMiniPlayer() }
    }

    override fun onResume() {
        super.onResume()
        MusicService.addListener(this)
        updateMiniPlayer()
    }

    override fun onPause() {
        super.onPause()
        MusicService.removeListener(this)
    }
}