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

package com.saschl.cameragps.service.pairing

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import timber.log.Timber
import java.util.Locale

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun isDevicePaired(adapter: BluetoothAdapter?, deviceAddress: String): Boolean {
    return adapter?.bondedDevices?.any {
        it.address == deviceAddress.uppercase(Locale.getDefault())
    } ?: false
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
fun BondingStateListener(
    device: BluetoothDevice,
    onBondingSuccess: () -> Unit,
    onBondingFailed: () -> Unit
) {
    val context = LocalContext.current
    val currentOnBondingSuccess by rememberUpdatedState(onBondingSuccess)
    val currentOnBondingFailed by rememberUpdatedState(onBondingFailed)

    DisposableEffect(device) {
        val bondingReceiver = object : BroadcastReceiver() {
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
                                BluetoothDevice.BOND_BONDED -> {
                                    currentOnBondingSuccess()
                                }
                                BluetoothDevice.BOND_NONE -> {
                                    currentOnBondingFailed()
                                }
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(bondingReceiver, filter)

        onDispose {
            context.unregisterReceiver(bondingReceiver)
        }
    }
}
