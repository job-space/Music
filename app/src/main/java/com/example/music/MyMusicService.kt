package com.example.music

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MyMusicService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler()

    private var musicList: List<MusicItem>? = null
    private var currentPosition: Int = 0
    private val CHANNEL_ID = "MyMusicServiceChannel"
    private val binder = MyMusicBinder()
    private var seekBarUpdateListener: ((Int) -> Unit)? = null
    private var accessForSkipToNext: Int = 0


    private lateinit var musicTitle: TextView

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    val currentPosition = it.currentPosition
                    seekBarUpdateListener?.invoke(currentPosition)
                    sendSeekBarUpdateBroadcast(currentPosition)
                    handler.postDelayed(this, 1000) // We update every second
                }
            }
        }
    }


    inner class MyMusicBinder : Binder() {
        fun getService(): MyMusicService = this@MyMusicService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnCompletionListener {
                if (accessForSkipToNext > 0) {
                    skipToNext() // is responsible for the continuity of the playlist


                }
            }

        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PLAY" -> playMusic()
            "ACTION_PAUSE" -> pauseMusic()
            "ACTION_NEXT" -> skipToNext()
            "ACTION_PREVIOUS" -> skipToPrevious()
        }
        return START_NOT_STICKY
    }

    fun setMusicList(musicList: List<MusicItem>?, position: Int, accessForSkipToNext: Int) {
        this.musicList = musicList
        this.currentPosition = position
        this.accessForSkipToNext = accessForSkipToNext
        startMusic()
    }

    private fun startMusic() {
        musicList?.getOrNull(currentPosition)?.let { musicItem ->
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(this, musicItem.uri)
            mediaPlayer?.prepareAsync()
            mediaPlayer?.setOnPreparedListener {
                mediaPlayer?.start()
                showNotification(musicItem.title, isPlaying = true)
                startSeekBarUpdate()
                notifyTitleUpdate() // Update the song title after it starts
                accessForSkipToNext++
            }
        }
    }

    fun isPlaying(): Boolean = mediaPlayer!!.isPlaying

    fun playMusic() {
        mediaPlayer?.start()
        showNotification(musicList?.get(currentPosition)?.title ?: "", isPlaying = true)
        startSeekBarUpdate()
        sendPlayPauseBroadcast(true)  // TO SYNCHRONISE THE PAUSE BUTTON WITH THE BACKGROUND MODE
    }

    fun pauseMusic() {
        mediaPlayer?.pause()
        showNotification(musicList?.get(currentPosition)?.title ?: "", isPlaying = false)
        stopSeekBarUpdate()
        sendPlayPauseBroadcast(false)  // TO SYNCHRONISE THE PAUSE BUTTON WITH THE BACKGROUND MODE
    }

    // TO SYNCHRONISE THE PAUSE BUTTON WITH THE BACKGROUND MODE
    private fun sendPlayPauseBroadcast(isPlaying: Boolean) {
        val intent = Intent("ACTION_PLAY_PAUSE_UPDATE")
        intent.putExtra("IS_PLAYING", isPlaying)
        sendBroadcast(intent)
    }

    fun skipToNext() {
        musicList?.let {
            currentPosition = (currentPosition + 1) % it.size
            startMusic()
        }
    }

    fun skipToPrevious() {
        musicList?.let {
            currentPosition = if (currentPosition > 0) currentPosition - 1 else it.size - 1
            startMusic()
        }
    }

    //////////////////////////////////////////////// a function for displaying the correct name of the next song after it ends ////////
    fun notifyTitleUpdate() {
        val musicTitleValue = musicList?.get(currentPosition)?.title ?: return
        musicServiceCallback?.onTitleUpdated(musicTitleValue, currentPosition)
    }

    interface MusicServiceCallback {
        fun onTitleUpdated(title: String, position: Int)
    }

    private var musicServiceCallback: MusicServiceCallback? = null

    fun setMusicServiceCallback(callback: MusicServiceCallback) {
        this.musicServiceCallback = callback
    }


    fun getDuration(): Int = mediaPlayer!!.duration


    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }


    /////////////////////////////////////////////////////////////////////////////////////////////////

    private fun showNotification(title: String, isPlaying: Boolean) {
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                getPendingIntent("ACTION_PAUSE")
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                getPendingIntent("ACTION_PLAY")
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Playing Music")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "Previous",
                getPendingIntent("ACTION_PREVIOUS")
            ))
            .addAction(playPauseAction)
            .addAction(NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "Next",
                getPendingIntent("ACTION_NEXT")
            ))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }






    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MyMusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for Music Service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun startSeekBarUpdate() {
        handler.post(updateSeekBarRunnable)
    }

    private fun stopSeekBarUpdate() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun sendSeekBarUpdateBroadcast(position: Int) {
        Intent("ACTION_UPDATE_SEEKBAR").apply {
            putExtra("CURRENT_POSITION", position)
            putExtra("DURATION", mediaPlayer?.duration ?: 0)
            sendBroadcast(this)
        }
    }


    fun setSeekBarUpdateListener(listener: (Int) -> Unit) {
        this.seekBarUpdateListener = listener
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
    }

}
