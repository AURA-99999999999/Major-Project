package com.aura.music.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.music.player.MusicService
import com.aura.music.player.AudioEffectsState
import com.aura.music.player.BandState
import kotlin.math.absoluteValue
import kotlin.math.abs

/**
 * Full-screen equalizer interface with real-time audio enhancement controls.
 * 
 * Features:
 * - Multi-band equalizer with frequency sliders
 * - Preset selector (Pop, Rock, Jazz, Classical, etc.)
 * - Bass boost control
 * - Virtualizer control
 * - Reset to default
 * - Material Design 3 styling
 */
/**
 * EqualizerScreen with MusicService integration.
 * Connects to the service's AudioEffectsManager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    musicService: MusicService?,
    onNavigateBack: () -> Unit
) {
    val effectsManager = musicService?.getAudioEffectsManager() ?: return
    val effectsState by effectsManager.state.collectAsState()
    
    EqualizerScreenContent(
        effectsState = effectsState,
        onBandLevelChanged = { band, level ->
            effectsManager.setBandLevel(band, level)
        },
        onPresetSelected = { preset ->
            effectsManager.usePreset(preset)
        },
        onBassBoostChanged = { strength ->
            effectsManager.setBassBoostStrength(strength)
        },
        onVirtualizerChanged = { strength ->
            effectsManager.setVirtualizerStrength(strength)
        },
        onReset = {
            effectsManager.reset()
        },
        onNavigateBack = onNavigateBack
    )
}

/**
 * EqualizerScreen content composable with audio enhancement controls.
 * This is the internal implementation that receives state and callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerScreenContent(
    effectsState: AudioEffectsState,
    onBandLevelChanged: (Short, Short) -> Unit,
    onPresetSelected: (Short) -> Unit,
    onBassBoostChanged: (Short) -> Unit,
    onVirtualizerChanged: (Short) -> Unit,
    onReset: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Audio Equalizer",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onReset) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        
        if (!effectsState.isAvailable) {
            // Unavailable state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "🎵",
                        fontSize = 48.sp
                    )
                    Text(
                        text = "Audio enhancements unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Start playing music to enable the equalizer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Main equalizer content
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 120.dp), // Extra bottom padding for mini-player
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Presets section
                PresetsSection(
                    presets = effectsState.presets,
                    currentPreset = effectsState.currentPreset,
                    onPresetSelected = onPresetSelected
                )
                
                // Equalizer bands
                EqualizerBandsSection(
                    bands = effectsState.bands,
                    bandLevelRange = effectsState.bandLevelRange,
                    onBandLevelChanged = onBandLevelChanged
                )
                
                // Bass boost
                BassBoostSection(
                    strength = effectsState.bassBoostStrength,
                    onStrengthChanged = onBassBoostChanged
                )
                
                // Virtualizer
                VirtualizerSection(
                    strength = effectsState.virtualizerStrength,
                    onStrengthChanged = onVirtualizerChanged
                )
            }
        }
    }
}

@Composable
private fun PresetsSection(
    presets: List<String>,
    currentPreset: Int,
    onPresetSelected: (Short) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Presets",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(presets) { index, presetName ->
                PresetChip(
                    name = presetName,
                    isSelected = index == currentPreset,
                    onClick = { onPresetSelected(index.toShort()) }
                )
            }
        }
    }
}

@Composable
private fun PresetChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) 
                MaterialTheme.colorScheme.onPrimaryContainer 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EqualizerBandsSection(
    bands: List<BandState>,
    bandLevelRange: Pair<Int, Int>,
    onBandLevelChanged: (Short, Short) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Equalizer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bands.forEach { band ->
                EqualizerBandSlider(
                    band = band,
                    levelRange = bandLevelRange,
                    onLevelChanged = { level ->
                        onBandLevelChanged(band.index, level)
                    }
                )
            }
        }
    }
}

@Composable
private fun EqualizerBandSlider(
    band: BandState,
    levelRange: Pair<Int, Int>,
    onLevelChanged: (Short) -> Unit
) {
    // Normalize to user-friendly -15 to +15 dB range
    // Convert millibels to dB: band.level is in millibels (-1500 to +1500)
    val dbValue = band.level.toFloat() / 100f  // Convert to dB (-15.0 to +15.0)
    val sliderPosition = dbValue + 15f         // Map to 0-30 slider range
    
    // Color based on adjustment type
    val thumbColor = when {
        dbValue > 0.5f -> Color(0xFF4CAF50)   // Green for boost
        dbValue < -0.5f -> Color(0xFFFF5722)  // Orange-red for cut
        else -> MaterialTheme.colorScheme.primary  // Neutral
    }
    
    Column(
        modifier = Modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Level display: normalized dB value with color feedback
        Text(
            text = when {
                dbValue > 0 -> "+%.1f".format(dbValue)
                dbValue < 0 -> "%.1f".format(dbValue)
                else -> "0.0"
            },
            style = MaterialTheme.typography.labelSmall,
            color = thumbColor,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        
        // Vertical track with thumb indicator
        Box(
            modifier = Modifier
                .height(240.dp)
                .width(64.dp),
            contentAlignment = Alignment.Center
        ) {
            // Neutral background track - shows full range
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .align(Alignment.Center)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(3.dp)
                    )
            )
            
            // Center baseline at 0 dB - reference line
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .width(24.dp)
                    .align(Alignment.Center)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        RoundedCornerShape(1.5.dp)
                    )
            )
            
            // Interactive slider - thumb represents current level
            Slider(
                value = sliderPosition,
                onValueChange = { position ->
                    // Convert UI position (0-30) back to dB (-15 to +15)
                    val db = position - 15f
                    // Convert dB to millibels (multiply by 100)
                    val millibels = (db * 100f).toInt().toShort()
                    onLevelChanged(millibels)
                },
                valueRange = 0f..30f,  // Fixed range: -15 dB to +15 dB
                steps = 29,  // 30 total positions for discrete dB steps
                modifier = Modifier
                    .fillMaxHeight()
                    .rotate(270f)
                    .width(240.dp),
                colors = SliderDefaults.colors(
                    thumbColor = thumbColor,
                    activeTrackColor = Color.Transparent,  // No progress fill
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
        
        // Frequency label
        Text(
            text = formatFrequency(band.frequency),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun BassBoostSection(
    strength: Int,
    onStrengthChanged: (Short) -> Unit
) {
    EffectControlSection(
        title = "Bass Boost",
        icon = "🔊",
        strength = strength,
        onStrengthChanged = onStrengthChanged
    )
}

@Composable
private fun VirtualizerSection(
    strength: Int,
    onStrengthChanged: (Short) -> Unit
) {
    EffectControlSection(
        title = "Virtualizer",
        icon = "🎧",
        strength = strength,
        onStrengthChanged = onStrengthChanged
    )
}

@Composable
private fun EffectControlSection(
    title: String,
    icon: String,
    strength: Int,
    onStrengthChanged: (Short) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = icon,
                        fontSize = 24.sp
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "${strength / 10}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Add padding above and below slider for better touch target
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Slider(
                    value = strength.toFloat(),
                    onValueChange = { onStrengthChanged(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    steps = 99, // 100 discrete steps (0-1000 in steps of 10)
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp), // Increased height for better touch target
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun formatFrequency(hz: Int): String {
    return when {
        hz >= 1000 -> "${hz / 1000}kHz"
        else -> "${hz}Hz"
    }
}
