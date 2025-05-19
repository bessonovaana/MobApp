package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

class MainActivity2 : AppCompatActivity() {

    private val REQUEST_READ_STORAGE = 101
    private lateinit var seekBar: SeekBar
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var PlPsBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var backBtn: Button
    private lateinit var songTitle: TextView
    private lateinit var currentTimeView: TextView
    private lateinit var volumeTextView: TextView

    private lateinit var mplayer: MediaPlayer
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val songsList = mutableListOf<Song>()
    private var SongIndex = 0

    data class Song(
        val id: Long,
        val title: String,
        val artist: String,
        val duration: Long,
        val uri: Uri
    )

    private val updateSeekBar = object : Runnable {
        override fun run() {
            updateProgress()
            updateCurrentTime()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        val button_b: Button=findViewById(R.id.button4)

        button_b.setOnClickListener{
            val intent = Intent(this, MenuActivity::class.java)
            startActivity(intent)
        }
        initViews()
        setupAudioManager()
        Permissions()
    }

    private fun initViews() {
        seekBar = findViewById(R.id.seekBar)
        volumeSeekBar = findViewById(R.id.voluem)
        PlPsBtn = findViewById(R.id.b_play)
        nextBtn = findViewById(R.id.next)
        backBtn = findViewById(R.id.Prev)
        songTitle = findViewById(R.id.textView)
        currentTimeView = findViewById(R.id.time)
        volumeTextView = findViewById(R.id.sound)

        PlPsBtn.setOnClickListener { PlPs() }
        nextBtn.setOnClickListener { nextSong() }
        backBtn.setOnClickListener { backSong() }

        seekBar.setOnSeekBarChangeListener(createSeekBarListener())
    }

    private fun setupAudioManager() {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        volumeSeekBar.max = maxVolume
        volumeSeekBar.progress = currentVolume
        updateVolumeText(currentVolume)

        volumeSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                updateVolumeText(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateVolumeText(volume: Int) {
        val percentage = (volume * 100) / volumeSeekBar.max
        volumeTextView.text = "$percentage%"
    }

    private fun createSeekBarListener() = object : OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser && ::mplayer.isInitialized && mplayer.duration > 0) {
                val newPosition = (progress * mplayer.duration) / 100
                mplayer.seekTo(newPosition)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            handler.removeCallbacks(updateSeekBar)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            handler.post(updateSeekBar)
        }
    }

    private fun updateProgress() {
        if (::mplayer.isInitialized && mplayer.isPlaying && mplayer.duration > 0) {
            val progress = (mplayer.currentPosition * 100) / mplayer.duration
            seekBar.progress = progress
        }
    }

    private fun updateCurrentTime() {
        if (::mplayer.isInitialized && mplayer.duration > 0) {
            val currentPos = mplayer.currentPosition
            currentTimeView.text = formatTime(currentPos)
        }
    }

    private fun formatTime(millis: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis.toLong()) -
                TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun Permissions() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                Toast.makeText(this, "Для воспроизведения музыки нужно разрешение на чтение файлов",
                    Toast.LENGTH_LONG).show()
            }
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_READ_STORAGE)
        } else {
            loadSongs()
            setupMedia()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_STORAGE && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadSongs()
                setupMedia()
            } else {
                Toast.makeText(this, "Для воспроизведения музыки нужно разрешение",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun loadSongs() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            songsList.clear()
            while (cursor.moveToNext()) {
                songsList.add(
                    Song(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                        Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(0).toString()
                        )
                    )
                )
            }
        }

        if (songsList.isEmpty()) {
            Toast.makeText(this, "Нет музыки для воспроизведения", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupMedia() {
        mplayer = MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setOnPreparedListener {
                PlPsBtn.isEnabled = true
                playMusic()
            }
            setOnCompletionListener {
                nextSong()
            }
        }
        playSong(SongIndex)
    }

    private fun playSong(index: Int) {
        try {
            mplayer.reset()
            mplayer.setDataSource(this, songsList[index].uri)
            mplayer.prepareAsync()

            with(songsList[index]) {
                songTitle.text = "$title - $artist"
            }
            seekBar.progress = 0
            PlPsBtn.isEnabled = false
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка воспроизведения: ${e.localizedMessage}",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun PlPs() {
        if (mplayer.isPlaying) pauseMusic() else playMusic()
    }

    private fun playMusic() {
        mplayer.start()
        PlPsBtn.text = "Pause"
        handler.post(updateSeekBar)
    }

    private fun pauseMusic() {
        mplayer.pause()
        PlPsBtn.text = "Play"
        handler.removeCallbacks(updateSeekBar)
        if (::mplayer.isInitialized && mplayer.isPlaying) {
            mplayer.pause()
        }
    }

    private fun nextSong() {
        SongIndex = (SongIndex + 1) % songsList.size
        playSong(SongIndex)
    }

    private fun backSong() {
        SongIndex = if (SongIndex == 0) songsList.size - 1 else SongIndex - 1
        playSong(SongIndex)
    }
    override fun onPause() {
        super.onPause()
        // Пауза при сворачивании приложения
        if (::mplayer.isInitialized && mplayer.isPlaying) {
            mplayer.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        // Возобновление при возврате в приложение (если нужно)
        if (::mplayer.isInitialized && !mplayer.isPlaying && PlPsBtn.text == "Pause") {
            mplayer.start()
            handler.post(updateSeekBar)
        }
    }

    override fun onStop() {
        super.onStop()
        // Остановка при выходе из приложения
        if (::mplayer.isInitialized) {
            mplayer.stop()
            handler.removeCallbacks(updateSeekBar)
        }
    }
    override fun onDestroy() {
        handler.removeCallbacks(updateSeekBar)
        if (::mplayer.isInitialized) mplayer.release()
        super.onDestroy()
    }
}