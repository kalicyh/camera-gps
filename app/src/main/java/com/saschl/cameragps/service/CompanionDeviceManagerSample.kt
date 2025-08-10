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
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.TRANSPORT_AUTO
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saschl.cameragps.R
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.service.getAssociatedDevices
import com.saschl.cameragps.service.toAssociatedDevice
import com.saschl.cameragps.ui.EnhancedLocationPermissionBox
import com.saschl.cameragps.ui.LogViewerActivity
import com.saschl.cameragps.service.CompanionDeviceSampleService.Companion.CHARACTERISTIC_UUID
import com.saschl.cameragps.service.CompanionDeviceSampleService.Companion.SERVICE_UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteBuffer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.Executor
import java.util.regex.Pattern
import kotlin.random.Random

@Composable
fun CompanionDeviceManagerSample() {

    val context = LocalContext.current
    val deviceManager = context.getSystemService<CompanionDeviceManager>()
    val adapter = context.getSystemService<BluetoothManager>()?.adapter
    var selectedDevice by remember {
        mutableStateOf<BluetoothDevice?>(null)
    }
    if (deviceManager == null || adapter == null) {
        Text(text = "No Companion device manager found. The device does not support it.")
    } else {
        if (selectedDevice == null) {
            EnhancedLocationPermissionBox {
                DevicesScreen(deviceManager) { device ->
                    selectedDevice =
                        (device.device ?: adapter.getRemoteDevice(device.address))
                }
            }
        } else {
            EnhancedLocationPermissionBox {
                ConnectDeviceScreen(device = selectedDevice!!) {
                    selectedDevice = null
                }
            }
        }
    }
}


private data class DeviceConnectionState(
    val gatt: BluetoothGatt?,
    val connectionState: Int,
    val mtu: Int,
    val services: List<BluetoothGattService> = emptyList(),
    val messageSent: Boolean = false,
    val messageReceived: String = "",
) {
    companion object {
        val None = DeviceConnectionState(null, -1, -1)
    }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
private fun BLEConnectEffect(
    device: BluetoothDevice,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onStateChange: (DeviceConnectionState) -> Unit,
) {
    val context = LocalContext.current
    val currentOnStateChange by rememberUpdatedState(onStateChange)

    // Keep the current connection state
    var state by remember {
        mutableStateOf(DeviceConnectionState.None)
    }

    DisposableEffect(lifecycleOwner, device) {
        // This callback will notify us when things change in the GATT connection so we can update
        // our state
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                super.onConnectionStateChange(gatt, status, newState)
                state = state.copy(gatt = gatt, connectionState = newState)
                currentOnStateChange(state)

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    // Here you should handle the error returned in status based on the constants
                    // https://developer.android.com/reference/android/bluetooth/BluetoothGatt#summary
                    // For example for GATT_INSUFFICIENT_ENCRYPTION or
                    // GATT_INSUFFICIENT_AUTHENTICATION you should create a bond.
                    // https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createBond()
                    Log.e("BLEConnectEffect", "An error happened: $status")
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                state = state.copy(gatt = gatt, mtu = mtu)
                currentOnStateChange(state)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
                state = state.copy(services = gatt.services)
                currentOnStateChange(state)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int,
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                state = state.copy(messageSent = status == BluetoothGatt.GATT_SUCCESS)
                currentOnStateChange(state)
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
                state = state.copy(messageReceived = value.decodeToString())
                currentOnStateChange(state)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (state.gatt != null) {
                    // If we previously had a GATT connection let's reestablish it
                    state.gatt?.connect()
                } else {
                    // Otherwise create a new GATT connection
                    state = state.copy(
                        gatt = device.connectGatt(
                            context,
                            false,
                            callback,
                            TRANSPORT_AUTO
                        )
                    )
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                // Unless you have a reason to keep connected while in the bg you should disconnect
                state.gatt?.disconnect()
            }
        }

        // Add the observer to the lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the effect leaves the Composition, remove the observer and close the connection
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.gatt?.close()
            state = DeviceConnectionState.None
        }
    }
}

internal fun Int.toConnectionStateString() = when (this) {
    BluetoothProfile.STATE_CONNECTED -> "Connected"
    BluetoothProfile.STATE_CONNECTING -> "Connecting"
    BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
    BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting"
    else -> "N/A"
}


