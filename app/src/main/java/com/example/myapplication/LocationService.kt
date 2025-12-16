package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.telephony.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.json.JSONObject
import org.zeromq.ZMQ
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var connectivityManager: ConnectivityManager
    private var isTracking = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val WAKELOCK_TIMEOUT = 30 * 60 * 1000L

    // Целевое место и время для установки сигнала
    private val targetLat = 55.0432326  // Целевой latitude
    private val targetLon = 82.973374   // Целевой longitude
    private val targetRadius = 50.0     // Радиус в метрах
    private val targetTimeWindow = 5 * 60 * 1000L  // ±5 минут в миллисекундах
    private val targetTimestamp = 1765817741523L  // Целевое время

    companion object {
        private const val CHANNEL_ID = "location_tracker_channel"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_SERVER_IP = "SERVER_IP"

        @JvmStatic
        fun startTracking(context: Context, serverIP: String = "192.168.137.1:5500") {
            Intent(context, LocationService::class.java).apply {
                putExtra(EXTRA_SERVER_IP, serverIP)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(this)
                } else {
                    context.startService(this)
                }
            }
        }

        @JvmStatic
        fun stopTracking(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        initServices()
        createNotificationChannel()
        createForegroundNotification()
        initWakeLock()
        Log.d("LocationService", "Сервис создан")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isTracking) return START_STICKY

        val serverIP = intent?.getStringExtra(EXTRA_SERVER_IP) ?: "192.168.137.1:5500"
        isTracking = true
        startLocationUpdates()

        Log.d("LocationService", "Трекинг запущен: $serverIP")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private fun initWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LocationService::BackgroundWakeLock"
            ).apply {
                acquire(WAKELOCK_TIMEOUT)
            }
            Log.d("LocationService", "WakeLock активен")
        } catch (e: SecurityException) {
            Log.w("LocationService", "WakeLock недоступен")
            wakeLock = null
        } catch (e: Exception) {
            Log.e("LocationService", "WakeLock ошибка: ${e.message}")
            wakeLock = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновое отслеживание местоположения и сети"
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("NewApi")
    private fun createForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Трекинг активен")
            .setContentText("Местоположение + сеть (цель: $targetLat, $targetLon)")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w("LocationService", "Нет разрешений")
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000)
            .setWaitForAccurateLocation(true)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    handleLocation(location)
                    wakeLock?.acquire(WAKELOCK_TIMEOUT)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    @SuppressLint("MissingPermission")
    private fun handleLocation(location: Location) {
        val currentTime = System.currentTimeMillis()

        if (isAtTargetLocation(location) && isInTargetTimeWindow(currentTime)) {
            Log.d("LocationService", "ЦЕЛЬ ДОСТИГНУТА! Устанавливаем сигнал...")
            val fakeSignalData = createTargetSignalData()
            saveAndSendLocationWithNetworkInfo(location, fakeSignalData)
        } else {
            saveAndSendLocationWithNetworkInfo(location, getRealNetworkInfo())
        }
    }

    private fun isAtTargetLocation(location: Location): Boolean {
        val distance = Location("").apply {
            latitude = targetLat
            longitude = targetLon
        }.distanceTo(location)

        val isClose = distance <= targetRadius
        Log.d("LocationService", "Расстояние до цели: ${"%.1f".format(distance)}м (лимит: $targetRadius)")
        return isClose
    }

    private fun isInTargetTimeWindow(currentTime: Long): Boolean {
        val timeDiff = kotlin.math.abs(currentTime - targetTimestamp)
        val isInWindow = timeDiff <= targetTimeWindow
        Log.d("LocationService", "Временное окно: ${timeDiff/1000}s (лимит: ${targetTimeWindow/1000}s)")
        return isInWindow
    }

    private fun createTargetSignalData(): JSONObject {
        return JSONObject().apply {
            put("operator", "МТС")
            put("networkType", TelephonyManager.NETWORK_TYPE_LTE)
            put("networkTypeName", "4G LTE")
            put("phoneType", TelephonyManager.PHONE_TYPE_GSM)
            put("signalLevel", 4)

            val cells = org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "CellInfoLte")
                    put("identity", JSONObject().apply {
                        put("mcc", "250")
                        put("mnc", "01")
                        put("pci", 123)
                        put("tac", 7890)
                        put("earfcn", 1200)
                    })
                    put("signal", JSONObject().apply {
                        put("rsrp", -75)
                        put("rsrq", -8.5)
                        put("rssi", -55)
                        put("rssnr", 25)
                        put("cqi", 14)
                        put("timingAdvance", 32)
                        put("asuLevel", 30)
                    })
                })
            }
            put("cells", cells)

            put("isWifi", false)
            put("isMobile", true)
            put("isConnected", true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getRealNetworkInfo(): JSONObject {
        val networkInfo = JSONObject()
        try {
            networkInfo.put("operator", telephonyManager.networkOperatorName ?: "N/A")
            networkInfo.put("networkType", telephonyManager.networkType)
            networkInfo.put("phoneType", telephonyManager.phoneType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                telephonyManager.signalStrength?.let { strength ->
                    networkInfo.put("signalLevel", strength.level)
                }

                // Детальная информация о ячейках
                telephonyManager.allCellInfo?.firstOrNull()?.let { cellInfo ->
                    networkInfo.put("cellClass", cellInfo.javaClass.simpleName)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                networkInfo.put("isWifi", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                networkInfo.put("isMobile", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                networkInfo.put("isConnected", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
            }

            val networkType = telephonyManager.networkType
            networkInfo.put("networkTypeName", when (networkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                else -> "Unknown($networkType)"
            })

        } catch (e: Exception) {
            networkInfo.put("error", e.message)
        }
        return networkInfo
    }

    @SuppressLint("MissingPermission")
    private fun saveAndSendLocationWithNetworkInfo(location: Location, networkInfo: JSONObject) {
        thread {
            try {
                val data = JSONObject().apply {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("altitude", if (location.hasAltitude()) location.altitude else null)
                    put("speed", if (location.hasSpeed()) location.speed else null)
                    put("accuracy", if (location.hasAccuracy()) location.accuracy else null)
                    put("time", SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(location.time)))
                    put("timestamp", location.time)
                    put("network", networkInfo)
                }

                val dir = File(getExternalFilesDir(null), "LocationData")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "track_${System.currentTimeMillis()}.json")
                FileWriter(file).use { it.write(data.toString(2)) }

                sendToServer(file.absolutePath)

            } catch (e: Exception) {
                Log.e("LocationService", "Ошибка сохранения: ${e.message}")
            }
        }
    }

    private fun sendToServer(filePath: String) {
        thread {
            try {
                val file = File(filePath)
                if (!file.exists()) return@thread

                ZMQ.context(1).use { context ->
                    context.socket(ZMQ.REQ).use { socket ->
                        socket.setReceiveTimeOut(5000)
                        socket.connect("tcp://192.168.137.1:5500")
                        socket.send(file.readBytes(), 0)
                        socket.recv(0)
                        Log.d("LocationService", "Отправлено: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Отправка ошибка: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isTracking = false
        try {
            fusedLocationClient.removeLocationUpdates(object : LocationCallback() {})
        } catch (e: Exception) {
            Log.w("LocationService", "Не удалось остановить обновления локации")
        }
        wakeLock?.release()
        Log.d("LocationService", "Сервис остановлен")
    }
}
