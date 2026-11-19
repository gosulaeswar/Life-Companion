package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.security.PersonalAiKeyState

private const val AI_STUDIO_KEY_URL = "https://aistudio.google.com/apikey"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalAiKeyCard(
    keyState: PersonalAiKeyState,
    isTesting: Boolean,
    feedback: Pair<Boolean, String>?,
    onSaveAndTest: (String) -> Unit,
    onTestSavedKey: () -> Unit,
    onRemoveKey: () -> Unit,
    onDismissFeedback: () -> Unit = {},
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editorExpanded by remember(initiallyExpanded, keyState.hasPersonalKey) {
        mutableStateOf(initiallyExpanded)
    }
    var draftKey by remember { mutableStateOf("") }
    var showPlainDraft by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .testTag("personal_ai_key_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (keyState.hasPersonalKey) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (keyState.hasPersonalKey) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Icon(
                            imageVector = if (keyState.hasPersonalKey) Icons.Rounded.CloudDone else Icons.Rounded.Key,
                            contentDescription = "Cloud AI connection",
                            tint = if (keyState.hasPersonalKey) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Cloud AI Connection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (keyState.hasPersonalKey) {
                                "Using your Personal Gemini API Key"
                            } else {
                                "No App Check debug token? Use your own Gemini key"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Active Engine Pill
            Surface(
                shape = CircleShape,
                color = if (keyState.hasPersonalKey) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (keyState.hasPersonalKey) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (keyState.hasPersonalKey) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Active engine: ${keyState.activeEngineLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (keyState.hasPersonalKey) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }

            if (keyState.hasPersonalKey) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Saved Personal Key (Encrypted)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = keyState.maskedPreview,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.testTag("personal_ai_key_masked_preview")
                                )
                            }
                            TextButton(
                                onClick = { editorExpanded = !editorExpanded },
                                modifier = Modifier.testTag("personal_ai_key_replace_toggle")
                            ) {
                                Text(if (editorExpanded) "Close" else "Replace")
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onTestSavedKey,
                                enabled = !isTesting,
                                modifier = Modifier.testTag("personal_ai_key_test_saved_button")
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testing...")
                                } else {
                                    Text("Test Connection")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    draftKey = ""
                                    editorExpanded = false
                                    onRemoveKey()
                                },
                                enabled = !isTesting,
                                modifier = Modifier.testTag("personal_ai_key_remove_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove Key")
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "For sideloaded or family-shared APKs, each person can enter their own free Gemini API key here to run Voice AI, Prescription Scanner, Lab Explainer, and Meal Planner without Firebase App Check debug tokens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = { editorExpanded = !editorExpanded },
                    modifier = Modifier.testTag("personal_ai_key_setup_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (editorExpanded) "Hide Key Setup" else "Enter Personal Gemini API Key")
                }
            }

            AnimatedVisibility(visible = editorExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = draftKey,
                        onValueChange = { draftKey = it },
                        label = { Text("Gemini API Key (AIza...)") },
                        placeholder = { Text("Paste key from Google AI Studio") },
                        singleLine = true,
                        visualTransformation = if (showPlainDraft) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { showPlainDraft = !showPlainDraft },
                                modifier = Modifier.testTag("personal_ai_key_visibility_toggle")
                            ) {
                                Icon(
                                    imageVector = if (showPlainDraft) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = if (showPlainDraft) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("personal_gemini_key_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val pasted = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                if (!pasted.isNullOrBlank()) {
                                    draftKey = pasted
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("personal_gemini_key_paste_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste")
                        }

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_KEY_URL))
                                    )
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "Open aistudio.google.com/apikey in your browser",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.testTag("personal_gemini_key_get_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Get Free Key (AI Studio)")
                        }
                    }

                    Button(
                        onClick = {
                            onSaveAndTest(draftKey)
                            draftKey = ""
                            showPlainDraft = false
                        },
                        enabled = !isTesting && draftKey.trim().isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("personal_gemini_key_save_button")
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving & Testing Connection...")
                        } else {
                            Text("Save & Test Connection")
                        }
                    }
                }
            }

            feedback?.let { (isSuccess, message) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSuccess) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("personal_ai_key_feedback_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSuccess) "✅ $message" else "⚠️ $message",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSuccess) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissFeedback) {
                            Text("OK")
                        }
                    }
                }
            }

            Text(
                text = "🔐 Encrypted locally with Android Keystore (AES-256-GCM). Never synced to cloud, never included in exported backups, and never bundled inside shared APKs.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
