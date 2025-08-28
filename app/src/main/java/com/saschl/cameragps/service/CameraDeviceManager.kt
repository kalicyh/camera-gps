package com.saschl.cameragps.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saschl.cameragps.service.pairing.startDevicePresenceObservation
import com.saschl.cameragps.ui.DevicesScreen
import com.saschl.cameragps.ui.EnhancedLocationPermissionBox
import com.saschl.cameragps.utils.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("MissingPermission")
@Composable
fun CameraDeviceManager(
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceManager = context.getSystemService<CompanionDeviceManager>()
    val adapter = context.getSystemService<BluetoothManager>()?.adapter
    val locationManager = context.getSystemService<LocationManager>()
    var selectedDevice by remember {
        mutableStateOf<AssociatedDeviceCompat?>(null)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    var associatedDevices by remember {
        mutableStateOf(deviceManager!!.getAssociatedDevices(adapter!!))
    }

    var isBluetoothEnabled by remember {
        mutableStateOf(adapter?.isEnabled == true)
    }

    var isLocationEnabled by remember {
        mutableStateOf(locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                      locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true)
    }

    LaunchedEffect(lifecycleState) {
        when (lifecycleState) {
            Lifecycle.State.RESUMED -> {
                associatedDevices = deviceManager!!.getAssociatedDevices(adapter!!)
                isBluetoothEnabled = adapter.isEnabled == true
                isLocationEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                                  locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            }
            else -> { /* No action needed */
            }
        }
    }

    if (deviceManager == null || adapter == null) {
        Text(text = "No Companion device manager found. The device does not support it.")
    } else {
        if (selectedDevice == null) {
            EnhancedLocationPermissionBox {
                DevicesScreen(
                    deviceManager = deviceManager,
                    isBluetoothEnabled = isBluetoothEnabled,
                    isLocationEnabled = isLocationEnabled,
                    associatedDevices = associatedDevices,
                    onDeviceAssociated = {
                        scope.launch {
                            delay(1000) // give the system a short time to breathe
                            startDevicePresenceObservation(deviceManager, it)
                        }
                    },
                    onConnect = { device ->
                        selectedDevice = device
                    },
                    onSettingsClick = onSettingsClick
                )
            }
        } else {
            EnhancedLocationPermissionBox {
                DeviceDetailScreen(
                    device = selectedDevice!!,
                    deviceManager = deviceManager,
                    onDisassociate = { device ->
                        associatedDevices.find { ass -> ass.address == device.address }
                            ?.let { foundDevice ->
                                Timber.i("Disassociating device: ${foundDevice.name} (${foundDevice.address})")
                                scope.launch {
                                    PreferencesManager.setKeepAliveEnabled(
                                        context,
                                        foundDevice.address,
                                        false
                                    )

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                                        deviceManager.stopObservingDevicePresence(
                                            ObservingDevicePresenceRequest.Builder()
                                                .setAssociationId(foundDevice.id).build()
                                        )
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        @Suppress("DEPRECATION")
                                        deviceManager.stopObservingDevicePresence(foundDevice.address)
                                    }

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        deviceManager.disassociate(foundDevice.id)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        deviceManager.disassociate(foundDevice.address)
                                    }

                                    val serviceIntent = Intent(
                                        context.applicationContext,
                                        LocationSenderService::class.java
                                    )
                                    context.stopService(serviceIntent)

                                    associatedDevices = deviceManager.getAssociatedDevices(adapter)
                                }
                                selectedDevice = null
                            }
                    },
                    onClose = { selectedDevice = null }
                )
            }
        }
    }
}
