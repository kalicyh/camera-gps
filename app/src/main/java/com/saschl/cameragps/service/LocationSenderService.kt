package com.saschl.cameragps.service

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.saschl.cameragps.notification.NotificationsHelper
import timber.log.Timber
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.experimental.and
import kotlin.experimental.or

class LocationSenderService : Service() {

    private var shouldSendTimeZoneAndDst: Boolean = true
    private var startId: Int = 0
    private val binder = LocalBinder()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var gatt1: BluetoothGatt? = null

    private var characteristic: BluetoothGattCharacteristic? = null

    private var locationResultVar: Location = Location("")

    private var startedManually: Boolean = false

    private var shutdownExecutor: ScheduledExecutorService? = null
    private var shutdownFuture: ScheduledFuture<*>? = null

    private var isShutdownRequested = false

    private val handlerThread = HandlerThread("LocationSenderServiceThread")

    companion object {

        // Random UUID for our service known between the client and server to allow communication
        val SERVICE_UUID: UUID = UUID.fromString("8000dd00-dd00-ffff-ffff-ffffffffffff")

        // Same as the service but for the characteristic
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000dd11-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_READ_UUID: UUID = UUID.fromString("0000dd21-0000-1000-8000-00805f9b34fb")

        val CHARACTERISTIC_ENABLE_GPS_COMMAND: UUID =
            UUID.fromString("0000dd30-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_ENABLED_GPS_COMMAND: UUID =
            UUID.fromString("0000dd31-0000-1000-8000-00805f9b34fb")


        // Actions for controlling the service
        const val ACTION_REQUEST_SHUTDOWN = "com.saschl.cameragps.ACTION_REQUEST_SHUTDOWN"
        const val ACTION_NORMAL_START = "com.saschl.cameragps.ACTION_NORMAL_START"

        // Shutdown delay in milliseconds (1 minute)
        private const val SHUTDOWN_DELAY_MS = 60 * 1000L
    }

    private val bluetoothManager: BluetoothManager by lazy {
        applicationContext.getSystemService()!!
    }


    inner class LocalBinder : Binder() {
        fun getService(): LocationSenderService = this@LocationSenderService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            super.onConnectionStateChange(gatt, status, newState)

            // state = state.copy(gatt = gatt, connectionState = newState)
            //  currentOnStateChange(state)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("An error happened: $status")
                fusedLocationClient.removeLocationUpdates(locationCallback)

                //BlePresenceScanner.start(applicationContext)
                if (startedManually && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    stopSelf()
                }
            } else {
                Timber.i("Connected to device %d", status)
                cancelShutdown()
                //  gatt.requestMtu(158)
                gatt.discoverServices()
            }
        }


        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            //      state = state.copy(services = gatt.services)
            //     currentOnStateChange(state)
            val service = gatt.services?.find { it.uuid == SERVICE_UUID }

