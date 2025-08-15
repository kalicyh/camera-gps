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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saschl.cameragps.R

@Composable
fun PairingConfirmationDialogWithLoading(
    deviceName: String,
    isPairing: Boolean,
    pairingResult: PairingResult?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = if (isPairing) { {} } else onDismiss, // Prevent dismissal during pairing
        title = {
            Text(
                text = when {
                    isPairing -> "Pairing Camera..."
                    pairingResult == PairingResult.SUCCESS -> "Pairing Complete!"
                    pairingResult == PairingResult.FAILED -> "Pairing Failed"
                    else -> "Camera Pairing Required"
                },
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                when {
                    isPairing -> {
                        // Show loading state
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 16.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Pairing with '$deviceName'...\nPlease wait.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    pairingResult == PairingResult.SUCCESS -> {
                        // Show success state
                        Text(
                            text = "Successfully paired with '$deviceName'!\n\nYou can now connect to your camera.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    pairingResult == PairingResult.FAILED -> {
                        // Show failure state
                        Text(
                            text = "Failed to pair with '$deviceName'.\n\nPlease ensure your camera is in pairing mode and try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        // Show initial instructions
                        Text(
                            text = stringResource(
                                R.string.to_pair_with_your_camera_please,
                                deviceName
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string._1_turn_on_your_camera),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string._2_go_to_camera_settings_and_enable_bluetooth_pairing_mode),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string._3_make_sure_the_camera_is_discoverable),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.once_your_camera_is_ready_tap_continue_to_start_pairing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                isPairing -> {
                    // No buttons during pairing
                }
                pairingResult == PairingResult.SUCCESS -> {
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
                pairingResult == PairingResult.FAILED -> {
                    TextButton(onClick = onRetry) {
                        Text("Try Again")
                    }
                }
                else -> {
                    TextButton(onClick = onConfirm) {
                        Text("Continue")
                    }
                }
            }
        },
        dismissButton = {
            if (!isPairing && pairingResult != PairingResult.SUCCESS) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