@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun ConnectDeviceScreen(device: BluetoothDevice, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Keeps track of the last connection state with the device
    var state by remember(device) {
        mutableStateOf<DeviceConnectionState?>(null)
    }

    // Track Bluetooth pairing state
    var pairingState by remember(device) {
        mutableStateOf(BluetoothPairingState())
    }

    // Once the device services are discovered find the GATTServerSample service
    val service by remember(state) {
        mutableStateOf(state?.services?.find { it.uuid == SERVICE_UUID })
    }
    // If the GATTServerSample service is found, get the characteristic
    val characteristic by remember(service) {
        mutableStateOf(service?.getCharacteristic(CHARACTERISTIC_UUID))
    }

    // Handle Bluetooth pairing
    BluetoothPairingEffect(
        device = device,
        onPairingStateChange = { newPairingState ->
            pairingState = newPairingState
        }
    )

    // This effect will handle the connection and notify when the state changes
    BLEConnectEffect(device = device) {
        // update our state to recompose the UI
        state = it

        // If we get authentication/encryption errors, try to pair the device
        if (it.gatt != null) {
            // Check if we need to pair the device
            val adapter = context.getSystemService<BluetoothManager>()?.adapter
            if (!isDevicePaired(adapter, device.address) && pairingState.state != PairingState.PAIRING) {
                Timber.i("Device not paired, initiating pairing process")
                scope.launch {
                    initiateBluetoothPairing(device)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Device Details", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Name: ${device.name} (${device.address})")

        // Show pairing status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Pairing Status:")
            Text(
                text = when (pairingState.state) {
                    PairingState.NOT_PAIRED -> "Not Paired"
                    PairingState.PAIRING -> "Pairing..."
                    PairingState.PAIRED -> "Paired ✓"
                    PairingState.PAIRING_FAILED -> "Failed"
                },
                color = when (pairingState.state) {
                    PairingState.PAIRED -> MaterialTheme.colorScheme.primary
                    PairingState.PAIRING_FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }

        // Show pairing error if any
        pairingState.errorMessage?.let { error ->
            Text(
                text = "Pairing Error: $error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(text = "Connection Status: ${state?.connectionState?.toConnectionStateString()}")
        Text(text = "MTU: ${state?.mtu}")
        Text(text = "Services: ${state?.services?.joinToString { it.uuid.toString() + " " + it.type }}")
        Text(text = "Message sent: ${state?.messageSent}")
        Text(text = "Message received: ${state?.messageReceived}")

        // Manual pairing button
        Button(
            enabled = pairingState.state == PairingState.NOT_PAIRED || pairingState.state == PairingState.PAIRING_FAILED,
            onClick = {
                scope.launch {
                    Timber.i("Manual pairing initiated for ${device.name}")
                    initiateBluetoothPairing(device)
                }
            },
        ) {
            Text(text = if (pairingState.state == PairingState.PAIRING) "Pairing..." else "Pair Device")
        }
        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    if (state?.connectionState == BluetoothProfile.STATE_DISCONNECTED) {
                        //      state?.gatt?.connect()
                    }
                    // Example on how to request specific MTUs
                    // Note that from Android 14 onwards the system will define a default MTU and
                    // it will only be sent once to the peripheral device
                    state?.gatt?.requestMtu(Random.nextInt(27, 190))
                }
            },
        ) {
            Text(text = "Request MTU")
        }
        Button(
            enabled = state?.gatt != null,
            onClick = {
                scope.launch(Dispatchers.IO) {
                    // Once we have the connection discover the peripheral services
                    state?.gatt?.discoverServices()
                }
            },
        ) {
            Text(text = "Discover")
        }
        Button(
            enabled = state?.gatt != null && characteristic != null,
            onClick = {
                /* scope.launch(Dispatchers.IO) {
                     sendData(state?.gatt!!, characteristic!!)
                 }*/
            },
        ) {
            Text(text = "Write to server")
        }
        Button(
            enabled = state?.gatt != null && characteristic != null,
            onClick = {
                scope.launch(Dispatchers.IO) {
                    state?.gatt?.readCharacteristic(characteristic)
                }
            },
        ) {
            Text(text = "Read characteristic")
        }
        Button(onClick = onClose) {
            Text(text = "Close")
        }
    }
}

fun set_location(latitude: Double, longitude: Double): ByteArray {
    val myLat = (latitude * 1e7).toInt()
    val myLng = (longitude * 1e7).toInt()
    val myLatByte = ByteBuffer.allocate(4).putInt(myLat).array()
    val myLngByte = ByteBuffer.allocate(4).putInt(myLng).array()
    return myLatByte + myLngByte
}

fun set_date(zoneId: ZoneId): ByteArray {
    val now = ZonedDateTime.ofInstant(Instant.now(), zoneId)
    val year = now.year.toShort()
    val yearBytes = ByteBuffer.allocate(2).putShort(year).array()
    val hour = now.hour
    return byteArrayOf(
        yearBytes[0], yearBytes[1],
        now.monthValue.toByte(),
        now.dayOfMonth.toByte(),
        now.hour.toByte(),
        now.minute.toByte(),
        now.second.toByte()
    )
}


@Composable
private fun DevicesScreen(
    deviceManager: CompanionDeviceManager,
    onConnect: (AssociatedDeviceCompat) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var associatedDevices by remember {
        // If we already associated the device no need to do it again.
        mutableStateOf(deviceManager.getAssociatedDevices())
    }

    // Single state for pairing dialog
    var pairingDialogState by remember { mutableStateOf(PairingDialogState.Hidden) }

    LaunchedEffect(associatedDevices) {
        associatedDevices.forEach { device ->
            // After starting device presence observation, check if pairing is needed
            scope.launch {
                val bluetoothManager = context.getSystemService<BluetoothManager>()
                val adapter = bluetoothManager?.adapter
                val bluetoothDevice = device.device ?: adapter?.getRemoteDevice(device.address)

                if (bluetoothDevice != null && !isDevicePaired(adapter, device.address)) {
                    Timber.i("Device ${device.name} not paired, showing pairing confirmation dialog")
                    pairingDialogState = PairingDialogState(device = device, isVisible = true)
                } else if (isDevicePaired(adapter, device.address)) {
                    Timber.i("Device ${device.name} is already paired")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                        deviceManager.startObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(device.id).build())
                    } else {
                        deviceManager.startObservingDevicePresence(device.address)
                    }
                }
            }
        }
    }

    // Show pairing confirmation dialog
    pairingDialogState.device?.let { device ->
        if (pairingDialogState.isVisible) {
            PairingConfirmationDialog(
                deviceName = device.name,
                onConfirm = {
                    scope.launch {
                        val bluetoothManager = context.getSystemService<BluetoothManager>()
                        val adapter = bluetoothManager?.adapter
                        val bluetoothDevice = device.device ?: adapter?.getRemoteDevice(device.address)

                        if (bluetoothDevice != null) {
                            Timber.i("User confirmed pairing for ${device.name}, initiating pairing")
                            val pairingResult = initiateBluetoothPairing(bluetoothDevice)
                            if (pairingResult) {
                                Timber.i("Pairing initiated successfully for ${device.name}")
                                Timber.i("Starting observing device presence for ${device.name})")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                                    deviceManager.startObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(device.id).build())
                                } else {
                                    deviceManager.startObservingDevicePresence(device.address)
                                }
                            } else {
                                Timber.w("Failed to initiate pairing for ${device.name}")
                            }
                        }
                    }
                    pairingDialogState = PairingDialogState.Hidden
                },
                onDismiss = {
                    Timber.i("User cancelled pairing for ${device.name}")
                    pairingDialogState = PairingDialogState.Hidden
                }
            )
        }
    }

    Scaffold(
        topBar = {
            Text(
                text = "Sony Camera GPS",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(32.dp),
            )
        }) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,

        ) {
            Button(onClick = {
                context.startActivity(
                    Intent(
                        context,
                        LogViewerActivity::class.java
                    )
                )
            }) { Text(text = "View logs") }
            ScanForDevicesMenu(deviceManager) {
                associatedDevices = associatedDevices + it
            }
            AssociatedDevicesList(
                associatedDevices = associatedDevices,
                onConnect = onConnect,
                onDisassociate = {
                    scope.launch {

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                            deviceManager.stopObservingDevicePresence(
                                ObservingDevicePresenceRequest.Builder().setAssociationId(it.id)
                                    .build()
                            )

                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            @Suppress("DEPRECATION")
                            deviceManager.stopObservingDevicePresence(it.address)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            deviceManager.disassociate(it.id)
                        } else {
                            @Suppress("DEPRECATION")
                            deviceManager.disassociate(it.address)
                        }


                        associatedDevices = deviceManager.getAssociatedDevices()
                    }
                },
            )
        }
    }
}

