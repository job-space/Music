package com.example.music.ui.home

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.music.MusicItem
import com.example.music.PlayerActivity
import com.example.music.R
import com.example.music.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val musicList: MutableList<MusicItem> = mutableListOf()
    private lateinit var musicListLayout: LinearLayout

    private val REQUEST_CODE_PERMISSION = 1
    private var currentPosition: Int = -1

    private val playPauseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            currentPosition = intent?.getIntExtra("currentPosition", -1) ?: -1
            updatePlayButtons(currentPosition)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Getting currentPosition from fragment arguments
        currentPosition = arguments?.getInt("currentPosition", -1) ?: -1

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_PERMISSION
            )

            // a function that checks every second whether permission has been granted
            checkPerm(currentPosition)

        } else {
            initializeMusic(currentPosition)
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.example.music.PLAY_PAUSE_ACTION")
        requireActivity().registerReceiver(playPauseReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        requireActivity().unregisterReceiver(playPauseReceiver)
    }

    private fun initializeMusic(currentPosition: Int) {
        musicListLayout = binding.root.findViewById(R.id.list_musics)

        // Clean up your music list and layout before filling it in again
        musicList.clear()
        musicListLayout.removeAllViews()

        loadMusic()

        for ((index, musicItem) in musicList.withIndex()) {
            val musicView = layoutInflater.inflate(R.layout.list_item_with_obj, musicListLayout, false)

            val viewLayout = musicView.findViewById<LinearLayout>(R.id.layoutView)
            val titleView = musicView.findViewById<TextView>(R.id.title_music)
            val playButton = musicView.findViewById<ImageButton>(R.id.button_play)

            titleView.text = musicItem.title
            playButton.setOnClickListener {
                playMusic(index)
            }

            musicView.setOnClickListener {
                playButton.performClick()
            }

            if (currentPosition == index) {
                playButton.setImageResource(android.R.drawable.ic_media_pause)
                viewLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.current))
            }

            musicListLayout.addView(musicView)
        }
    }

    // to automatically download the music list during the first launch after granting access
    private fun checkPerm(currentPosition: Int) {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED) {
            initializeMusic(currentPosition)
        } else checkPerm(currentPosition)

    }

    private fun updatePlayButtons(currentPosition: Int) {
        for (i in 0 until musicListLayout.childCount) {
            val musicView = musicListLayout.getChildAt(i)
            val playButton = musicView.findViewById<ImageButton>(R.id.button_play)
            val viewLayout = musicView.findViewById<LinearLayout>(R.id.layoutView)

            if (i == currentPosition) {
                viewLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.current))
                playButton.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                viewLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent)) // Colour restoration
                playButton.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    private fun loadMusic() {
        val contentResolver: ContentResolver = requireActivity().contentResolver
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA
        )
        val cursor: Cursor? = contentResolver.query(uri, projection, selection, null, null)

        cursor?.use {
            val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val idIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID)

            while (cursor.moveToNext()) {
                val title = cursor.getString(titleIndex)
                val id = cursor.getLong(idIndex)
                val musicUri = Uri.withAppendedPath(uri, id.toString())

                musicList.add(MusicItem(title, musicUri))
            }
        }
    }

    private fun playMusic(position: Int) {
        val musicItem = musicList[position]

        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("MUSIC_URI", musicItem.uri.toString())
            putExtra("MUSIC_TITLE", musicItem.title)
            putParcelableArrayListExtra("MUSIC_LIST", ArrayList(musicList))
            putExtra("MUSIC_POSITION", position)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeMusic(currentPosition)
            } else {
                // Permission not granted
                // You can display a message to the user
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
