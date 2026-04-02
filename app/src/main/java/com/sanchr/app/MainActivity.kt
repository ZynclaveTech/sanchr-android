package com.sanchr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sanchr.core.designsystem.theme.SanchrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // TODO: Keep splash screen visible while checking auth state
        // splashScreen.setKeepOnScreenCondition { !viewModel.isReady.value }

        enableEdgeToEdge()

        setContent {
            SanchrTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SanchrNavHost()
                }
            }
        }
    }
}
