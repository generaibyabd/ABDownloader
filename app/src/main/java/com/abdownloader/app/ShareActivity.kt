package com.abdownloader.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abdownloader.app.databinding.DialogQualityPickerBinding
import com.abdownloader.app.adapters.QualityAdapter
import kotlinx.coroutines.launch
import android.content.Intent

class ShareActivity : AppCompatActivity() {

    private lateinit var binding: DialogQualityPickerBinding
    private var videoInfo: VideoInfo? = null
    private var selectedFormat: VideoInfo.Format? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the quality picker dialog
        binding = DialogQualityPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get shared URL
        val sharedUrl = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: run {
            finish()
            return
        }

        // Fetch info
        lifecycleScope.launch {
            val result = DownloadManager.fetchVideoInfo(sharedUrl)
            result.onSuccess { info ->
                videoInfo = info
                setupUI(info)
            }.onFailure {
                binding.txtTitle.text = "Error: ${it.message}"
            }
        }
    }

    private fun setupUI(info: VideoInfo) {
        binding.txtTitle.text = info.title
        binding.txtDuration.text = formatDuration(info.duration)

        // Load thumbnail with Glide
        // (already imported in adapter)

        // Split formats into video and audio
        val videoFormats = info.formats.filter { !it.isAudio }
        val audioFormats = info.formats.filter { it.isAudio }

        // Setup video grid (3 columns)
        val videoAdapter = QualityAdapter(videoFormats, false) { format ->
            selectedFormat = format
        }
        binding.recyclerVideoQualities.layoutManager = 
            androidx.recyclerview.widget.GridLayoutManager(this, 3)
        binding.recyclerVideoQualities.adapter = videoAdapter

        // Setup audio grid (3 columns)
        val audioAdapter = QualityAdapter(audioFormats, true) { format ->
            selectedFormat = format
        }
        binding.recyclerAudioQualities.layoutManager = 
            androidx.recyclerview.widget.GridLayoutManager(this, 3)
        binding.recyclerAudioQualities.adapter = audioAdapter

        // Confirm download
        binding.btnConfirmDownload.setOnClickListener {
            selectedFormat?.let { format ->
                DownloadManager.addDownload(info, format)
                finish()
            }
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }

    override fun onBackPressed() {
        finish()
    }
}
