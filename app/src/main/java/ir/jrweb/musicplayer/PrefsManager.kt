package ir.jrweb.musicplayer

import android.content.Context

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("jrweb_music_prefs", Context.MODE_PRIVATE)

    fun getFavoriteIds(): MutableSet<String> {
        return prefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun isFavorite(songId: Long): Boolean {
        return getFavoriteIds().contains(songId.toString())
    }

    fun toggleFavorite(songId: Long): Boolean {
        val set = getFavoriteIds()
        val key = songId.toString()
        val added = if (set.contains(key)) {
            set.remove(key)
            false
        } else {
            set.add(key)
            true
        }
        prefs.edit().putStringSet("favorites", set).apply()
        return added
    }

    // Folder Filter (checked = NOT in excluded set)
    fun getExcludedFolders(): MutableSet<String> {
        return prefs.getStringSet("excluded_folders", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun isFolderSelected(folderName: String): Boolean {
        return !getExcludedFolders().contains(folderName)
    }

    fun setFolderSelected(folderName: String, selected: Boolean) {
        val excluded = getExcludedFolders()
        if (selected) excluded.remove(folderName) else excluded.add(folderName)
        prefs.edit().putStringSet("excluded_folders", excluded).apply()
    }

    var isShuffle: Boolean
        get() = prefs.getBoolean("shuffle", false)
        set(value) = prefs.edit().putBoolean("shuffle", value).apply()

    var repeatMode: RepeatMode
        get() {
            val idx = prefs.getInt("repeat_mode", RepeatMode.OFF.ordinal)
            return RepeatMode.values().getOrElse(idx) { RepeatMode.OFF }
        }
        set(value) = prefs.edit().putInt("repeat_mode", value.ordinal).apply()

    // Equalizer settings
    var isEqEnabled: Boolean
        get() = prefs.getBoolean("eq_enabled", false)
        set(value) = prefs.edit().putBoolean("eq_enabled", value).apply()

    var bassBoostStrength: Int
        get() = prefs.getInt("eq_bass", 0)
        set(value) = prefs.edit().putInt("eq_bass", value).apply()

    fun getBandLevel(band: Int, defaultLevel: Int = 0): Int {
        return prefs.getInt("eq_band_$band", defaultLevel)
    }

    fun setBandLevel(band: Int, level: Int) {
        prefs.edit().putInt("eq_band_$band", level).apply()
    }
}