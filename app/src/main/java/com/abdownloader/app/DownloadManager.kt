package com.abdownloader.app

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.junkfood02.youtubedl.android.YoutubeDL
import io.github.junkfood02.youtubedl.android.YoutubeDLRequest
import io.github.junkfood02.youtubedl.android.YoutubeDLEvent
import io.github.junkfood02.youtubedl.android.YoutubeDLException
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/**
 * Singleton manager for all download operations.
 * Handles:
 * - Fetching video metadata (title, thumbnail, duration, formats)
 * - Listing playlist items
 * - Executing downloads with progress callbacks
 * - Managing the download queue
 */
object DownloadManager {

    private const val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // LiveData to observe the list of all download tasks
    private val _tasks = MutableLiveData<List<DownloadTask>>(emptyList())
    val tasks: LiveData<List<DownloadTask>> = _tasks

    // LiveData to observe a single currently downloading item (for UI updates)
    private val _currentDownload = MutableLiveData<DownloadTask?>(null)
    val currentDownload: LiveData<DownloadTask?> = _currentDownload

    // In-memory map of task ID -> DownloadTask (for fast updates)
    private val taskMap = mutableMapOf<String, DownloadTask>()

    // Queue of pending tasks (processed one by one)
    private val pendingQueue = mutableListOf<DownloadTask>()
    private var isProcessing = false

    /**
     * Fetches video/playlist information from a URL.
     * Returns a VideoInfo object (or list for playlists) without downloading.
     */
    suspend fun fetchVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val yt = YoutubeDLInitializer.getInstance()
            // Extract info (this fetches metadata only, no download)
            val info = yt.extractInfo(url)
            
            if (info == null) {
                return@withContext Result.failure(Exception("No info returned from yt-dlp"))
            }

            // Parse the result. The library returns a Map<String, Any?>.
            val title = info["title"] as? String ?: "Unknown Title"
            val thumbnail = info["thumbnail"] as? String ?: ""
            val duration = (info["duration"] as? Number)?.toLong() ?: 0L
            
            // Check if this is a playlist
            val entries = info["entries"] as? List<*>
            val isPlaylist = entries != null && entries.isNotEmpty()

            // If it's a playlist, we need to extract individual video entries
            // For now, we return a single VideoInfo with isPlaylistItem = true
            // The UI will detect this and fetch playlist items separately.
            
            // Parse available formats
            val formats = parseFormats(info)
            
            val videoInfo = VideoInfo(
                url = url,
                title = title,
                thumbnail = thumbnail,
                duration = duration,
                formats = formats,
                isPlaylistItem = isPlaylist
            )