            // If the GATTServerSample service is found, get the characteristic
            characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)

            val readCharacteristic = service?.getCharacteristic(CHARACTERISTIC_READ_UUID)

            //enable GPS if needed
            val gpsEnableCharacteristic = service?.getCharacteristic(CHARACTERISTIC_ENABLE_GPS_COMMAND)
            if(gpsEnableCharacteristic != null) {

                    Timber.i("Enabling GPS characteristic: ${gpsEnableCharacteristic.uuid}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(
                            gpsEnableCharacteristic,
                            byteArrayOf(0x01),
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        )
                    } else {
                        gpsEnableCharacteristic.value = byteArrayOf(0x01)
                        gatt.writeCharacteristic(gpsEnableCharacteristic)
                    }
            } else {
                if(!handlerThread.isAlive) {
                    handlerThread.start()
                }
                // characteristic to enable gps does not exist, starting transmission
                fusedLocationClient.lastLocation.addOnSuccessListener {
                    if (it != null) {
                        locationResultVar = it
                        sendData(gatt, characteristic)
                    }
                }
                fusedLocationClient.requestLocationUpdates(
                    LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        5000,
                    ).build(), locationCallback, handlerThread.looper
                )
            }


            gatt.readCharacteristic(readCharacteristic)

        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            writtenCharacteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            super.onCharacteristicWrite(gatt, writtenCharacteristic, status)

            // The gps command has been unlocked, now lock it for us
            if (writtenCharacteristic?.uuid == CHARACTERISTIC_ENABLE_GPS_COMMAND) {
                val lockCharacteristic = gatt.services.flatMap { s -> s.characteristics }.find { it.uuid == CHARACTERISTIC_ENABLED_GPS_COMMAND }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                    lockCharacteristic?.let {
                        Timber.i("Found characteristic to lock GPS: ${it.uuid}")
                        gatt.writeCharacteristic(
                            it,
                            byteArrayOf(0x01),
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        )
                    }

                } else {
                    lockCharacteristic?.value = byteArrayOf(0x01)
                    gatt.writeCharacteristic(lockCharacteristic)
                }
            } else if (writtenCharacteristic?.uuid == CHARACTERISTIC_ENABLED_GPS_COMMAND) {
                Timber.i("GPS flag enabled on device, will now send data")
                fusedLocationClient.lastLocation.addOnSuccessListener {
                    if (it != null) {
                        locationResultVar = it
                        sendData(gatt, characteristic)
                    }
                }
                fusedLocationClient.requestLocationUpdates(
                    LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        5000,
                    ).build(), locationCallback, Looper.getMainLooper()
                )
            }
            Timber.i("onCharacteristic write status: $status")
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                doOnRead(characteristic.value)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            doOnRead(value)
        }

        private fun doOnRead(value: ByteArray) {
            shouldSendTimeZoneAndDst = (value.size >= 5 && value[4].and(2.toByte()) == 2.toByte())
            Timber.i("Characteristic read, shouldSendTimeZoneAndDst: $shouldSendTimeZoneAndDst")
        }
    }


    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check if this is a shutdown request
        if (intent?.action == ACTION_REQUEST_SHUTDOWN) {
            requestShutdown()
            return START_REDELIVER_INTENT
        }

        // Cancel any pending shutdown since we're starting normally
        cancelShutdown()

        startAsForegroundService()

        this.startId = startId;

        val address = intent?.getStringExtra("address")
        startedManually = intent?.getBooleanExtra("startedManually", false) ?: false
        var device: BluetoothDevice? = null

        device = bluetoothManager.adapter.getRemoteDevice(address)

        if (gatt1 != null) {
            Timber.i("Gatt will be reused")
        } else {
            Timber.i("Gatt will be created")

            gatt1 = device?.connectGatt(this, true, callback)
        }
        return START_REDELIVER_INTENT
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()

        // Clean up shutdown timer
        cancelShutdown()

        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        gatt1?.disconnect()
        gatt1?.close()
        gatt1 = null
        Timber.i("Destroyed service")
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        if (Timber.treeCount == 0) {
            FileTree.initialize(this)
            Timber.plant(Timber.DebugTree(), FileTree(this))

            // Set up global exception handler to log crashes
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(defaultHandler))
        }


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(fetchedLocation: LocationResult) {

                // any location is better than none for now
                val lastLocation = fetchedLocation.lastLocation
                if (locationResultVar.provider.equals("") && lastLocation != null) {
                    locationResultVar = lastLocation
                    return
                }

                if (lastLocation != null) {
                    // new location is way less accurate, only take if the old location is very old
                    if ((lastLocation.accuracy - locationResultVar.accuracy) > 200) {
                        Timber.w(
                            "New location is way less accurate than the old one, will only update if the last location is older than 5 minutes"
                        )
                        if (lastLocation.time - locationResultVar.time > 1000 * 60 * 5) {
                            Timber.d(
                                "Last accurate location is older than 5 minutes, updating anyway"
                            )
                            locationResultVar = lastLocation
                        }
                    } else {
                        /*  Timber.w(
                              "New location is more accurate than the old one, updating"
                          )*/
                        locationResultVar = lastLocation
                    }

                }
                sendData(gatt1, characteristic)
            }
        }
    }

    private fun startAsForegroundService() {
        // create the notification channel
        NotificationsHelper.createNotificationChannel(this)

        // promote service to foreground service
        ServiceCompat.startForeground(
            this,
            1,
            NotificationsHelper.buildNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

        )
    }

    @SuppressLint("MissingPermission")
    private fun sendData(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
    ) {
        val timeZoneId = ZoneId.systemDefault()

        val paddingBytes = ByteArray(65)
        val fixedBytes = byteArrayOf(
            0x00,
            if (shouldSendTimeZoneAndDst) 0x5D else 0x59,
            0x08,
            0x02,
            0xFC.toByte(),
            if (shouldSendTimeZoneAndDst) 0x03 else 0x00,
            0x00,
            0x00,
            0x10,
            0x10,
            0x10
        )

        // taken from https://github.com/mlapaglia/AlphaSync/blob/main/app/src/main/java/com.alphasync/sonycommand/SonyCommandGenerator.kt
        val locationBytes = getConvertedCoordinates(locationResultVar)
        val dateBytes = getConvertedDate()
        val timeZoneOffsetBytes = getConvertedTimeZoneOffset(timeZoneId)
        val dstOffsetBytes = getConvertedDstOffset(timeZoneId)

        var data: ByteArray

        if (shouldSendTimeZoneAndDst) {
            data = ByteArray(95)
        } else {
            data = ByteArray(91)
        }
        var currentBytePosition = 0

        System.arraycopy(fixedBytes, 0, data, currentBytePosition, fixedBytes.size)
        currentBytePosition += fixedBytes.size
        System.arraycopy(locationBytes, 0, data, currentBytePosition, locationBytes.size)
        currentBytePosition += locationBytes.size
        System.arraycopy(dateBytes, 0, data, currentBytePosition, dateBytes.size)
        currentBytePosition += dateBytes.size
        System.arraycopy(paddingBytes, 0, data, currentBytePosition, paddingBytes.size)
        if (shouldSendTimeZoneAndDst) {
            currentBytePosition += paddingBytes.size
            System.arraycopy(
                timeZoneOffsetBytes,
                0,
                data,
                currentBytePosition,
                timeZoneOffsetBytes.size
            )
            currentBytePosition += timeZoneOffsetBytes.size
            System.arraycopy(dstOffsetBytes, 0, data, currentBytePosition, dstOffsetBytes.size)
        }
        //val hex = data.toHex()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (characteristic != null) {
                val result = gatt?.writeCharacteristic(
                    characteristic,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                )
                Timber.i("Write result: $result")
            }
        } else {
            if (characteristic != null) {
                characteristic.value = data
                val result = gatt?.writeCharacteristic(characteristic)
                Timber.i("Write result: $result")
            }
        }
    }

    /**
     * Requests a graceful shutdown with a 1-minute delay
     */
    fun requestShutdown() {
        if (isShutdownRequested) {
            Timber.i("Shutdown already requested, ignoring duplicate request")
            return
        }

        isShutdownRequested = true
        Timber.i("Shutdown requested, will terminate in ${SHUTDOWN_DELAY_MS / 1000} seconds")

        // Create single-threaded executor if needed
/*        if (shutdownExecutor == null) {
            shutdownExecutor = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "shutdown-timer").apply {
                    isDaemon = true
                }
            }
        }

        shutdownFuture = shutdownExecutor?.schedule({
            if (isShutdownRequested) {
                Timber.i("Shutdown timer expired, terminating service")
                // Switch back to main thread for UI operations
                Handler(Looper.getMainLooper()).post {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }, SHUTDOWN_DELAY_MS, TimeUnit.MILLISECONDS)*/
        stopSelf()
    }

    /**
     * Cancels any pending shutdown
     */
    private fun cancelShutdown() {
        if (isShutdownRequested) {
            Timber.i("Cancelling pending shutdown")
           /* shutdownFuture?.cancel(false)
            shutdownFuture = null*/
            isShutdownRequested = false
        }
    }
}

