package com.watsup.azimute_find

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.watsup.azimute_find.ui.MapScreen
import com.watsup.azimute_find.ui.theme.AzimuteFindTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AzimuteFindTheme {
                MapScreen()
            }
        }
    }
}
