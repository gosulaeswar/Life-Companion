package com.example.healthsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LifeCompanionTheme

class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifeCompanionTheme {
                HealthPermissionsRationaleContent(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun HealthPermissionsRationaleContent(onClose: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🛡️ LifeWeave Health Data Privacy",
                style = MaterialTheme.typography.headlineSmall
            )
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Why LifeWeave requests read-only Health Connect access",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "• Steps, distance, calories, workouts, sleep, heart rate, hydration, nutrition meals, and weight are read solely so your Fitbit, Google Fit, Samsung Health, Garmin, Oura, or diet app data appears inside your LifeWeave wellness & nutrition trackers.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• All synced fitness & nutrition records stay stored privately on your device inside LifeWeave's local vault. Zero records are sold, shared with advertisers, or uploaded to third-party servers.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• You control which categories are enabled and can grant or revoke any permission at any time without affecting manual tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Got It")
            }
        }
    }
}
