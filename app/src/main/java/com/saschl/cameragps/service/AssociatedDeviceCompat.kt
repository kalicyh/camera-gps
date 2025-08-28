package com.saschl.cameragps.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Wrapper for the different type of classes the CDM returns
 */
data class AssociatedDeviceCompat(
    val id: Int,
    val address: String,
    var name: String,
    val device: BluetoothDevice?,
    var isPaired: Boolean = false
)


@SuppressLint("MissingPermission")
internal fun CompanionDeviceManager.getAssociatedDevices(adapter: BluetoothAdapter): List<AssociatedDeviceCompat> {
    val associatedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        myAssociations.map { it.toAssociatedDevice() }
    } else {
        // Before Android 34 we can only get the MAC.
        @Suppress("DEPRECATION")
        associations.map {
            AssociatedDeviceCompat(
                id = -1,
                address = it.uppercase(Locale.getDefault()),
                name = adapter.getRemoteDevice(it.uppercase()).name ?: "N/A",
                device = null,
            )
        }
    }
    return associatedDevice
}


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun AssociationInfo.toAssociatedDevice() = AssociatedDeviceCompat(
    id = id,
    address = deviceMacAddress?.toString().let { it?.uppercase(Locale.getDefault()) } ?: "N/A",
    name = displayName?.ifBlank { "N/A" }?.toString() ?: "N/A",
    device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        associatedDevice?.bleDevice?.device
    } else {
        null
    },
)
