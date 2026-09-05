package com.abdownloader.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.abdownloader.app.adapters.PlaylistAdapter
import com.abdownloader.app.databinding.ActivityPlaylistBinding
import kotlinx.coroutines.launch

class PlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistBinding
    private lateinit var adapter: PlaylistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val playlistUrl = intent.getStringExtra("playlist_url") ?: run {
            finish()
            return
        }

        setSupportActionBar(binding.toolbarPlaylist)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = PlaylistAdapter(emptyList()) { _, _ -> }
        binding.recyclerPlaylist.layoutManager = LinearLayoutManager(this)
        binding.recyclerPlaylist.adapter = adapter

        // Fetch playlist items
        lifecycleScope.launch {
            val result = DownloadManager.fetchPlaylistItems(playlistUrl)
            result.onSuccess { items ->
                adapter.updateItems(items)
                updateButtonText()
            }.onFailure {
                Toast.makeText(this@PlaylistActivity, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Select/Deselect All
        binding.btnSelectAll.setOnClickListener {
            if (adapter.getSelectedCount() == adapter.itemCount) {
                adapter.deselectAll()
            } else {
                adapter.selectAll()
            }
            updateButtonText()
        }

        // Download Selected
        binding.btnDownloadSelected.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) {
                Toast.makeText(this, "Select at least one video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // For each selected, we need to show quality picker.
            // For simplicity, we'll pick the best quality for each.
            // In a real app, you'd show a quality picker for the first and apply to all.
            // Let's show a quality picker for the first video.
            // We'll use the existing MainActivity's quality picker logic.
            // But since we're in a separate activity, we'll show a simplified dialog.
            // For now, we just download with best quality.
            selected.forEach { video ->
                val bestFormat = video.formats.firstOrNull { !it.isAudio }
                if (bestFormat != null) {
                    DownloadManager.addDownload(video, bestFormat)
                }
            }
            Toast.makeText(this, "Added ${selected.size} downloads to queue", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateButtonText() {
        val count = adapter.getSelectedCount()
        binding.btnDownloadSelected.text = "Download Selected ($count)"
        if (count == adapter.itemCount && adapter.itemCount > 0) {
            binding.btnSelectAll.text = "Deselect All"
        } else {
            binding.btnSelectAll.text = "Select All"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
