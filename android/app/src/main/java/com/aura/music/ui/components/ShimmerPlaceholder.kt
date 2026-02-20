package com.aura.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shimmer Loading Effect
 * 
 * Provides a professional shimmer/skeleton loading animation
 * for better perceived performance while content loads.
 */

@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnimation - 500f, translateAnimation - 500f),
        end = Offset(translateAnimation, translateAnimation)
    )
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

/**
 * Shimmer placeholder for a song item in horizontal scroll
 */
@Composable
fun ShimmerSongItem(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(140.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Album art placeholder
        ShimmerEffect(
            modifier = Modifier
                .size(140.dp)
                .aspectRatio(1f),
            shape = RoundedCornerShape(12.dp)
        )
        
        // Title placeholder
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            shape = RoundedCornerShape(4.dp)
        )
        
        // Artist placeholder
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

/**
 * Shimmer placeholder for the entire "Recommended for You" section
 */
@Composable
fun ShimmerRecommendedSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section header placeholder
        ShimmerEffect(
            modifier = Modifier
                .width(180.dp)
                .height(24.dp),
            shape = RoundedCornerShape(4.dp)
        )
        
        // Horizontal row of song cards
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                ShimmerSongItem()
            }
        }
    }
}

/**
 * Shimmer placeholder for top artist item
 */
@Composable
fun ShimmerArtistItem(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Artist image placeholder (circular)
        ShimmerEffect(
            modifier = Modifier
                .size(100.dp),
            shape = CircleShape
        )
        
        // Artist name placeholder
        ShimmerEffect(
            modifier = Modifier
                .width(80.dp)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

/**
 * Shimmer placeholder for playlist card
 */
@Composable
fun ShimmerPlaylistCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(160.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Playlist cover placeholder
        ShimmerEffect(
            modifier = Modifier
                .size(160.dp)
                .aspectRatio(1f),
            shape = RoundedCornerShape(8.dp)
        )
        
        // Title placeholder
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            shape = RoundedCornerShape(4.dp)
        )
        
        // Description placeholder
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

/**
 * Shimmer placeholder for daily mix card
 */
@Composable
fun ShimmerDailyMixCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(180.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mix cover with gradient placeholder
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(12.dp)
        )
        
        // Title placeholder
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(16.dp),
            shape = RoundedCornerShape(4.dp)
        )
        
        // Subtitle placeholder
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(12.dp),
            shape = RoundedCornerShape(4.dp)
        )
    }
}
