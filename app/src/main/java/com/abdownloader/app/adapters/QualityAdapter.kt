package com.abdownloader.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abdownloader.app.VideoInfo
import com.abdownloader.app.databinding.ItemQualityChipBinding

class QualityAdapter(
    private val formats: List<VideoInfo.Format>,
    private val isAudio: Boolean,
    private val onItemSelected: (VideoInfo.Format) -> Unit
) : RecyclerView.Adapter<QualityAdapter.ViewHolder>() {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQualityChipBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val format = formats[position]
        holder.bind(format, position == selectedPosition)
        holder.itemView.setOnClickListener {
            selectedPosition = position
            onItemSelected(format)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = formats.size

    inner class ViewHolder(private val binding: ItemQualityChipBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(format: VideoInfo.Format, isSelected: Boolean) {
            binding.chipText.text = format.label
            binding.chipText.isChecked = isSelected
            binding.chipText.setChipBackgroundColorResource(
                if (isSelected) android.R.color.holo_blue_light else android.R.color.transparent
            )
        }
    }
}
