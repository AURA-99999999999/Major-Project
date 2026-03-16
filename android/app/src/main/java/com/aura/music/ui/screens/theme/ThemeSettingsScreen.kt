package com.aura.music.ui.screens.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.music.ui.theme.AccentColor
import com.aura.music.ui.theme.AccentPaletteProvider
import com.aura.music.ui.theme.GradientProvider
import com.aura.music.ui.theme.GradientTheme
import com.aura.music.ui.theme.ThemeManager
import com.aura.music.ui.theme.ThemeMode

/**
 * Complete theme settings UI for Profile screen
 * Allows users to customize:
 * - Theme mode (Dark/Light/System)
 * - Accent colors
 * - Gradient themes
 * - Dynamic album colors
 * - Minimal mode
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    themeManager: ThemeManager,
    onBackPress: () -> Unit
) {
    android.util.Log.d("ThemeSettingsScreen", "Composable entered, themeManager=$themeManager")
    val themeState by themeManager.themeState.collectAsState()
    android.util.Log.d("ThemeSettingsScreen", "ThemeState collected: $themeState")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Theme Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Theme Mode Section
            item {
                ThemeModeSection(
                    currentMode = themeState.themeMode,
                    onModeSelected = { themeManager.setThemeMode(it) }
                )
            }

            item { Divider() }

            // Accent Color Section  
            item {
                AccentColorSection(
                    currentAccent = themeState.accentColor,
                    onAccentSelected = { themeManager.setAccentColor(it) }
                )
            }

            item { Divider() }

            // Gradient Theme Section
            item {
                GradientThemeSection(
                    currentGradient = themeState.gradientTheme,
                    isDarkTheme = themeState.themeMode != ThemeMode.LIGHT,
                    onGradientSelected = { themeManager.setGradientTheme(it) }
                )
            }

            item { Divider() }

            // Dynamic Album Colors Section
            item {
                DynamicAlbumColorsSection(
                    enabled = themeState.dynamicAlbumColors,
                    onToggle = { themeManager.setDynamicAlbumColors(it) }
                )
            }

            item { Divider() }

            // Minimal Mode Section
            item {
                MinimalModeSection(
                    enabled = themeState.minimalMode,
                    onToggle = { themeManager.setMinimalMode(it) }
                )
            }

            // Preview Section
            item {
                PreviewSection(themeState)
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Theme mode selection
 */
@Composable
private fun ThemeModeSection(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Theme Mode",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                ModeButton(
                    label = mode.name,
                    isSelected = currentMode == mode,
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Theme mode button
 */
@Composable
private fun ModeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected)
            null
        else
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline
            )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Accent color grid selector
 */
@Composable
private fun AccentColorSection(
    currentAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Accent Color",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),  // Fixed height to prevent infinity constraints
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(AccentColor.entries) { accent ->
                AccentColorItem(
                    accent = accent,
                    isSelected = currentAccent == accent,
                    onClick = { onAccentSelected(accent) }
                )
            }
        }
    }
}

/**
 * Accent color circle selector item
 */
@Composable
private fun AccentColorItem(
    accent: AccentColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = AccentPaletteProvider.getPalette(accent)

    Box(
        modifier = Modifier
            .size(56.dp)
            .background(
                color = palette.primary,
                shape = CircleShape
            )
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.surface,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = palette.dark,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Gradient theme selector
 */
@Composable
private fun GradientThemeSection(
    currentGradient: GradientTheme,
    isDarkTheme: Boolean,
    onGradientSelected: (GradientTheme) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Gradient Theme",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GradientTheme.entries.forEach { gradient ->
                GradientThemeItem(
                    gradient = gradient,
                    isDarkTheme = isDarkTheme,
                    isSelected = currentGradient == gradient,
                    onClick = { onGradientSelected(gradient) }
                )
            }
        }
    }
}

/**
 * Individual gradient theme item
 */
@Composable
private fun GradientThemeItem(
    gradient: GradientTheme,
    isDarkTheme: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected)
            BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        else
            null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            brush = GradientProvider.getGradient(gradient, isDarkTheme)
                                ?: androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp)
                        )
                )

                Text(
                    text = gradient.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Dynamic album colors toggle
 */
@Composable
private fun DynamicAlbumColorsSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dynamic Album Colors",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Extract colors from album artwork",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

/**
 * Minimal mode toggle
 */
@Composable
private fun MinimalModeSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Minimal Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Distraction-free monochrome UI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

/**
 * Theme preview section
 */
@Composable
private fun PreviewSection(themeState: com.aura.music.ui.theme.ThemeState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Preview",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Current Theme",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${themeState.accentColor.displayName} • " +
                                "${themeState.gradientTheme.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
