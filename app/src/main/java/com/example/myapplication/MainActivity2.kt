package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
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

    private val songList = listOf(
        R.raw.sidim_s_bobrom to "Sidim s bobrom",
        R.raw.sigma_boy to "Sigma Boy"
    )
    private var songIndex = 0

    @SuppressLint("UnsafeIntentLaunch")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        initViews()
        setupMediaPlayer()
        setupListeners()
        setupVolumeControl()

        val back: Button = findViewById(R.id.button4)
        back.setOnClickListener {
            intent = Intent(this, MenuActivity::class.java)
            startActivity(intent)
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
            val (songResId, title) = songList[index]
            val afd = resources.openRawResourceFd(songResId)

            mediaPlayer.reset()
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer.prepareAsync()

            mediaPlayer.setOnPreparedListener {
                songTitle.text = title
                seekBar.progress = 0
                playMusic()
            }

            afd.close()
        } catch (e: IOException) {
            e.printStackTrace()
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
        songIndex = (songIndex + 1) % songList.size
        playSong(songIndex)
    }

    private fun previousSong() {
        songIndex = if (songIndex == 0) songList.size - 1 else songIndex - 1
        playSong(songIndex)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateSeekBar)
        mediaPlayer.release()
    }
}