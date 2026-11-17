package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.PrivacyVaultBadge
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.ProfileFormState
import com.example.ui.viewmodel.ProfileSetupViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    formState: ProfileFormState,
    viewModel: ProfileSetupViewModel,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val genders = listOf("Female", "Male", "Non-Binary", "Prefer not to say", "Custom")
    val activities = listOf("Light", "Moderate", "Active")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 580.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canNavigateBack) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (formState.isEditing) {
                            stringResource(id = R.string.profile_edit_title)
                        } else {
                            stringResource(id = R.string.profile_setup_title)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Saved privately on this device with zero cloud sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PrivacyVaultBadge(text = "Stored only on this device")

            // Identity Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Personal Details",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = formState.fullName,
                        onValueChange = viewModel::updateName,
                        label = { Text("Your Name / Nickname") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = formState.ageText,
                            onValueChange = viewModel::updateAge,
                            label = { Text("Age") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_age_input")
                        )
                        OutlinedTextField(
                            value = formState.heightCmText,
                            onValueChange = viewModel::updateHeightCm,
                            label = { Text("Height (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = formState.weightKgText,
                            onValueChange = viewModel::updateWeightKg,
                            label = { Text("Weight (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "Gender Identity (Inclusive)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        genders.forEach { option ->
                            FilterChip(
                                selected = formState.genderIdentity == option,
                                onClick = { viewModel.updateGenderIdentity(option) },
                                label = { Text(option) },
                                modifier = Modifier.testTag("gender_chip_${option.lowercase().replace(" ", "_")}")
                            )
                        }
                    }

                    AnimatedVisibility(visible = formState.genderIdentity == "Custom") {
                        OutlinedTextField(
                            value = formState.customGenderLabel,
                            onValueChange = viewModel::updateCustomGender,
                            label = { Text("Custom identity label") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Activity & Hydration Target Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Daily Activity & Hydration Goal",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        activities.forEach { level ->
                            FilterChip(
                                selected = formState.activityLevel == level,
                                onClick = { viewModel.updateActivityLevel(level) },
                                label = { Text(level) },
                                modifier = Modifier.testTag("activity_chip_${level.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Water Target",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${formState.dailyWaterGoalMl} ml",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoMetricFont
                            ),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Slider(
                        value = formState.dailyWaterGoalMl.toFloat(),
                        onValueChange = { viewModel.updateDailyWaterGoal((it / 50f).roundToInt() * 50) },
                        valueRange = 1200f..4500f,
                        modifier = Modifier.testTag("profile_water_goal_slider")
                    )
                }
            }

            // Cycle Tracking Opt-In Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include Cycle & Period Tracker",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Always free. Toggle visibility anytime based on your needs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = formState.cycleTrackingEnabled,
                            onCheckedChange = viewModel::updateCycleTrackingEnabled,
                            modifier = Modifier.testTag("profile_cycle_switch")
                        )
                    }

                    AnimatedVisibility(visible = formState.cycleTrackingEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Typical Cycle Length", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${formState.averageCycleLengthDays} days",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Slider(
                                value = formState.averageCycleLengthDays.toFloat(),
                                onValueChange = { viewModel.updateAverageCycleLength(it.roundToInt()) },
                                valueRange = 21f..42f
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Typical Period Duration", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${formState.averagePeriodDurationDays} days",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Slider(
                                value = formState.averagePeriodDurationDays.toFloat(),
                                onValueChange = { viewModel.updateAveragePeriodDuration(it.roundToInt()) },
                                valueRange = 2f..10f
                            )
                        }
                    }
                }
            }

            if (formState.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = formState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Button(
                onClick = { viewModel.saveProfile(onSaved) },
                enabled = !formState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("profile_save_button"),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(
                    text = if (formState.isEditing) "Update Local Profile" else "Save & Enter LifeWeave",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
