package com.example.myapplication

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Runnable
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class GPSActivity : AppCompatActivity() {

    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationText: TextView
    private lateinit var toggleTrackingBtn: Button
    private lateinit var mainLayout: ConstraintLayout

    var backgroundColorIndex = 0
    var buttonColorIndex = 0

    private val LOCATION_PERMISSION_REQUEST = 1001
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA)

    var lastMovingTime = System.currentTimeMillis()

    private var isTracking = false
    private val handler = Handler(Looper.getMainLooper())

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1000L
    ).apply {
        setMinUpdateIntervalMillis(100)
        setWaitForAccurateLocation(true)
    }.build()

    private val locationCallback = object : com.google.android.gms.location.LocationCallback() {
        override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
            super.onLocationResult(locationResult)
            locationResult.lastLocation?.let { location ->
                updateLocationUI(location)
                saveLocationToJson(formatLocationData(location))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gpsactivity)

        locationText = findViewById(R.id.textView2)
        val backBtn: Button = findViewById(R.id.b)
        toggleTrackingBtn = findViewById(R.id.gps)
        mainLayout = findViewById(R.id.main)

        locationClient = LocationServices.getFusedLocationProviderClient(this)

        backBtn.setOnClickListener {
            stopLocationUpdates()
            startActivity(Intent(this, MenuActivity::class.java))
            finish()
        }

        toggleTrackingBtn.setOnClickListener {
            if (isTracking) {
                stopLocationUpdates()
                toggleTrackingBtn.text = "Начать трекинг"

            } else {
                checkPermissionsAndGetLocation()


            }

        }
        toggleTrackingBtn.post(object : Runnable {
            override fun run() {
                handler.postDelayed(this, 100)
            }
        })

    }




    private fun checkPermissionsAndGetLocation() {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        when {
            missingPermissions.isEmpty() -> {
                startLocationUpdates()
                toggleTrackingBtn.text = "Остановить трекинг"
            }
            missingPermissions.any { perm ->
                ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
            } -> {
                showPermissionExplanationDialog()
            }
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toTypedArray(),
                    LOCATION_PERMISSION_REQUEST
                )
            }
        }
    }

    private fun startLocationUpdates() {
        if (!isTracking) {
            isTracking = true
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
                Toast.makeText(this, "Трекинг начат", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopLocationUpdates() {
        if (isTracking) {
            isTracking = false
            locationClient.removeLocationUpdates(locationCallback)
            Toast.makeText(this, "Трекинг остановлен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLocationUI(location: Location) {
        locationText.text = """
            Широта: ${"%.6f".format(location.latitude)}
            Долгота: ${"%.6f".format(location.longitude)}
            Высота: ${"%.1f".format(location.altitude)} м
            Время: ${dateFormat.format(Date(location.time))}
        """.trimIndent()
    }

    private fun formatLocationData(location: Location): JSONObject {
        return JSONObject().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("altitude", location.altitude)
            put("time", dateFormat.format(Date(location.time)))
            put("timestamp", System.currentTimeMillis())
        }
    }

    private fun saveLocationToJson(locationData: JSONObject) {
        try {
            val dir = File(getExternalFilesDir(null), "LocationData")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val file = File(dir, "location_${System.currentTimeMillis()}.json")
            FileWriter(file).use { writer ->
                writer.write(locationData.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Ошибка сохранения: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Необходимы разрешения")
            .setMessage("Для работы трекинга нужны:\n\n- Доступ к местоположению\n- Сохранение файлов")
            .setPositiveButton("Запросить") { _, _ ->
                requestMissingPermissions()
            }
            .setNegativeButton("Отмена") { _, _ ->
                toggleTrackingBtn.text = "Начать трекинг"
            }
            .show()
    }

    private fun requestMissingPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions,
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

                if (allGranted) {
                    startLocationUpdates()
                    toggleTrackingBtn.text = "Остановить трекинг"
                } else {
                    val shouldShowRationale = permissions.any {
                        ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                    }

                    if (!shouldShowRationale) {
                        showSettingsRedirectDialog()
                    } else {
                        Toast.makeText(
                            this,
                            "Не все разрешения предоставлены",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun showSettingsRedirectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Разрешения отклонены")
            .setMessage("Вы запретили запрос разрешений. Открыть настройки?")
            .setPositiveButton("Настройки") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            stopLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        handler.removeCallbacksAndMessages(null)
    }
}