            Log.d(TAG, "Fetched info: $title, formats: ${formats.size}")
            return@withContext Result.success(videoInfo)

        } catch (e: YoutubeDLException) {
            Log.e(TAG, "YoutubeDL error", e)
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching info", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Fetches all items inside a playlist.
     */
    suspend fun fetchPlaylistItems(url: String): Result<List<VideoInfo>> = withContext(Dispatchers.IO) {
        try {
            val yt = YoutubeDLInitializer.getInstance()
            val info = yt.extractInfo(url)
            
            if (info == null) {
                return@withContext Result.failure(Exception("No info returned"))
            }

            val entries = info["entries"] as? List<*>
            if (entries == null || entries.isEmpty()) {
                return@withContext Result.failure(Exception("No entries found in playlist"))
            }

            val items = mutableListOf<VideoInfo>()
            for (entry in entries) {
                val entryMap = entry as? Map<*, *> ?: continue
                val videoUrl = entryMap["webpage_url"] as? String 
                    ?: entryMap["url"] as? String 
                    ?: "https://youtube.com/watch?v=${entryMap["id"]}"
                val title = entryMap["title"] as? String ?: "Unknown Title"
                val thumbnail = entryMap["thumbnail"] as? String ?: ""
                val duration = (entryMap["duration"] as? Number)?.toLong() ?: 0L
                
                // Extract formats for this item (optional, we can fetch per-item later)
                val formats = parseFormats(entryMap)
                
                val videoInfo = VideoInfo(
                    url = videoUrl,
                    title = title,
                    thumbnail = thumbnail,
                    duration = duration,
                    formats = formats,
                    isPlaylistItem = true
                )
                items.add(videoInfo)
            }

            Log.d(TAG, "Fetched ${items.size} playlist items")
            return@withContext Result.success(items)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Adds a download task to the queue.
     */
    fun addDownload(
        videoInfo: VideoInfo,
        format: VideoInfo.Format,
        outputFileName: String = ""
    ) {
        val id = UUID.randomUUID().toString()
        val task = DownloadTask(
            id = id,
            videoInfo = videoInfo,
            selectedFormat = format,
            status = DownloadStatus.Queued,
            progress = 0
        )
        
        // Add to map and list
        taskMap[id] = task
        updateTaskList()

        // Add to queue
        pendingQueue.add(task)
        Log.d(TAG, "Added to queue: ${videoInfo.title} (${format.label})")
        
        // Start processing if not already
        if (!isProcessing) {
            processQueue()
        }
    }

    /**
     * Removes a finished/failed task from the list (optional).
     */
    fun removeTask(taskId: String) {
        taskMap.remove(taskId)
        pendingQueue.removeAll { it.id == taskId }
        updateTaskList()
    }

    /**
     * Processes the queue one by one.
     */
    private fun processQueue() {
        if (pendingQueue.isEmpty()) {
            isProcessing = false
            _currentDownload.postValue(null)
            return
        }

        isProcessing = true
        val task = pendingQueue.first()
        _currentDownload.postValue(task)
        
        // Update status
        task.status = DownloadStatus.Downloading(0)
        updateTaskList()

        // Execute download
        scope.launch {
            executeDownload(task)
            // After this, remove from queue and continue
            pendingQueue.remove(task)
            updateTaskList()
            processQueue() // Process next
        }
    }

    /**
     * Executes a single download using yt-dlp.
     */
    private suspend fun executeDownload(task: DownloadTask) = withContext(Dispatchers.IO) {
        try {
            val yt = YoutubeDLInitializer.getInstance()
            val context = AppGlobals.context ?: return@withContext

            // Build output path
            val fileName = if (task.videoInfo.title.isNotEmpty()) {
                task.videoInfo.title.replace(Regex("[^a-zA-Z0-9 ]"), "").trim() + ".mp4"
            } else {
                "download_${System.currentTimeMillis()}.mp4"
            }
            
            // Save to Downloads folder
            val downloadDir = context.getExternalFilesDir(null) ?: context.filesDir
            val outputPath = File(downloadDir, fileName).absolutePath

            // Build request
            val request = YoutubeDLRequest(task.videoInfo.url, outputPath)
            
            // Set format
            request.setOption("-f", task.selectedFormat.formatId)
            
            // Optional: Mimic Android client to avoid YouTube blocks
            request.setOption("--extractor-args", "youtube:player_client=android")
            
            // Use aria2c for faster downloads (if available)
            // request.setOption("--downloader", "aria2c")

            Log.d(TAG, "Starting download: ${task.videoInfo.title} -> $outputPath")

            // Execute with callback
            yt.execute(request, object : YoutubeDL.Callback {
                override fun onSuccess() {
                    // Download finished
                    task.status = DownloadStatus.Finished(outputPath)
                    task.progress = 100
                    updateTaskList()
                    _currentDownload.postValue(null)
                    Log.d(TAG, "Download finished: $outputPath")
                }

                override fun onError(error: YoutubeDLException) {
                    // Download failed
                    task.status = DownloadStatus.Failed(error.message ?: "Unknown error")
                    updateTaskList()
                    _currentDownload.postValue(null)
                    Log.e(TAG, "Download failed", error)
                }

                override fun onEvent(event: YoutubeDLEvent) {
                    when (event) {
                        is YoutubeDLEvent.Progress -> {
                            // Update progress
                            val percent = event.percent
                            task.progress = percent.toInt()
                            val speed = event.speed ?: ""
                            val eta = event.eta ?: ""
                            task.status = DownloadStatus.Downloading(percent.toInt(), speed, eta)
                            updateTaskList()
                            _currentDownload.postValue(task)
                            Log.d(TAG, "Progress: $percent% - Speed: $speed - ETA: $eta")
                        }
                        else -> {
                            // Other events (info, warning, etc.)
                            Log.d(TAG, "Event: $event")
                        }
                    }
                }
            })

        } catch (e: Exception) {
            task.status = DownloadStatus.Failed(e.message ?: "Exception occurred")
            updateTaskList()
            _currentDownload.postValue(null)
            Log.e(TAG, "Download exception", e)
        }
    }

    /**
     * Parses formats from the extracted info map.
     */
    private fun parseFormats(info: Map<*, *>): List<VideoInfo.Format> {
        val formatsList = mutableListOf<VideoInfo.Format>()
        
        val formats = info["formats"] as? List<*> ?: return emptyList()
        
        for (item in formats) {
            val formatMap = item as? Map<*, *> ?: continue
            val formatId = formatMap["format_id"]?.toString() ?: continue
            val resolution = formatMap["resolution"]?.toString() ?: ""
            val acodec = formatMap["acodec"]?.toString() ?: "none"
            val vcodec = formatMap["vcodec"]?.toString() ?: "none"
            
            // Determine if audio-only or video
            val isAudioOnly = vcodec == "none"
            val isVideoOnly = acodec == "none" || acodec == "none? unknown"
            
            // Skip if both are missing (should not happen)
            if (isAudioOnly && isVideoOnly) continue
            
            // For audio formats
            if (isAudioOnly) {
                val bitrate = formatMap["abr"] as? Number
                val label = if (bitrate != null) "${bitrate.toInt()}kbps" else "Audio"
                formatsList.add(VideoInfo.Format(label, formatId, isAudio = true))
            } else {
                // For video formats
                val height = formatMap["height"] as? Number
                val label = if (height != null) "${height}p" else resolution
                formatsList.add(VideoInfo.Format(label, formatId, isAudio = false))
            }
        }
        
        // Deduplicate and sort
        return formatsList.distinctBy { it.formatId }
            .sortedWith(compareByDescending<VideoInfo.Format> { !it.isAudio }.thenByDescending { it.label })
    }

    /**
     * Updates the LiveData list with the current task map values.
     */
    private fun updateTaskList() {
        _tasks.postValue(taskMap.values.toList())
    }
}

/**
 * Global application context holder (used by DownloadManager).
 * We'll set this in MainActivity onCreate.
 */
object AppGlobals {
    var context: Context? = null
}
