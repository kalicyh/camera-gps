/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.saschl.cameragps.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.saschl.cameragps.R
import timber.log.Timber
import java.util.Locale
import java.util.UUID


class CompanionDeviceSampleService : CompanionDeviceService() {

    private fun startLocationSenderService(address: String?) {
        val serviceIntent = Intent(this, LocationSenderService::class.java)
        serviceIntent.putExtra("address", address?.uppercase(Locale.getDefault()))
        Timber.i("Starting LocationSenderService for address: $address")
        startForegroundService(serviceIntent)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        super.onDeviceAppeared(address)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || missingPermissions()) {
            return
        }

        startLocationSenderService(address)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        super.onDeviceAppeared(associationInfo)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Timber.i("Device appeared old API: ${associationInfo.id}")

            val address = associationInfo.deviceMacAddress?.toString() ?: return

            startLocationSenderService(address)
        }
    }


    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    @SuppressLint("MissingPermission")
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        super.onDevicePresenceEvent(event)
        if (missingPermissions()) {
            Timber.e(CompanionDeviceSampleService::class.java.toString(), "Missing permissions")
            return
        }

        if (event.event == DevicePresenceEvent.EVENT_BLE_APPEARED) {

            Timber.i("Device appeared new API: ${event.associationId}")
            val associationId = event.associationId
            val deviceManager = getSystemService<CompanionDeviceManager>()
            val associatedDevices = deviceManager?.getMyAssociations()
            val associationInfo = associatedDevices?.find { it.id == associationId }
            val address = associationInfo?.deviceMacAddress?.toString()

            startLocationSenderService(address)
        }

        if (event.event == DevicePresenceEvent.EVENT_BLE_DISAPPEARED) {
            Timber.i("Device disappeared new API: ${event.associationId}")
            stopService(Intent(this, LocationSenderService::class.java))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {
        super.onDeviceDisappeared(address)
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Timber.i("Device disappeared oldest api: $address")
            stopService(Intent(this, LocationSenderService::class.java))
            return
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            super.onDeviceDisappeared(associationInfo)
            Timber.i("Device disappeared old API: ${associationInfo.id}")
            stopService(Intent(this, LocationSenderService::class.java))
        }

    }

    override fun onCreate() {
        super.onCreate()
        if (Timber.treeCount == 0) {
            FileTree.initialize(this)
            Timber.plant(Timber.DebugTree(), FileTree(this))

            // Set up global exception handler to log crashes
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(defaultHandler))
        }
        Timber.i("CDM started")
    }


    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()

        //   fusedLocationClient.removeLocationUpdates(locationCallback)
        // timerJob.cancel()

        //fusedLocationClient.removeLocationUpdates(locationCallback)

      //  stopService(Intent(this, LocationSenderService::class.java))
        //  notificationManager.onDeviceDisappeared("Service gone :)")

        /*   gatt?.disconnect()
           gatt?.close()*/ }

    /*  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
          super.onStartCommand(intent, flags, startId)

          return START_STICKY
      }*/


    /**
     * Check BLUETOOTH_CONNECT is granted and POST_NOTIFICATIONS is granted for devices running
     * Android 13 and above.
     */
    private fun missingPermissions(): Boolean = ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED

}
