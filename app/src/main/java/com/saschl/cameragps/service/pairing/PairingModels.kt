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

import com.saschl.cameragps.service.AssociatedDeviceCompat

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

// Data class to hold pairing dialog state
data class PairingDialogState(
    val device: AssociatedDeviceCompat? = null,
    val isVisible: Boolean = false,
    val isPairing: Boolean = false,
    val pairingResult: PairingResult? = null
) {
    companion object {
        val Hidden = PairingDialogState()
    }
}

enum class PairingResult {
    SUCCESS,
    FAILED
}