@Composable
private fun ScanForDevicesMenu(
    deviceManager: CompanionDeviceManager,
    onDeviceAssociated: (AssociatedDeviceCompat) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var errorMessage by remember {
        mutableStateOf("")
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        when (it.resultCode) {
            CompanionDeviceManager.RESULT_OK -> {
                it.data?.getAssociationResult()?.run {
                    onDeviceAssociated(this)
                }
            }

            CompanionDeviceManager.RESULT_CANCELED -> {
                errorMessage = "The request was canceled"
            }

            CompanionDeviceManager.RESULT_INTERNAL_ERROR -> {
                errorMessage = "Internal error happened"
            }

            CompanionDeviceManager.RESULT_DISCOVERY_TIMEOUT -> {
                errorMessage = "No device matching the given filter were found"
            }

            CompanionDeviceManager.RESULT_USER_REJECTED -> {
                errorMessage = "The user explicitly declined the request"
            }

            else -> {
                errorMessage = "Unknown error"
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                text = stringResource(R.string.scan_for_devices),
            )
            Button(
                modifier = Modifier.weight(0.3f),
                onClick = {
                    scope.launch {
                        val intentSender = requestDeviceAssociation(deviceManager)
                        launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                },
            ) {
                Text(text = "Start")
            }
        }
        if (errorMessage.isNotBlank()) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssociatedDevicesList(
    associatedDevices: List<AssociatedDeviceCompat>,
    onConnect: (AssociatedDeviceCompat) -> Unit,
    onDisassociate: (AssociatedDeviceCompat) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stickyHeader {
            Text(
                text = "Associated Devices:",
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(associatedDevices) { device ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Text(text = "ID: ${device.id}")
                    Text(text = "MAC: ${device.address}")
                    Text(text = "Name: ${device.name}")
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.6f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                ) {
                    OutlinedButton(
                        onClick = { onConnect(device) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Connect")
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDisassociate(device) },
                        border = ButtonDefaults.outlinedButtonBorder().copy(
                            brush = SolidColor(MaterialTheme.colorScheme.error),
                        ),
                    ) {
                        Text(text = "Disassociate", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun Intent.getAssociationResult(): AssociatedDeviceCompat? {
    var result: AssociatedDeviceCompat? = null

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        result = getParcelableExtra(
            CompanionDeviceManager.EXTRA_ASSOCIATION,
            AssociationInfo::class.java,
        )?.toAssociatedDevice()
    } else {
        // Below Android 33 the result returns either a BLE ScanResult, a
        // Classic BluetoothDevice or a Wifi ScanResult
        // In our case we are looking for our BLE GATT server so we can cast directly
        // to the BLE ScanResult
        @Suppress("DEPRECATION")
        val scanResult = getParcelableExtra<ScanResult>(CompanionDeviceManager.EXTRA_DEVICE)
        if (scanResult != null) {
            result = AssociatedDeviceCompat(
                id = scanResult.advertisingSid,
                address = scanResult.device.address ?: "N/A",
                name = scanResult.scanRecord?.deviceName ?: "N/A",
                device = scanResult.device,
            )
        }
    }
    return result

}

private suspend fun requestDeviceAssociation(deviceManager: CompanionDeviceManager): IntentSender {
    // Match only Bluetooth devices whose service UUID matches this pattern.
    // For this demo we will match our GATTServerSample
    val deviceFilter = BluetoothLeDeviceFilter.Builder()
        .setNamePattern(Pattern.compile("ILCE"))
        .build()

    val pairingRequest: AssociationRequest = AssociationRequest.Builder()
        // Find only devices that match this request filter.
        .addDeviceFilter(deviceFilter)
        // Stop scanning as soon as one device matching the filter is found.
      //  .setSingleDevice(true)
        .build()

    val result = CompletableDeferred<IntentSender>()

    val callback = object : CompanionDeviceManager.Callback() {
        override fun onAssociationPending(intentSender: IntentSender) {
            result.complete(intentSender)
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onDeviceFound(intentSender: IntentSender) {
            result.complete(intentSender)
        }

        override fun onAssociationCreated(associationInfo: AssociationInfo) {

            Timber.i("Association created: ${associationInfo.displayName} (${associationInfo.id})")
            // If you want to start observing the device presence you can do it here.
            // This will allow you to receive events when the device is nearby or not.
            // Note that this is only available in API 34 and above.
        /*    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                deviceManager.startObservingDevicePresence(
                    ObservingDevicePresenceRequest.Builder().setAssociationId(associationInfo.id)
                        .build()
                )
            } else {
                deviceManager.myAssociations.stream().filter { it -> it.id == associationInfo. }
                deviceManager.startOmyAssociationsbservingDevicePresence(it.toAssociatedDevice().address)
            }*/
            // This callback was added in API 33 but the result is also send in the activity result.
            // For handling backwards compatibility we can just have all the logic there instead
        }

        override fun onFailure(errorMessage: CharSequence?) {
            result.completeExceptionally(IllegalStateException(errorMessage?.toString().orEmpty()))
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val executor = Executor { it.run() }
        deviceManager.associate(pairingRequest, executor, callback)
    } else {
        deviceManager.associate(pairingRequest, callback, null)
    }

    return result.await()
}

// Bluetooth pairing state and functionality
enum class PairingState {
    NOT_PAIRED,
    PAIRING,
    PAIRED,
    PAIRING_FAILED
}

data class BluetoothPairingState(
    val state: PairingState = PairingState.NOT_PAIRED,
    val errorMessage: String? = null
)

@RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
fun isDevicePaired(adapter: BluetoothAdapter?, deviceAddress: String): Boolean {
    return adapter?.bondedDevices?.any { it.address == deviceAddress.uppercase(Locale.getDefault()) } ?: false
}

@RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
fun initiateBluetoothPairing(device: BluetoothDevice): Boolean {
    return try {
        device.createBond()
    } catch (e: SecurityException) {
        Timber.e(e, "Failed to initiate pairing due to security exception")
        false
    } catch (e: Exception) {
        Timber.e(e, "Failed to initiate pairing")
        false
    }
}

@Composable
fun BluetoothPairingEffect(
    device: BluetoothDevice,
    onPairingStateChange: (BluetoothPairingState) -> Unit,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    val context = LocalContext.current
    val currentOnPairingStateChange by rememberUpdatedState(onPairingStateChange)

    DisposableEffect(device, lifecycleOwner) {
        var pairingState by mutableStateOf(BluetoothPairingState())

        val pairingReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        val bondedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                        if (bondedDevice?.address == device.address) {
                            when (bondState) {
                                BluetoothDevice.BOND_BONDING -> {
                                    pairingState = BluetoothPairingState(PairingState.PAIRING)
                                    currentOnPairingStateChange(pairingState)
                                    Timber.i("Bluetooth pairing in progress with ${device.name}")
                                }
                                BluetoothDevice.BOND_BONDED -> {
                                    pairingState = BluetoothPairingState(PairingState.PAIRED)
                                    currentOnPairingStateChange(pairingState)
                                    Timber.i("Bluetooth pairing successful with ${device.name}")
                                }
                                BluetoothDevice.BOND_NONE -> {
                                    pairingState = BluetoothPairingState(
                                        PairingState.PAIRING_FAILED,
                                        "Pairing failed or bond was removed"
                                    )
                                    currentOnPairingStateChange(pairingState)
                                    Timber.w("Bluetooth pairing failed with ${device.name}")
                                }
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(pairingReceiver, filter)

        // Check initial pairing state
        val adapter = context.getSystemService<BluetoothManager>()?.adapter
        val isAlreadyPaired = isDevicePaired(adapter, device.address)
        if (isAlreadyPaired) {
            pairingState = BluetoothPairingState(PairingState.PAIRED)
            currentOnPairingStateChange(pairingState)
        } else {
            pairingState = BluetoothPairingState(PairingState.NOT_PAIRED)
            currentOnPairingStateChange(pairingState)
        }

        onDispose {
            context.unregisterReceiver(pairingReceiver)
        }
    }
}

@Composable
private fun PairingConfirmationDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Camera Pairing Required",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "To pair with your camera '$deviceName', please:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "\n1. Turn on your camera",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "2. Go to camera settings and enable Bluetooth/Pairing mode",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "3. Make sure the camera is discoverable",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "\nOnce your camera is ready, tap 'Continue' to start pairing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Data class to hold pairing dialog state
private data class PairingDialogState(
    val device: AssociatedDeviceCompat? = null,
    val isVisible: Boolean = false
) {
    companion object {
        val Hidden = PairingDialogState()
    }
}
