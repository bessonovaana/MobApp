package com.example.myapplication

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class GPSActivity : AppCompatActivity() {

    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationText: TextView
    private val LOCATION_PERMISSION_REQUEST = 1001
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gpsactivity)

        locationText = findViewById(R.id.textView2)
        val backBtn: Button = findViewById(R.id.b)
        val getLocationBtn: Button = findViewById(R.id.gps)

        locationClient = LocationServices.getFusedLocationProviderClient(this)

        backBtn.setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
            finish()
        }

        getLocationBtn.setOnClickListener {
            checkPermissionsAndGetLocation()
        }
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
                // Все разрешения есть
                getCurrentLocation()
            }
            missingPermissions.any { perm ->
                ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
            } -> {
                // Показываем объяснение перед запросом
                showPermissionExplanationDialog()
            }
            else -> {
                // Запрашиваем разрешения
                ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toTypedArray(),
                    LOCATION_PERMISSION_REQUEST
                )
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Необходимы разрешения")
            .setMessage("Приложению нужны разрешения для:\n\n- Доступа к вашему местоположению\n- Сохранения данных в файл")
            .setPositiveButton("Понятно") { _, _ ->
                requestMissingPermissions()
            }
            .setNegativeButton("Отмена", null)
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

        ActivityCompat.requestPermissions(
            this,
            missingPermissions,
            LOCATION_PERMISSION_REQUEST
        )
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        locationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val locationData = formatLocationData(location)

                locationText.text = """
                    Широта: ${location.latitude}
                    Долгота: ${location.longitude}
                    Высота: ${location.altitude} м
                    Время: ${dateFormat.format(Date(location.time))}
                """.trimIndent()

                saveLocationToJson(locationData)
            } else {
                locationText.text = "Не удалось получить местоположение"
                Toast.makeText(this, "Местоположение недоступно", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatLocationData(location: Location): JSONObject {
        return JSONObject().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("altitude", location.altitude)
            put("time", dateFormat.format(Date(location.time)))
            put("accuracy", location.accuracy)
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
                Toast.makeText(this, "Данные сохранены в: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка сохранения: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
                    getCurrentLocation()
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
            .setTitle("Требуются разрешения")
            .setMessage("Вы запретили запрос разрешений. Хотите открыть настройки, чтобы предоставить их?")
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
}