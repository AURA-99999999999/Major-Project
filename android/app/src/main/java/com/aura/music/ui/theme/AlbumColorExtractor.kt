package com.aura.music.ui.theme

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts dominant and vibrant colors from album artwork
 * Uses Android Palette API for intelligent color analysis
 */
object AlbumColorExtractor {

    private val colorCache = mutableMapOf<String, ThemeColors>()

    /**
     * Extract theme colors from album artwork bitmap
     * Runs on IO dispatcher to avoid blocking main thread
     *
     * @param bitmap Album artwork bitmap
     * @param cacheKey Optional cache key to avoid recomputation
     * @return ThemeColors with extracted palette
     */
    suspend fun extractThemeColors(
        bitmap: Bitmap,
        cacheKey: String? = null
    ): ThemeColors = withContext(Dispatchers.IO) {
        // Check cache first
        cacheKey?.let { key ->
            colorCache[key]?.let { return@withContext it }
        }

        try {
            val palette = Palette.from(bitmap).generate()

            val colors = ThemeColors(
                vibrant = palette.vibrantSwatch?.let { Color(it.rgb) },
                vibrantDark = palette.darkVibrantSwatch?.let { Color(it.rgb) },
                vibrantLight = palette.lightVibrantSwatch?.let { Color(it.rgb) },
                muted = palette.mutedSwatch?.let { Color(it.rgb) },
                mutedDark = palette.darkMutedSwatch?.let { Color(it.rgb) },
                mutedLight = palette.lightMutedSwatch?.let { Color(it.rgb) },
                dominant = Color(palette.dominantSwatch?.rgb ?: 0xFF1A1A1A.toInt())
            )

            // Cache the result
            cacheKey?.let { key ->
                colorCache[key] = colors
            }

            colors
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty theme colors on error
            ThemeColors()
        }
    }

    /**
     * Extract theme colors from bitmap with fallback handling
     * Prioritizes vibrant colors, falls back to muted/dominant
     *
     * @param bitmap Album artwork bitmap
     * @param fallbackAccent Accent color to use if extraction fails
     * @param cacheKey Optional cache key
     * @return Best available color or fallback
     */
    suspend fun extractPrimaryColor(
        bitmap: Bitmap,
        fallbackAccent: AccentColor = AccentColor.BLUE,
        cacheKey: String? = null
    ): Color = withContext(Dispatchers.IO) {
        val colors = extractThemeColors(bitmap, cacheKey)

        // Priority: vibrant > vibrant dark > muted > muted dark > dominant > fallback
        colors.vibrant
            ?: colors.vibrantDark
            ?: colors.muted
            ?: colors.mutedDark
            ?: colors.dominant
            ?: AccentPaletteProvider.getPalette(fallbackAccent).primary
    }

    /**
     * Clear cache to free memory
     */
    fun clearCache() {
        colorCache.clear()
    }

    /**
     * Clear specific cache entry
     */
    fun clearCacheEntry(key: String) {
        colorCache.remove(key)
    }

    /**
     * Get cache size (for testing/debugging)
     */
    fun getCacheSize(): Int = colorCache.size
}
