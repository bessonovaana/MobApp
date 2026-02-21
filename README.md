# Android Мониторинг Геолокации и Сети

Мобильное приложение для отслеживания GPS, характеристик сети (RSRP, тип LTE/5G) и передачи данных на ПК-сервер.

## Функции
- **GPSActivity**: UI для просмотра координат/высоты, управление LocationService [https://github.com/bessonovaana/MobApp/blob/master/app/src/main/java/com/example/myapplication/GPSActivity.kt]
- **Telephony**: Отображение силы сигнала, типа сети (TelephonyManager.networkType) [https://github.com/bessonovaana/MobApp/blob/master/app/src/main/java/com/example/myapplication/Telephony.kt]
- **Socket**: Соединение с сервером (ZMQ/TCP, порт 5000/5500) [https://github.com/bessonovaana/MobApp/blob/master/app/src/main/java/com/example/myapplication/Socket.kt]
- **LocationService**: Фоновая работа (START_STICKY, WakeLock), JSON с данными каждые 1-10 мин [https://github.com/bessonovaana/MobApp/blob/master/app/src/main/java/com/example/myapplication/LocationService.kt]
- **MainActivity/MainActivity2**: Калькулятор/плеер 
- **MenuActivity**: Навигация

## Архитектура
7 Activity + ForegroundService. Данные: GPS (FusedLocationProviderClient), сеть (allCellInfo.dbm), JSON → сокет на ipaddress:5500.
Сервер: отдельный репозиторий (Python ZMQ).[https://github.com/bessonovaana/socet.git]

## Установка
1. minSdk 23+, Google Play Services
2. Разрешения: ACCESS_FINE_LOCATION, READ_PHONE_STATE, INTERNET, FOREGROUND_SERVICE_LOCATION
3. Запуск: MenuActivity → GPS/Telephony → Start Tracking

## Сборка
- Android Studio
- build.gradle: play-services-location, jerozmq
