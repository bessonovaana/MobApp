package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Environment
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
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
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
            put("speed", location.speed)
            put("accuracy", location.accuracy)
        }
    }

    private fun saveLocationToJson(locationData: JSONObject) {
        try {
            val dir = File(Environment.getExternalStorageDirectory(), "LocationData")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val file = File(dir, "location_${System.currentTimeMillis()}.json")
            FileWriter(file).use { writer ->
                writer.write(locationData.toString())
                Toast.makeText(this, "Данные сохранены: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
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
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCurrentLocation()
                } else {
                    Toast.makeText(
                        this,
                        "Для работы приложения необходимы разрешения на доступ к местоположению",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}