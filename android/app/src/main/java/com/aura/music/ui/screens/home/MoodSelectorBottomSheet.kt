package com.aura.music.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.music.data.model.MoodCategory
import com.aura.music.ui.utils.MoodGradientMapper
import com.aura.music.ui.utils.MoodIconMapper

/**
 * Bottom sheet dialog for mood selection.
 * Displays mood categories in a responsive grid layout.
 * Follows Material Design 3 guidelines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSelectorBottomSheet(
    moods: List<MoodCategory>,
    onDismiss: () -> Unit,
    onMoodSelected: (MoodCategory) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Find Your Mood",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Set the vibe for your music",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Mood Grid
            if (moods.isEmpty()) {
                EmptyMoodState()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(moods) { mood ->
                        MoodCard(
                            mood = mood,
                            onClick = { 
                                onMoodSelected(mood)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual mood card with gradient background and ripple effect
 */
@Composable
private fun MoodCard(
    mood: MoodCategory,
    onClick: () -> Unit
) {
    // Get gradient colors for this mood
    val (gradientStart, gradientEnd) = MoodGradientMapper.getGradientForMood(mood.title)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        gradientStart.copy(alpha = MoodGradientMapper.getGradientAlpha()),
                        gradientEnd.copy(alpha = MoodGradientMapper.getGradientAlpha())
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mood icon - Material Design icon mapped to mood
            Icon(
                imageVector = MoodIconMapper.getIconForMood(mood.title),
                contentDescription = MoodIconMapper.getIconContentDescription(mood.title),
                modifier = Modifier.size(40.dp),
                tint = Color.White.copy(alpha = 0.95f)
            )
            
            // Mood name
            Text(
                text = mood.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/**
 * Empty state when no moods are available
 */
@Composable
private fun EmptyMoodState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No moods available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
