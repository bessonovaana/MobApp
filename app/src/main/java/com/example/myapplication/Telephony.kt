package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class Telephony : AppCompatActivity() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var networkText: TextView
    private lateinit var toggleTrackingBtn: Button
    private val PHONE_PERMISSION_REQUEST = 1002
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    private var isTracking = false

    // Для периодического обновления UI
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNetworkDisplay()
            if (isTracking) {
                handler.postDelayed(this, 3000) // Каждые 3 секунды
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telephony) // Создай layout или используй activity_gpsactivity

        networkText = findViewById(R.id.Info) // Аналогично GPSActivity
        val backBtn: Button = findViewById(R.id.backtomenu)
        toggleTrackingBtn = findViewById(R.id.b_take_per) // Переименуй в layout на telephony

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

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
        LocationService.stopTracking(this) // Останавливаем Location тоже
        toggleTrackingBtn.text = "Начать мониторинг сети"
        isTracking = false
        handler.removeCallbacks(updateRunnable)
        networkText.text = "Мониторинг остановлен"
        Toast.makeText(this, "Мониторинг сети остановлен", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun updateNetworkDisplay() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            networkText.text = "Нет разрешения на чтение состояния сети"
            return
        }

        try {
            updateNetworkUI()
        } catch (e: Exception) {
            networkText.text = "Ошибка получения данных сети"
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateNetworkUI() {
        val networkInfo = java.util.HashMap<String, Any>()

        networkInfo["operator"] = telephonyManager.networkOperatorName ?: "N/A"
        networkInfo["simOperator"] = telephonyManager.simOperatorName ?: "N/A"
        networkInfo["networkType"] = telephonyManager.networkType


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            telephonyManager.signalStrength?.let { strength ->
                networkInfo["signalLevel"] = strength.level // 0-4
            }
        }

        // Тип сети
        val networkType = telephonyManager.networkType
        val networkTypeName = when (networkType) {
            android.telephony.TelephonyManager.NETWORK_TYPE_NR -> "5G"
            android.telephony.TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP, android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA,
            android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA, android.telephony.TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            android.telephony.TelephonyManager.NETWORK_TYPE_GSM, android.telephony.TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
            else -> "Unknown($networkType)"
        }

        // Информация о ячейках (безопасно)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            try {
                telephonyManager.allCellInfo?.firstOrNull()?.let { cellInfo ->
                    networkInfo["cellClass"] = cellInfo.javaClass.simpleName ?: "unknown"
                }
            } catch (e: Exception) {
                networkInfo["cellInfo"] = "unavailable"
            }
        }

        val text = """
            Информация о сети:
            Оператор: ${networkInfo["operator"]}
            SIM: ${networkInfo["simOperator"]}
            Тип сети: $networkTypeName (${networkInfo["networkType"]})
            
            Сигнал:
            Уровень: ${networkInfo["signalLevel"] ?: "N/A"} (0-4)
            
            Ячейка:
            Тип: ${networkInfo["cellClass"] ?: "N/A"}
            
        """.trimIndent()

        networkText.text = text
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            startTracking()
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), PHONE_PERMISSION_REQUEST
            )
        }
    }

    private fun startTracking() {
        LocationService.startTracking(this)
        toggleTrackingBtn.text = "Остановить мониторинг"
        isTracking = true
        handler.post(updateRunnable)
        Toast.makeText(this, "Мониторинг сети запущен", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PHONE_PERMISSION_REQUEST && grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startTracking()
        } else {
            Toast.makeText(this, "Разрешения необходимы для мониторинга сети", Toast.LENGTH_LONG).show()
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
