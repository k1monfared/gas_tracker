package com.k1.gastracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.k1.gastracker.ui.GasTrackerApp
import com.k1.gastracker.ui.theme.GasTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GasTrackerTheme {
                GasTrackerApp()
            }
        }
    }
}
