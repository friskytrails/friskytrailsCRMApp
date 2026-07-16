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
import javax.inject.Inject

@HiltAndroidApp
class CrmApplication : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {

            val darkPref by themeManager.darkMode.collectAsState(initial = null)
            val darkTheme = darkPref ?: isSystemInDarkTheme()
            CRMApplicationTheme(darkTheme = darkTheme) {
                CrmNavGraph()
            }
        }
    }
}
