package com.example.myapplication

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity2 : AppCompatActivity() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var songTitle: TextView
    private lateinit var volumeSeekBar: SeekBar
    private var currentVolume = 1.0f
    private lateinit var volumeTextView: TextView
    private lateinit var currentTimeTextView: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val updateSeekBar = object : Runnable {
        override fun run() {
            updateProgress()
            updateCurrentTime()
            handler.postDelayed(this, 1000)
        }
    }

    private val songList = mutableListOf<Song>()
    private var songIndex = 0

    data class Song(
        val id: Long,
        val title: String,
        val artist: String,
        val duration: Long,
        val uri: Uri
    )

    @SuppressLint("UnsafeIntentLaunch")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        initViews()
        loadSongsFromStorage()
        if (songList.isNotEmpty()) {
            setupMediaPlayer()
            setupListeners()
            setupVolumeControl()
        } else {
            Toast.makeText(this, "No music found on device", Toast.LENGTH_LONG).show()
        }

        val back: Button = findViewById(R.id.button4)
        back.setOnClickListener {
            intent = Intent(this, MenuActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadSongsFromStorage() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.TITLE + " ASC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val title = it.getString(titleColumn)
                val artist = it.getString(artistColumn)
                val duration = it.getLong(durationColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                songList.add(Song(id, title, artist, duration, uri))
            }
        }
    }

    private fun setupVolumeControl() {
        volumeSeekBar = findViewById(R.id.voluem)
        volumeSeekBar.max = 100
        volumeSeekBar.progress = (currentVolume * 100).toInt()
        updateVolumeText()

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentVolume = progress / 100f
                setVolume(currentVolume)
                updateVolumeText()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    @SuppressLint("SetTextI18n")
    private fun updateVolumeText() {
        volumeTextView.text = "${(currentVolume * 100).toInt()}%"
    }

    @SuppressLint("SetTextI18n")
    private fun updateCurrentTime() {
        if (::mediaPlayer.isInitialized && mediaPlayer.duration > 0) {
            val currentPosition = mediaPlayer.currentPosition
            val minutes = TimeUnit.MILLISECONDS.toMinutes(currentPosition.toLong())
            val seconds = TimeUnit.MILLISECONDS.toSeconds(currentPosition.toLong()) -
                    TimeUnit.MINUTES.toSeconds(minutes)
            currentTimeTextView.text = String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun setVolume(volume: Float) {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.setVolume(volume, volume)
        }
    }

    private fun updateProgress() {
        if (mediaPlayer.isPlaying && mediaPlayer.duration > 0) {
            val progress = (mediaPlayer.currentPosition * 100) / mediaPlayer.duration
            seekBar.progress = progress
        }
    }

    private fun playSong(index: Int) {
        try {
            val song = songList[index]
            mediaPlayer.reset()
            mediaPlayer.setDataSource(applicationContext, song.uri)
            mediaPlayer.prepareAsync()

            mediaPlayer.setOnPreparedListener {
                songTitle.text = "${song.title} - ${song.artist}"
                seekBar.progress = 0
                playMusic()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error playing song", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initViews() {
        seekBar = findViewById<SeekBar>(R.id.seekBar).apply {
            max = 100
            min = 0
            progress = 0
            setOnSeekBarChangeListener(createSeekBar())
        }

        volumeTextView = findViewById(R.id.sound)
        currentTimeTextView = findViewById(R.id.time)
        playButton = findViewById<Button>(R.id.b_play)
        songTitle = findViewById<TextView>(R.id.textView)
    }

    private fun setupMediaPlayer() {
        mediaPlayer = MediaPlayer()
        playSong(songIndex)
    }

    private fun setupListeners() {
        playButton.setOnClickListener {
            if (mediaPlayer.isPlaying) pauseMusic() else playMusic()
        }

        findViewById<Button>(R.id.next).setOnClickListener { nextSong() }
        findViewById<Button>(R.id.Prev).setOnClickListener { previousSong() }
    }

    private fun createSeekBar() = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser && mediaPlayer.duration > 0) {
                val newPosition = (progress * mediaPlayer.duration) / 100
                mediaPlayer.seekTo(newPosition)
                updateCurrentTime()
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            handler.removeCallbacks(updateSeekBar)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            handler.post(updateSeekBar)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun playMusic() {
        mediaPlayer.start()
        setVolume(currentVolume)
        playButton.text = "Stop"
        handler.post(updateSeekBar)
    }

    @SuppressLint("SetTextI18n")
    private fun pauseMusic() {
        mediaPlayer.pause()
        playButton.text = "Play"
        handler.removeCallbacks(updateSeekBar)
    }

    private fun nextSong() {
        if (songList.isEmpty()) return
        songIndex = (songIndex + 1) % songList.size
        playSong(songIndex)
    }

    private fun previousSong() {
        if (songList.isEmpty()) return
        songIndex = if (songIndex == 0) songList.size - 1 else songIndex - 1
        playSong(songIndex)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateSeekBar)
        mediaPlayer.release()
    }
}