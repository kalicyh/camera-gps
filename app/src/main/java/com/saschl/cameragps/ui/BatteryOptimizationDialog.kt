package com.saschl.cameragps.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saschl.cameragps.R
import com.saschl.cameragps.utils.PreferencesManager
import timber.log.Timber

@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.battery_optimization_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.battery_optimization_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = {
                        Timber.i("Battery optimization dialog cancelled")
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.battery_optimization_cancel))
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                TextButton(
                    onClick = {
                        PreferencesManager.setBatteryOptimizationDialogDismissed(context, true)
                        Timber.i("Battery optimization dialog dismissed permanently")
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.battery_optimization_dont_show))
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                // Direct intent to request ignoring battery optimizations for this app
                                /* Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                } */
                               
                           
                            context.startActivity(intent)
                            Timber.i("Opened battery optimization settings for package: ${context.packageName}")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open battery optimization settings, trying fallback")
                            // Fallback to general settings if specific intent fails
                            try {
                                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(fallbackIntent)
                                Timber.i("Opened general battery optimization settings")
                            } catch (fallbackException: Exception) {
                                Timber.e(fallbackException, "Failed to open any battery optimization settings")
                            }
                        }
                        onDismiss()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.battery_optimization_proceed),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    )
}
