package com.example.music

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.music.ui.home.HomeFragment

class PlayerActivity : AppCompatActivity() {

    private lateinit var playPauseButton: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var musicTitle: TextView
    private var musicList: ArrayList<MusicItem>? = null
    private var currentPosition: Int = 0
    private var accessForSkipToNext: Int = 0


    private var musicService: MyMusicService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MyMusicService.MyMusicBinder
            musicService = binder.getService()
            isBound = true

            // Transferring data to the service
            musicService?.setMusicList(musicList, currentPosition, accessForSkipToNext)

            // Initialising the SeekBar
            seekBar.max = musicService?.getDuration() ?: 0
            musicService?.setSeekBarUpdateListener { position ->
                seekBar.progress = position
            }

            //////////////////////////////////////////////////////////////
            // Initialising the callback to update the song title
            musicService?.setMusicServiceCallback(object : MyMusicService.MusicServiceCallback {
                override fun onTitleUpdated(title: String, position: Int) {
                    musicTitle.text = title
                    currentPosition = position

                }
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    private val seekBarReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val currentPosition = intent?.getIntExtra("CURRENT_POSITION", 0) ?: 0
            val duration = intent?.getIntExtra("DURATION", 0) ?: 0
            seekBar.max = duration
            seekBar.progress = currentPosition
        }
    }

    ////////////// TO SYNCHRONISE THE PAUSE BUTTON WITH THE BACKGROUND MODE ///////////////
    private val playPauseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isPlaying = intent?.getBooleanExtra("IS_PLAYING", false) ?: false
            updatePlayPauseButton(isPlaying)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (isPlaying) {
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    /////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        playPauseButton = findViewById(R.id.play_pause_button)
        seekBar = findViewById(R.id.seekBar)
        musicTitle = findViewById(R.id.music_title)

        // Getting data from Intent
        intent?.let { handleIntent(it) }

        playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        findViewById<ImageButton>(R.id.previous_button).setOnClickListener {
            musicList?.let {
                currentPosition = if (currentPosition > 0) currentPosition - 1 else it.size - 1
                musicTitle.text = it[currentPosition].title // Change the lyrics of a song
                skipToPrevious()
            }
        }

        findViewById<ImageButton>(R.id.next_button).setOnClickListener {
            musicList?.let {
                currentPosition = (currentPosition + 1) % it.size
                musicTitle.text = it[currentPosition].title // Change the lyrics of a song
                skipToNext()
            }
        }


        // Handling changes in SeekBar position
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Connecting with MyMusicService
        Intent(this, MyMusicService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }

        // Register BroadcastReceiver to update SeekBar
        registerReceiver(seekBarReceiver, IntentFilter("ACTION_UPDATE_SEEKBAR"))


        // Registering a BroadcastReceiver to update the status of the Play/Pause button
        registerReceiver(playPauseReceiver, IntentFilter("ACTION_PLAY_PAUSE_UPDATE"))

        val navigationView: ImageButton = findViewById(R.id.main_menu_button)

        navigationView.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("currentPosition", currentPosition)
            }
            startActivity(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    //////////////////////////////////////////////////////////////////////////
    private fun handleIntent(intent: Intent) {
        musicList = intent.getParcelableArrayListExtra("MUSIC_LIST")
        currentPosition = intent.getIntExtra("MUSIC_POSITION", 0)
        musicTitle.text = musicList?.get(currentPosition)?.title

    }

    private fun togglePlayPause() {
        if (musicService?.isPlaying() == true) {
            musicService?.pauseMusic()
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
        } else {
            musicService?.playMusic()
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    private fun skipToNext() {
        musicService?.skipToNext()
        playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun skipToPrevious() {
        musicService?.skipToPrevious()
        playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
    }


    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        unregisterReceiver(seekBarReceiver)
        unregisterReceiver(playPauseReceiver) // TO SYNCHRONISE THE PAUSE BUTTON WITH THE BACKGROUND MODE
    }
}
