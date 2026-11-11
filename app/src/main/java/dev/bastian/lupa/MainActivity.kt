package dev.bastian.lupa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.bastian.lupa.ui.LupaNavHost
import dev.bastian.lupa.ui.theme.LupaTheme

class MainActivity : ComponentActivity() {
    /** Grows each time something asks for the Changes screen (the analysis notification). */
    private var openChangesToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a rotation the same intent comes back; only a fresh launch should navigate.
        if (savedInstanceState == null) handle(intent)
        setContent {
            LupaTheme {
                LupaNavHost(openChangesToken)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_CHANGES, false) == true) openChangesToken++
    }

    companion object {
        const val EXTRA_OPEN_CHANGES = "dev.bastian.lupa.OPEN_CHANGES"
    }
}
