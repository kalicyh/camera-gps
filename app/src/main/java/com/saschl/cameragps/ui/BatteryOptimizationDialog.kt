package com.saschl.cameragps.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
    val isXiaomi = isXiaomiDevice()

    if (isXiaomi) {
        XiaomiBatteryOptimizationDialog(onDismiss = onDismiss)
    } else {
        StandardBatteryOptimizationDialog(onDismiss = onDismiss)
    }
}

@Composable
private fun XiaomiBatteryOptimizationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.battery_optimization_xiaomi_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.battery_optimization_xiaomi_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Battery optimization settings button
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent().apply {
                                component = ComponentName(
                                    "com.miui.securitycenter",
                                    "com.miui.appmanager.AppManagerMainActivity"
                                )
                                putExtra("package_name", context.packageName)
                                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open Xiaomi battery optimization settings")
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.battery_optimization_xiaomi_battery),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Autostart settings button
                TextButton(
                    onClick = {
                        try {
                            // Try to open Xiaomi's autostart management
                            val autostartIntent = Intent().apply {
                                component = android.content.ComponentName(
                                    "com.miui.securitycenter",
                                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                )
                            }
                            context.startActivity(autostartIntent)
                            Timber.i("Opened Xiaomi autostart settings")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open Xiaomi autostart settings, trying fallback")
                            try {
                                // Fallback to general app settings
                                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(fallbackIntent)
                                Timber.i("Opened app details settings as fallback")
                            } catch (fallbackException: Exception) {
                                Timber.e(fallbackException, "Failed to open any settings")
                            }
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.battery_optimization_xiaomi_autostart),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Don't show again button
                TextButton(
                    onClick = {
                        PreferencesManager.setBatteryOptimizationDialogDismissed(context, true)
                        Timber.i("Xiaomi battery optimization dialog dismissed permanently")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_optimization_dont_show))
                }

                // Cancel button
                TextButton(
                    onClick = {
                        Timber.i("Xiaomi battery optimization dialog cancelled")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_optimization_cancel))
                }
            }
        }
    )
}

@Composable
private fun StandardBatteryOptimizationDialog(
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = {
                        try {
                            // should be safe to use as we do not request the permission and let the user decide
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        
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
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.battery_optimization_proceed),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                TextButton(
                    onClick = {
                        PreferencesManager.setBatteryOptimizationDialogDismissed(context, true)
                        Timber.i("Battery optimization dialog dismissed permanently")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_optimization_dont_show))
                }
                
                TextButton(
                    onClick = {
                        Timber.i("Battery optimization dialog cancelled")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_optimization_cancel))
                }
            }
        }
    )
}

fun isXiaomiDevice(): Boolean {
    return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) || Build.MANUFACTURER.equals("Redmi", ignoreCase = true)
}