private fun getConvertedDstOffset(timezoneId: ZoneId): ByteArray {
    if (!timezoneId.rules.isDaylightSavings(Instant.now())) {
        return byteArrayOf(0, 0) // No DST offset
    }
    val offsetDstMin = timezoneId.rules.getDaylightSavings(Instant.now()).toMinutes().toInt()
    return offsetDstMin.toShort().toByteArray()
}

private fun getConvertedTimeZoneOffset(timezoneId: ZoneId): ByteArray {
    val dt = LocalDateTime.now()
    val offsetMin = timezoneId.rules.getOffset(dt).totalSeconds / 60
    return offsetMin.toShort().toByteArray()
}

private fun Short.toByteArray(): ByteArray {
    return byteArrayOf((this.toInt() shr 8).toByte(), this.toByte())
}


private fun getConvertedCoordinates(location: Location): ByteArray {
    val latitude = (location.latitude * 10000000).toInt()
    val latitudeBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(latitude).array()

    val longitude = (location.longitude * 10000000).toInt()
    val longitudeBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(longitude).array()

    return latitudeBytes + longitudeBytes
}

private fun getConvertedDate(): ByteArray {
    val currentDateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"))
    val yearBytes = currentDateTime.year.toShort().toByteArray()

    return byteArrayOf(
        yearBytes[0],
        yearBytes[1],
        currentDateTime.monthValue.toByte(),
        currentDateTime.dayOfMonth.toByte(),
        currentDateTime.hour.toByte(),
        currentDateTime.minute.toByte(),
        currentDateTime.second.toByte()
    )
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
