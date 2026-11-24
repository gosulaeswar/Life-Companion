package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CobaltBlueSecondary
import com.example.ui.theme.LocalIsSpatialDarkTheme
import com.example.ui.theme.LuminousBlueSecondary
import com.example.ui.theme.RadiantOrangePrimary
import com.example.ui.theme.SolarOrangePrimary
import com.example.ui.theme.SpatialDarkBase
import com.example.ui.theme.SpatialDarkBlueOrb
import com.example.ui.theme.SpatialDarkCyanOrb
import com.example.ui.theme.SpatialDarkOrangeOrb
import com.example.ui.theme.SpatialGlassDark
import com.example.ui.theme.SpatialGlassLight
import com.example.ui.theme.SpatialLightBase
import com.example.ui.theme.SpatialLightBlueOrb
import com.example.ui.theme.SpatialLightBlueTop
import com.example.ui.theme.SpatialLightCyanOrb
import com.example.ui.theme.SpatialLightOrangeOrb
import com.example.ui.theme.SpatialLightPeachWash

/**
 * Full-bleed Spatial Orange & Blue atmospheric backdrop painted with multi-layered radial light
 * orbs, diagonal depth sweeps, and subtle spatial orbital rings. Adapts seamlessly to Light and
 * Dark modes so floating translucent glass cards appear suspended in 3D space.
 */
@Composable
fun SpatialOrangeBlueBackdrop(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = LocalIsSpatialDarkTheme.current,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.drawBehind {
            val w = size.width
            val h = size.height

            // 1. Diagonal atmospheric Orange ↔ Blue spatial sweep
            val baseColors = if (darkTheme) {
                listOf(
                    Color(0xFF071330), // deep royal midnight blue
                    SpatialDarkBase,
                    Color(0xFF1A1426), // twilight cobalt-indigo bridge
                    Color(0xFF28150C)  // warm solar orange horizon ember
                )
            } else {
                listOf(
                    SpatialLightBlueTop, // airy azure sky
                    SpatialLightBase,
                    Color(0xFFF3F5FF),
                    SpatialLightPeachWash // luminous warm apricot-orange horizon
                )
            }
            drawRect(
                brush = Brush.linearGradient(
                    colors = baseColors,
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            // 2. Top-left Cobalt Blue ambient depth orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (darkTheme) {
                        listOf(
                            SpatialDarkBlueOrb.copy(alpha = 0.52f),
                            SpatialDarkCyanOrb.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            SpatialLightBlueOrb.copy(alpha = 0.30f),
                            SpatialLightCyanOrb.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    },
                    center = Offset(w * 0.14f, h * 0.10f),
                    radius = maxOf(w, h) * 0.62f
                ),
                center = Offset(w * 0.14f, h * 0.10f),
                radius = maxOf(w, h) * 0.62f
            )

            // 3. Upper-right warm Solar Orange spatial bloom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (darkTheme) {
                        listOf(
                            SpatialDarkOrangeOrb.copy(alpha = 0.42f),
                            RadiantOrangePrimary.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            SpatialLightOrangeOrb.copy(alpha = 0.32f),
                            Color(0xFFFFB067).copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    },
                    center = Offset(w * 0.92f, h * 0.24f),
                    radius = maxOf(w, h) * 0.54f
                ),
                center = Offset(w * 0.92f, h * 0.24f),
                radius = maxOf(w, h) * 0.54f
            )

            // 4. Lower-left electric blue spatial counter-glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (darkTheme) {
                        listOf(
                            SpatialDarkCyanOrb.copy(alpha = 0.30f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            SpatialLightBlueOrb.copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    },
                    center = Offset(w * 0.12f, h * 0.78f),
                    radius = maxOf(w, h) * 0.50f
                ),
                center = Offset(w * 0.12f, h * 0.78f),
                radius = maxOf(w, h) * 0.50f
            )

            // 5. Bottom-right sunset orange ambient anchor
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (darkTheme) {
                        listOf(
                            SpatialDarkOrangeOrb.copy(alpha = 0.36f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            SpatialLightOrangeOrb.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    },
                    center = Offset(w * 0.86f, h * 0.90f),
                    radius = maxOf(w, h) * 0.52f
                ),
                center = Offset(w * 0.86f, h * 0.90f),
                radius = maxOf(w, h) * 0.52f
            )

            // 6. Subtle spatial glass orbital depth rings
            val ringColor = if (darkTheme) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.35f)
            drawCircle(
                color = ringColor,
                radius = w * 0.72f,
                center = Offset(w * 0.88f, h * 0.16f),
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = ringColor,
                radius = w * 0.58f,
                center = Offset(w * 0.10f, h * 0.84f),
                style = Stroke(width = 1.2f)
            )
        },
        content = content
    )
}

