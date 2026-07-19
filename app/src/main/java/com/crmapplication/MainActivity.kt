package com.crmapplication

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.crmapplication.ui.CrmNavGraph
import com.crmapplication.ui.theme.CRMApplicationTheme
import com.crmapplication.utils.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class CrmApplication : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Resolve the saved theme BEFORE the first composition. Collecting with initial = null made
        // the app paint the system theme for one frame, then flip to the saved value once DataStore
        // emitted — a visible flash (made worse by the cross-fade). This one-shot blocking read is a
        // fast local DataStore lookup, so frame 1 is already the correct theme.
        val initialDark = runBlocking { themeManager.darkMode.first() }

        setContent {
            // Seed with the pre-resolved value so there's no startup flip; keep collecting so the
            // Profile toggle still animates live.
            val darkPref by themeManager.darkMode.collectAsState(initial = initialDark)
            val darkTheme = darkPref ?: isSystemInDarkTheme()
            CRMApplicationTheme(darkTheme = darkTheme) {
                CrmNavGraph()
            }
        }
    }
}
