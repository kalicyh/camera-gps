package com.saschl.cameragps

import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.saschl.cameragps.service.FileTree
import com.saschl.cameragps.service.GlobalExceptionHandler
import com.saschl.cameragps.ui.theme.CameraGpsTheme
import com.saschl.cameragps.service.CameraDeviceManager
import com.saschl.cameragps.ui.BatteryOptimizationDialog
import com.saschl.cameragps.ui.SettingsScreen
import com.saschl.cameragps.ui.WelcomeScreen
import com.saschl.cameragps.utils.PreferencesManager
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)


        if (Timber.treeCount == 0) {
            FileTree.initialize(this)
            Timber.plant(Timber.DebugTree(), FileTree(this))

            // Set up global exception handler to log crashes
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(defaultHandler))
        }

        Timber.i("created MainActivity")
        setContent {
            CameraGpsTheme {
                AppContent()
            }
        }
    }

    @Composable
    private fun AppContent() {
        val context = LocalContext.current
        var showWelcome by remember { mutableStateOf(PreferencesManager.isFirstLaunch(context)) }
        var showSettings by remember { mutableStateOf(false) }
        
        // Check if battery optimization dialog should be shown
        val powerManager = context.getSystemService<PowerManager>()
        val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true // No battery optimization on older Android versions
        }
        
        var showBatteryOptimizationDialog by remember { 
            mutableStateOf(
                !PreferencesManager.isBatteryOptimizationDialogDismissed(context) && 
                !isIgnoringBatteryOptimizations
            ) 
        }

        when {
            showWelcome -> {
                WelcomeScreen(
                    onGetStarted = {
                        PreferencesManager.setFirstLaunchCompleted(context)
                        showWelcome = false
                        Timber.i("Welcome screen completed, navigating to main app")
                    }
                )
            }
            showSettings -> {
                SettingsScreen(
                    onBackClick = {
                        showSettings = false
                    }
                )
            }
            else -> {
                // Check battery optimization status when entering main screen
                LaunchedEffect(Unit) {
                    val currentBatteryStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                    } else {
                        true
                    }
                    
                    // Update dialog visibility based on current status
                    showBatteryOptimizationDialog = !PreferencesManager.isBatteryOptimizationDialogDismissed(context) && 
                                                     !currentBatteryStatus
                }
                
                // Show the main camera device manager
                CameraDeviceManager(
                    onSettingsClick = {
                        showSettings = true
                    }
                )
                
                // Show battery optimization dialog overlay if needed
                if (showBatteryOptimizationDialog) {
                    BatteryOptimizationDialog(
                        onDismiss = {
                            showBatteryOptimizationDialog = false
                        }
                    )
                }
            }
        }
    }
}
