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
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.saschl.cameragps.notification.NotificationsHelper
import com.saschl.cameragps.service.CompanionDeviceSampleService.DeviceNotificationManager
import timber.log.Timber
import java.nio.ByteBuffer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import java.util.Timer
import java.util.UUID

class LocationSenderService : Service() {

    private var startId: Int = 0
    private val binder = LocalBinder()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var gatt1: BluetoothGatt? = null

    private var characteristic: BluetoothGattCharacteristic? = null

    private var locationResultVar: Location = Location("")

    private var shutdownTimer = Timer()

    private var startedManually: Boolean = false

    companion object {

        // Random UUID for our service known between the client and server to allow communication
        val SERVICE_UUID: UUID = UUID.fromString("8000dd00-dd00-ffff-ffff-ffffffffffff")

        // Same as the service but for the characteristic
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000dd11-0000-1000-8000-00805f9b34fb")


        private const val CHANNEL = "gatt_server_channel"
    }

    private val notificationManager: DeviceNotificationManager by lazy {
        DeviceNotificationManager(applicationContext)
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
                // Here you should handle the error returned in status based on the constants
                // https://developer.android.com/reference/android/bluetooth/BluetoothGatt#summary
                // For example for GATT_INSUFFICIENT_ENCRYPTION or
                // GATT_INSUFFICIENT_AUTHENTICATION you should create a bond.
                // https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createBond()

                Timber.e("An error happened: $status")
                fusedLocationClient.removeLocationUpdates(locationCallback)


                shutdownTimer = Timer()
                /* shutdownTimer.schedule(object : TimerTask() {
                     override fun run() {

                         Log.e("LocationSenderService", "Disconnecting and closing")
                         gatt.disconnect()
                         gatt.close()
                         stopSelf(startId)
                     }

                 }, 120000)*/

                if(startedManually) {
                    stopSelf()
                }
            } else {
                shutdownTimer.cancel()
                shutdownTimer.purge()

                Timber.i("Connected to device")
                gatt.discoverServices()

            }


        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            //    state = state.copy(gatt = gatt, mtu = mtu)
            //    currentOnStateChange(state)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            //      state = state.copy(services = gatt.services)
            //     currentOnStateChange(state)
            val service = gatt.services?.find { it.uuid == SERVICE_UUID }

            // If the GATTServerSample service is found, get the characteristic
            characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
            fusedLocationClient.lastLocation.addOnSuccessListener {
                locationResultVar = it
                sendData(gatt, characteristic)
            }
            fusedLocationClient.requestLocationUpdates(
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    5000,

                    ).build(), locationCallback, Looper.getMainLooper()
            )


        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            //     state = state.copy(messageSent = status == BluetoothGatt.GATT_SUCCESS)
            //     currentOnStateChange(state)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            //   if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            doOnRead(characteristic.value)
            //    }
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
            //     state = state.copy(messageReceived = value.decodeToString())
            //      currentOnStateChange(state)
        }
    }


    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForegroundService()

        this.startId = startId;

        val address = intent?.getStringExtra("address")
        startedManually = intent?.getBooleanExtra("startedManually", false)?: false
        var device: BluetoothDevice? = null

        // if(address == null)

        /* if(gatt1?.)*/

        /*   bluetoothManager.getConnectedDevices(BluetoothGatt.GATT).forEach {
               if (it.address == address) {
                   device = it
               }
           }*/

        device = bluetoothManager.adapter.getRemoteDevice(address)
        Timber.i("ON START YEAH")


        if (gatt1 != null) {
            Timber.i("Gatt will be reused")
            //     gatt1?.connect()
        } else {
            Timber.i("Gatt will be created")

            gatt1 = device?.connectGatt(this, true, callback)
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        gatt1?.disconnect()
        gatt1?.close()
        shutdownTimer.cancel()
        shutdownTimer.purge()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        /* if (missingPermissions()) {
             Log.e(CompanionDeviceSampleService::class.java.toString(),"aaa");
             return
         }*/


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(fetchedLocation: LocationResult) {
                //Log.i("ayup", "Location result received " + fetchedLocation.lastLocation.toString())

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
                        Timber.w(
                            "New location is more accurate than the old one, updating"
                        )
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
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION.or(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        )
    }

    @SuppressLint("MissingPermission")
    private fun sendData(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
    ) {
        val data = ByteArray(95)

        data[0] = 0x00
        data[1] = 0x5D.toByte()


        // bytes 2-4
        val fixedData = "0802FC".chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        System.arraycopy(fixedData, 0, data, 2, fixedData.size)


        // transmit timezone offset? NO
        data[5] = 0x00.toByte()

        val fixedData2 = "0000101010".chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        System.arraycopy(fixedData2, 0, data, 6, fixedData2.size)

        // position information
        val latitude = locationResultVar.latitude
        val longitude = locationResultVar.longitude
        val locationData = setLocation(latitude, longitude)
        System.arraycopy(locationData, 0, data, 11, locationData.size)


        // here UTC time must be used
        val dateData = setDate(TimeZone.getTimeZone("UTC").toZoneId())
        System.arraycopy(dateData, 0, data, 19, dateData.size)


        val hex = data.toHex()
        Timber.i("Sending data: $hex with location $locationResultVar")

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
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun setLocation(latitude: Double, longitude: Double): ByteArray {
    val myLat = (latitude * 1e7).toInt()
    val myLng = (longitude * 1e7).toInt()
    val myLatByte = ByteBuffer.allocate(4).putInt(myLat).array()
    val myLngByte = ByteBuffer.allocate(4).putInt(myLng).array()
    return myLatByte + myLngByte
}

fun setDate(zoneId: ZoneId): ByteArray {
    val now = ZonedDateTime.ofInstant(Instant.now(), zoneId)
    val year = now.year.toShort()
    val yearBytes = ByteBuffer.allocate(2).putShort(year).array()
    return byteArrayOf(
        yearBytes[0], yearBytes[1],
        now.monthValue.toByte(),
        now.dayOfMonth.toByte(),
        now.hour.toByte(),
        now.minute.toByte(),
        now.second.toByte()
    )
}