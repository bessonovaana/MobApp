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

    private val targetLat = 55.0432326
    private val targetLon = 82.973374


    companion object {
        private const val CHANNEL_ID = "location_tracker_channel"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_SERVER_IP = "SERVER_IP"

        @JvmStatic
        fun startTracking(context: Context, serverIP: String = "192.168.1.125:5500") {
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

        val serverIP = intent?.getStringExtra(EXTRA_SERVER_IP) ?: "192.168.1.125:5500"
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
            .setContentText("Местоположение + сеть")
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


        saveAndSendLocationWithNetworkInfo(location, getRealNetworkInfo())
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

                telephonyManager.allCellInfo?.firstOrNull()?.let{ cellInfo ->
                    val strengthLte = cellInfo.cellSignalStrength
                    val rsrp = strengthLte.dbm

                    if (rsrp != CellInfo.UNAVAILABLE) {
                        networkInfo.put("rsrp", rsrp)
                    } else {
                        networkInfo.put("rsrp", JSONObject.NULL)
                    }
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
                        socket.connect("tcp://192.168.1.125:5500")
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
