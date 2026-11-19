package ir.jrweb.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private var songs: List<Song>,
    private val prefs: PrefsManager,
    private val onSongClick: (List<Song>, Int) -> Unit,
    private val onFavToggled: () -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivArt: ImageView = view.findViewById(R.id.ivArt)
        val tvTitle: TextView = view.findViewById(R.id.tvSongTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvSongSubtitle)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val btnFav: ImageButton = view.findViewById(R.id.btnItemFav)
    }

    fun updateList(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        val ctx = holder.itemView.context
        val isCurrent = MusicService.instance?.currentSong?.id == song.id

        holder.tvTitle.text = song.title
        holder.tvTitle.setTextColor(
            ContextCompat.getColor(ctx, if (isCurrent) R.color.orange_accent else R.color.text_dark)
        )
        holder.tvSubtitle.text = "${song.artist} • ${song.folderName}"
        holder.tvDuration.text = formatDuration(song.duration)

        AudioRepository.loadAlbumArt(ctx, song.albumId, holder.ivArt)

        val fav = prefs.isFavorite(song.id)
        holder.btnFav.setImageResource(if (fav) R.drawable.ic_heart else R.drawable.ic_heart_outline)

        holder.btnFav.setOnClickListener {
            prefs.toggleFavorite(song.id)
            notifyItemChanged(position)
            onFavToggled()
        }

        holder.itemView.setOnClickListener {
            onSongClick(songs, position)
        }
    }

    override fun getItemCount(): Int = songs.size
}

class GroupAdapter(
    private var groups: List<SongGroup>,
    private val prefs: PrefsManager,
    private val onGroupClick: (SongGroup) -> Unit,
    private val onFolderCheckedChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbFolder: CheckBox = view.findViewById(R.id.cbFolder)
        val ivIcon: ImageView = view.findViewById(R.id.ivGroupIcon)
        val tvName: TextView = view.findViewById(R.id.tvGroupName)
        val tvCount: TextView = view.findViewById(R.id.tvGroupCount)
    }

    fun updateGroups(newGroups: List<SongGroup>) {
        groups = newGroups
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.ivIcon.setImageResource(if (group.isFolder) R.drawable.ic_folder else R.drawable.ic_artist)
        holder.tvName.text = group.name
        holder.tvCount.text = "${group.songs.size} آهنگ"

        if (group.isFolder) {
            holder.cbFolder.visibility = View.VISIBLE
            holder.cbFolder.setOnCheckedChangeListener(null)
            holder.cbFolder.isChecked = prefs.isFolderSelected(group.name)
            holder.cbFolder.setOnCheckedChangeListener { _, isChecked ->
                prefs.setFolderSelected(group.name, isChecked)
                onFolderCheckedChanged(group.name, isChecked)
            }
        } else {
            holder.cbFolder.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onGroupClick(group) }
    }

    override fun getItemCount(): Int = groups.size
}