package com.saschl.cameragps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.saschl.cameragps.service.FileTree
import com.saschl.cameragps.service.GlobalExceptionHandler
import com.saschl.cameragps.ui.theme.CameraGpsTheme
import com.saschl.cameragps.service.CameraDeviceManager
import com.saschl.cameragps.ui.WelcomeScreen
import com.saschl.cameragps.utils.PreferencesManager
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        //if(Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            enableEdgeToEdge()
        //}

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
           /* if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && !isSystemInDarkTheme()) {
                // WHYYYY
                StatusBarProtection()
            }*/

        }

    }

    @Composable
    private fun AppContent() {
        val context = LocalContext.current
        var showWelcome by remember { mutableStateOf(PreferencesManager.isFirstLaunch(context)) }

        if (showWelcome) {
            WelcomeScreen(
                onGetStarted = {
                    PreferencesManager.setFirstLaunchCompleted(context)
                    showWelcome = false
                    Timber.i("Welcome screen completed, navigating to main app")
                }
            )
        } else {
            CameraDeviceManager()
        }
    }

    @Composable
    private fun StatusBarProtection(
        color: Color = MaterialTheme.colorScheme.secondary,
        heightProvider: () -> Float = calculateGradientHeight(),
    ) {

        Canvas(Modifier.fillMaxSize()) {
            val calculatedHeight = heightProvider()
            val gradient = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 1f),
                    color.copy(alpha = .8f),
                    color.copy(alpha = .6f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = calculatedHeight
            )
            drawRect(
                brush = gradient,
                size = Size(size.width, calculatedHeight),
            )
        }
    }

    @Composable
    fun calculateGradientHeight(): () -> Float {
        val statusBars = WindowInsets.statusBars
        val density = LocalDensity.current
        return { statusBars.getTop(density).times(1.2f) }
    }
}
