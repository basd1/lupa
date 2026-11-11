package dev.bastian.lupa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.bastian.lupa.ui.LupaNavHost
import dev.bastian.lupa.ui.theme.LupaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LupaTheme {
                LupaNavHost()
            }
        }
    }
}
