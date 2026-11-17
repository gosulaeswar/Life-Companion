package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.ai.configureAppCheck
import com.example.di.AppContainer
import com.example.ui.navigation.LifeCompanionApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                appContainer.alertSoundManager.rescheduleAllActiveReminders(
                    db = appContainer.database,
                    preferencesManager = appContainer.preferencesManager
                )
            }
        }
        setContent {
            LifeCompanionApp(container = appContainer)
        }
    }
}
