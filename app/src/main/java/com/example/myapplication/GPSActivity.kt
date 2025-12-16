package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.*

class GPSActivity : AppCompatActivity() {

    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationText: TextView
    private lateinit var toggleTrackingBtn: Button
    private val LOCATION_PERMISSION_REQUEST = 1001
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    private var isTracking = false

    // Для периодического обновления UI
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateLocationDisplay()
            if (isTracking) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gpsactivity)

        locationText = findViewById(R.id.textView2)
        val backBtn: Button = findViewById(R.id.b)
        toggleTrackingBtn = findViewById(R.id.gps)

        locationClient = LocationServices.getFusedLocationProviderClient(this)

        backBtn.setOnClickListener {
            stopTracking()
            startActivity(Intent(this, MenuActivity::class.java))
            finish()
        }

        toggleTrackingBtn.setOnClickListener {
            if (isTracking) {
                stopTracking()
            } else {
                checkPermissions()
            }
        }
    }

    private fun stopTracking() {
        LocationService.stopTracking(this)
        toggleTrackingBtn.text = "Начать трекинг"
        isTracking = false
        handler.removeCallbacks(updateRunnable)
        locationText.text = "Трекинг остановлен"
        Toast.makeText(this, "Трекинг остановлен", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationDisplay() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            locationText.text = "Нет разрешения на локацию"
            return
        }

        locationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    updateLocationUI(location)
                } else {
                    locationText.text = "Ожидание локации..."
                }
            }
            .addOnFailureListener {
                locationText.text = "Ошибка получения локации"
            }
    }

    private fun updateLocationUI(location: Location) {
        locationText.text = """
            Широта: ${"%.6f".format(location.latitude)}
            Долгота: ${"%.6f".format(location.longitude)}
            Высота: ${if (location.hasAltitude()) "${"%.1f".format(location.altitude)} м" else "N/A"}
            Время: ${dateFormat.format(Date(location.time))}
            
        """.trimIndent()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            startTracking()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        }
    }

    private fun startTracking() {
        LocationService.startTracking(this)
        toggleTrackingBtn.text = "Остановить трекинг"
        isTracking = true
        handler.post(updateRunnable)
        Toast.makeText(this, "Трекинг запущен", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startTracking()
        } else {
            Toast.makeText(this, "Разрешение необходимо для трекинга", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isTracking) {
            handler.post(updateRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        if (isTracking) {
            LocationService.stopTracking(this)
        }
    }
}
