package com.abdownloader.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a video (or playlist item) fetched from YouTube or other platforms.
 */
@Parcelize
data class VideoInfo(
    val url: String,                    // Original URL
    var title: String = "",             // Video title (fetched from yt-dlp)
    var thumbnail: String = "",         // Thumbnail URL
    var duration: Long = 0,             // Duration in seconds
    var formats: List<Format> = emptyList(), // Available video/audio formats
    var isPlaylistItem: Boolean = false // True if part of a playlist
) : Parcelable {

    /**
     * Represents a single downloadable format (video quality or audio bitrate).
     */
    @Parcelize
    data class Format(
        val label: String,              // e.g., "1080p", "720p", "192kbps"
        val formatId: String,           // Internal yt-dlp format code (e.g., "137+140")
        val isAudio: Boolean = false    // True if this is an audio-only format
    ) : Parcelable

    /**
     * Helper to get the best video format ID (fallback if user doesn't choose).
     */
    fun getBestVideoFormatId(): String? {
        return formats.firstOrNull { !it.isAudio }?.formatId
    }

    /**
     * Helper to get the best audio format ID.
     */
    fun getBestAudioFormatId(): String? {
        return formats.firstOrNull { it.isAudio }?.formatId
    }
}
