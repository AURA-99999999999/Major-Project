package com.aura.music.ui.theme

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.aura.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Composable effect that observes song changes and extracts dynamic colors
 * Triggered when a new song starts, automatically updates ThemeManager
 * 
 * Features:
 * - Caches color extraction by videoId to prevent redundant processing
 * - Loads artwork bitmap efficiently using Coil
 * - Extracts multiple color variants (vibrant, muted, dominant)
 * - Updates theme on background coroutine to prevent frame drops
 * - Clears colors when playback stops
 * 
 * Usage:
 * ThemeColorEffect(currentSong, themeManager)
 * 
 * @param song Current song being played (null to clear colors)
 * @param themeManager ThemeManager instance to update colors
 */
@Composable
fun ThemeColorEffect(
    song: Song?,
    themeManager: ThemeManager?
) {
    val context = LocalContext.current
    
    // Effect: Update colors when song changes
    LaunchedEffect(song?.videoId) {
        if (song == null || themeManager == null) {
            // Clear colors when playback stops
            themeManager?.clearDynamicAlbumColors()
            return@LaunchedEffect
        }
        
        if (!themeManager.themeState.value.dynamicAlbumColors) return@LaunchedEffect

        try {
            val artworkUrl = song.thumbnail ?: return@LaunchedEffect
            
            Log.d("ThemeColorEffect", "→ Loading artwork for color extraction: ${song.title}")
            
            // Load bitmap from URL using Coil on Default dispatcher
            withContext(Dispatchers.Default) {
                try {
                    val imageLoader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(artworkUrl)
                        .size(512)  // Reasonable size for color extraction
                        .build()

                    val result = imageLoader.execute(request)
                    val drawable = result.drawable
                    
                    val bitmap = when (drawable) {
                        is android.graphics.drawable.BitmapDrawable -> drawable.bitmap
                        else -> {
                            Log.w("ThemeColorEffect", "⚠ Unexpected drawable type: ${drawable?.javaClass?.simpleName}")
                            null
                        }
                    }

                    if (bitmap != null && bitmap.width > 1 && bitmap.height > 1) {
                        Log.d("ThemeColorEffect", "✓ Artwork loaded: ${bitmap.width}x${bitmap.height}")
                        // Extract colors - ThemeManager handles caching internally
                        themeManager.updateDynamicAlbumColors(bitmap, cacheKey = song.videoId)
                    } else {
                        Log.w("ThemeColorEffect", "⚠ Invalid bitmap or drawable")
                    }
                } catch (e: Exception) {
                    Log.e("ThemeColorEffect", "✗ Error loading artwork from URL", e)
                }
            }
        } catch (e: Exception) {
            Log.e("ThemeColorEffect", "✗ Error in theme color extraction", e)
        }
    }
}
