package io.github.catlinux.criba

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.catlinux.criba.ui.navigation.CribaNavHost
import io.github.catlinux.criba.ui.theme.CribaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CribaTheme {
                CribaNavHost()
            }
        }
    }
}
