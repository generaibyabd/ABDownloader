package com.abdownloader.app

/**
 * Represents the current state of a download task.
 */
sealed class DownloadStatus {
    object Idle : DownloadStatus()
    object Queued : DownloadStatus()
    data class Downloading(val progress: Int, val speed: String = "", val eta: String = "") : DownloadStatus()
    data class Finished(val filePath: String) : DownloadStatus()
    data class Failed(val error: String) : DownloadStatus()
}

/**
 * Tracks a specific download task with its VideoInfo and current status.
 */
data class DownloadTask(
    val id: String,                     // Unique ID (usually the URL)
    val videoInfo: VideoInfo,
    val selectedFormat: VideoInfo.Format,
    var status: DownloadStatus = DownloadStatus.Idle,
    var progress: Int = 0               // 0-100
)
