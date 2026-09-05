package com.abdownloader.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.abdownloader.app.adapters.DownloadProgressAdapter
import com.abdownloader.app.adapters.QualityAdapter
import com.abdownloader.app.databinding.ActivityMainBinding
import com.abdownloader.app.databinding.DialogQualityPickerBinding
import com.abdownloader.app.databinding.FragmentDownloadsBinding
import com.abdownloader.app.databinding.FragmentPasteBinding
import com.abdownloader.app.databinding.FragmentSettingsBinding
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pasteBinding: FragmentPasteBinding
    private lateinit var downloadsBinding: FragmentDownloadsBinding
    private lateinit var settingsBinding: FragmentSettingsBinding

    private lateinit var downloadAdapter: DownloadProgressAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set global context for DownloadManager
        AppGlobals.context = applicationContext

        // Initialize yt-dlp
        YoutubeDLInitializer.initialize(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup ViewPager2 with 3 tabs
        setupViewPager()

        // Setup bottom tabs
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Paste"
                1 -> "Downloads"
                2 -> "Settings"
                else -> ""
            }
        }.attach()

        // Initialize UI for each fragment
        setupPasteTab()
        setupDownloadsTab()
        setupSettingsTab()

        // Observe downloads
        DownloadManager.tasks.observe(this) { tasks ->
            downloadAdapter.updateTasks(tasks)
            val emptyView = downloadsBinding.txtEmptyDownloads
            if (tasks.isEmpty()) {
                emptyView.visibility = android.view.View.VISIBLE
                downloadsBinding.recyclerDownloads.visibility = android.view.View.GONE
            } else {
                emptyView.visibility = android.view.View.GONE
                downloadsBinding.recyclerDownloads.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun setupViewPager() {
        val adapter = object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3
            override fun createFragment(position: Int) = when (position) {
                0 -> PasteFragment()
                1 -> DownloadsFragment()
                2 -> SettingsFragment()
                else -> PasteFragment()
            }
        }
        binding.viewPager.adapter = adapter
    }

    // --- PASTE TAB ---
    private fun setupPasteTab() {
        // We'll use a Fragment approach, but since we're not using actual Fragments
        // in this simplified build, we directly get the views from the inflated layout.
        // For simplicity, we'll manage views manually.
        // Actually, better to use Fragments. Let's define inner Fragments or keep it simple.
        // I'll restructure to use Fragment classes.
    }

    // Since we are using Fragments, we need to define them as inner classes or separate files.
    // To keep it simple, I'll provide the Fragment classes now.

    inner class PasteFragment : androidx.fragment.app.Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            pasteBinding = FragmentPasteBinding.inflate(inflater, container, false)
            pasteBinding.btnDownload.setOnClickListener {
                val url = pasteBinding.inputUrl.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(requireContext(), "Please paste a URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Fetch info and show quality picker
                lifecycleScope.launch {
                    pasteBinding.txtStatus.text = "Fetching info..."
                    val result = DownloadManager.fetchVideoInfo(url)
                    result.onSuccess { info ->
                        pasteBinding.txtStatus.text = ""
                        if (info.isPlaylistItem) {
                            // Open playlist activity
                            startActivity(Intent(requireContext(), PlaylistActivity::class.java).apply {
                                putExtra("playlist_url", url)
                            })
                        } else {
                            showQualityPicker(info)
                        }
                    }.onFailure {
                        pasteBinding.txtStatus.text = "Error: ${it.message}"
                    }
                }
            }
            return pasteBinding.root
        }
    }

    inner class DownloadsFragment : androidx.fragment.app.Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            downloadsBinding = FragmentDownloadsBinding.inflate(inflater, container, false)
            downloadAdapter = DownloadProgressAdapter()
            downloadsBinding.recyclerDownloads.layoutManager = GridLayoutManager(requireContext(), 1)
            downloadsBinding.recyclerDownloads.adapter = downloadAdapter
            return downloadsBinding.root
        }
    }

    inner class SettingsFragment : androidx.fragment.app.Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            settingsBinding = FragmentSettingsBinding.inflate(inflater, container, false)

            // Dark mode switch
            val prefs = requireContext().getSharedPreferences("app_prefs", MODE_PRIVATE)
            settingsBinding.switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)
            settingsBinding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("dark_mode", isChecked).apply()
                if (isChecked) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }

            // Update yt-dlp
            settingsBinding.btnUpdateYtdlp.setOnClickListener {
                Toast.makeText(requireContext(), "Updating...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    try {
                        YoutubeDLInitializer.getInstance().updateYoutubeDL()
                        Toast.makeText(requireContext(), "Updated!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            return settingsBinding.root
        }
    }

    private fun showQualityPicker(info: VideoInfo) {
        val dialogBinding = DialogQualityPickerBinding.inflate(layoutInflater)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.txtTitle.text = info.title
        dialogBinding.txtDuration.text = formatDuration(info.duration)

        // Load thumbnail
        // (using Glide - ensure import)

        val videoFormats = info.formats.filter { !it.isAudio }
        val audioFormats = info.formats.filter { it.isAudio }

        var selectedFormat: VideoInfo.Format? = null

        val videoAdapter = QualityAdapter(videoFormats, false) { format ->
            selectedFormat = format
        }
        dialogBinding.recyclerVideoQualities.layoutManager = GridLayoutManager(this, 3)
        dialogBinding.recyclerVideoQualities.adapter = videoAdapter

        val audioAdapter = QualityAdapter(audioFormats, true) { format ->
            selectedFormat = format
        }
        dialogBinding.recyclerAudioQualities.layoutManager = GridLayoutManager(this, 3)
        dialogBinding.recyclerAudioQualities.adapter = audioAdapter

        dialogBinding.btnConfirmDownload.setOnClickListener {
            selectedFormat?.let { format ->
                DownloadManager.addDownload(info, format)
                dialog.dismiss()
                Toast.makeText(this, "Download added to queue", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "Please select a quality", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_about) {
            Toast.makeText(this, "ABDownloader v1.0\nBuilt with yt-dlp", Toast.LENGTH_LONG).show()
        }
        return super.onOptionsItemSelected(item)
    }
}
