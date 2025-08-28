package com.saschl.cameragps.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.saschl.cameragps.R
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.service.pairing.isDevicePaired
import com.saschl.cameragps.utils.PreferencesManager

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("MissingPermission")
@Composable
fun AssociatedDevicesList(
    associatedDevices: List<AssociatedDeviceCompat>,
    onConnect: (AssociatedDeviceCompat) -> Unit,
) {
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService<BluetoothManager>()
    val adapter = bluetoothManager?.adapter

    Column {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stickyHeader {
                Text(
                    text = stringResource(R.string.associated_devices),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (associatedDevices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.baseline_photo_camera_24),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.no_devices_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.no_devices_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(associatedDevices) { device ->
                val isPaired = try {
                    adapter?.let { isDevicePaired(it, device.address) } ?: false
                } catch (_: SecurityException) {
                    false
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clickable(
                            true,
                            onClick = {
                                onConnect(device)
                            }),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(0.2f),
                    ) {
                        Icon(
                            painterResource(R.drawable.baseline_photo_camera_24),
                            contentDescription = "Device Icon"
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        Text(
                            fontWeight = FontWeight.Bold,
                            text = device.name
                        )

                        if (!isPaired) {
                            Text(
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                text = context.getString(R.string.not_paired_tap_to_pair_again),
                            )
                        }
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S
                            && !PreferencesManager.isKeepAliveEnabled(context, device.address)
                        ) {
                            Text(
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                text = context.getString(R.string.android_12_requires_keep_alive),
                            )
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(0.8f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Show details"
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 2.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
