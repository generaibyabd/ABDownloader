package com.abdownloader.app

import android.content.Context
import android.util.Log
import io.github.junkfood02.youtubedl.android.YoutubeDL
import io.github.junkfood02.youtubedl.android.YoutubeDLException

/**
 * Singleton that handles initialization of the yt-dlp library.
 * This must be called ONCE in the Application class or MainActivity.
 */
object YoutubeDLInitializer {

    private const val TAG = "YoutubeDLInit"
    private var isInitialized = false

    /**
     * Initialize the library. Call this in your Application's onCreate() or MainActivity's onCreate().
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }

        try {
            // This loads native libraries and extracts Python runtime
            YoutubeDL.getInstance().init(context)
            
            // Optional: Set a custom user-agent to mimic Chrome (helps bypass blocks)
            // We'll apply this per-request instead, but we can set global defaults here if needed.
            
            isInitialized = true
            Log.d(TAG, "yt-dlp initialized successfully!")
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "Failed to initialize yt-dlp", e)
            // You can show a Toast to the user here
        }
    }

    /**
     * Returns the singleton instance of YoutubeDL (after initialization).
     * Throws if not initialized.
     */
    fun getInstance(): YoutubeDL {
        if (!isInitialized) {
            throw IllegalStateException("YoutubeDL not initialized. Call initialize() first.")
        }
        return YoutubeDL.getInstance()
    }

    /**
     * Checks if the library is ready.
     */
    fun isReady(): Boolean = isInitialized
}
