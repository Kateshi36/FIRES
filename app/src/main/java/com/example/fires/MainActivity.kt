package com.example.fires

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.fires.ui.navigation.AppNavHost
import com.example.fires.ui.theme.FIRESTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FIRESTheme {
                AppNavHost()
            }
        }
    }
}
