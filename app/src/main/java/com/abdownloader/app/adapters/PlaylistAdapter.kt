package com.abdownloader.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abdownloader.app.VideoInfo
import com.abdownloader.app.databinding.ItemPlaylistVideoBinding
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Locale

class PlaylistAdapter(
    private var items: List<VideoInfo> = emptyList(),
    private val onCheckChanged: (VideoInfo, Boolean) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    private val selectedItems = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistVideoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<VideoInfo>) {
        items = newItems
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun selectAll() {
        items.forEach { selectedItems.add(it.url) }
        notifyDataSetChanged()
    }

    fun deselectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<VideoInfo> {
        return items.filter { selectedItems.contains(it.url) }
    }

    fun getSelectedCount(): Int = selectedItems.size

    inner class ViewHolder(private val binding: ItemPlaylistVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(video: VideoInfo) {
            binding.txtTitle.text = video.title
            binding.txtDuration.text = formatDuration(video.duration)
            binding.checkVideo.isChecked = selectedItems.contains(video.url)

            Glide.with(binding.root.context)
                .load(video.thumbnail)
                .placeholder(android.R.drawable.ic_menu_camera)
                .into(binding.imgThumbnail)

            binding.checkVideo.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedItems.add(video.url)
                } else {
                    selectedItems.remove(video.url)
                }
                onCheckChanged(video, isChecked)
            }
        }

        private fun formatDuration(seconds: Long): String {
            if (seconds <= 0) return "00:00"
            val sdf = SimpleDateFormat("mm:ss", Locale.getDefault())
            return sdf.format(java.util.Date(seconds * 1000))
        }
    }
}
