package com.abdownloader.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abdownloader.app.DownloadTask
import com.abdownloader.app.DownloadStatus
import com.abdownloader.app.databinding.ItemDownloadProgressBinding
import com.bumptech.glide.Glide

class DownloadProgressAdapter(
    private var tasks: List<DownloadTask> = emptyList()
) : RecyclerView.Adapter<DownloadProgressAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadProgressBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<DownloadTask>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemDownloadProgressBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(task: DownloadTask) {
            binding.txtTitle.text = task.videoInfo.title
            binding.progressBar.progress = task.progress

            val statusText = when (task.status) {
                is DownloadStatus.Queued -> "Queued..."
                is DownloadStatus.Downloading -> {
                    val d = task.status as DownloadStatus.Downloading
                    "Downloading ${d.progress}% • ${d.speed} • ETA ${d.eta}"
                }
                is DownloadStatus.Finished -> "✅ Downloaded"
                is DownloadStatus.Failed -> "❌ Failed: ${(task.status as DownloadStatus.Failed).error}"
                else -> "Idle"
            }
            binding.txtStatus.text = statusText

            Glide.with(binding.root.context)
                .load(task.videoInfo.thumbnail)
                .placeholder(android.R.drawable.ic_menu_camera)
                .into(binding.imgThumbnail)
        }
    }
}