data class SpatialNavItemSpec(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val selected: Boolean,
    val testTag: String,
    val onClick: () -> Unit
)

/**
 * Floating translucent Spatial Glass navigation capsule dock with glowing Orange-to-Blue active
 * pills, specular rim lighting, >=48dp touch targets, and proper system navigation bar padding.
 */
@Composable
fun FloatingSpatialBottomDock(
    items: List<SpatialNavItemSpec>,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = LocalIsSpatialDarkTheme.current
) {
    val dockFill = if (darkTheme) {
        SpatialGlassDark.copy(alpha = 0.92f)
    } else {
        SpatialGlassLight.copy(alpha = 0.92f)
    }
    val rimBrush = Brush.linearGradient(
        colors = if (darkTheme) {
            listOf(
                LuminousBlueSecondary.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.24f),
                RadiantOrangePrimary.copy(alpha = 0.55f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.95f),
                CobaltBlueSecondary.copy(alpha = 0.28f),
                SolarOrangePrimary.copy(alpha = 0.32f)
            )
        }
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = dockFill,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.2.dp, rimBrush)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val contentColor by animateColorAsState(
                        targetValue = if (item.selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "spatial_dock_color"
                    )
                    val activePillBrush = Brush.horizontalGradient(
                        colors = if (darkTheme) {
                            listOf(
                                RadiantOrangePrimary.copy(alpha = 0.30f),
                                LuminousBlueSecondary.copy(alpha = 0.26f)
                            )
                        } else {
                            listOf(
                                SolarOrangePrimary.copy(alpha = 0.20f),
                                CobaltBlueSecondary.copy(alpha = 0.16f)
                            )
                        }
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .semantics { selected = item.selected }
                            .clickable(role = Role.Tab, onClick = item.onClick)
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .testTag(item.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .then(
                                    if (item.selected) {
                                        Modifier.background(activePillBrush)
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating translucent Spatial Glass navigation rail for tablets and foldables.
 */
@Composable
fun FloatingSpatialNavRail(
    items: List<SpatialNavItemSpec>,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = LocalIsSpatialDarkTheme.current
) {
    val railFill = if (darkTheme) {
        SpatialGlassDark.copy(alpha = 0.90f)
    } else {
        SpatialGlassLight.copy(alpha = 0.90f)
    }
    val rimBrush = Brush.verticalGradient(
        colors = listOf(
            if (darkTheme) LuminousBlueSecondary.copy(alpha = 0.45f) else CobaltBlueSecondary.copy(alpha = 0.28f),
            if (darkTheme) RadiantOrangePrimary.copy(alpha = 0.45f) else SolarOrangePrimary.copy(alpha = 0.30f)
        )
    )

    Box(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 10.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = railFill,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.2.dp, rimBrush)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items.forEach { item ->
                    val contentColor = if (item.selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val activePillBrush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                        )
                    )
                    Column(
                        modifier = Modifier
                            .widthIn(min = 64.dp)
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .semantics { selected = item.selected }
                            .clickable(role = Role.Tab, onClick = item.onClick)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .testTag(item.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .then(if (item.selected) Modifier.background(activePillBrush) else Modifier)
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
