package com.saschl.cameragps.service

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.companion.CompanionDeviceManager
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saschl.cameragps.R

@Composable
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun DeviceDetailScreen(device: BluetoothDevice, onDisassociate: (device: BluetoothDevice) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Text(
                text = "Devices details",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(32.dp)
            )
        }) { innerPadding ->

        BackHandler {
            onClose()
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),

        ) {
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Text(text = "Name: ${device.name} (${device.address})")
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(0.4f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDisassociate(device) },
                    border = ButtonDefaults.outlinedButtonBorder().copy(
                        brush = SolidColor(MaterialTheme.colorScheme.error),
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }



}
