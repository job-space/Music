// MusicBroadcastReceiver.kt
package com.example.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.ImageButton

class MusicBroadcastReceiver(private val playPauseButton: ImageButton) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("ACTION")
        when (action) {
            "PLAY" -> playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            "PAUSE" -> playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            "NEXT" -> {/* Handle next track */}
            "PREVIOUS" -> {/* Handle previous track */}
        }
    }
}
