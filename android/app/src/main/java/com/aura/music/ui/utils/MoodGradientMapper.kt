package com.aura.music.ui.utils

import androidx.compose.ui.graphics.Color

/**
 * Centralized utility for mapping mood names to gradient color pairs.
 * Provides consistent, reusable gradient selection for mood-based UI components.
 * 
 * Features:
 * - Case-insensitive matching
 * - Synonym support for common mood variations
 * - Modern, vibrant gradient palettes
 * - Dark mode compatible colors
 * - Lightweight performance (simple map lookup)
 */
object MoodGradientMapper {
    
    /**
     * Maps a mood name to its corresponding gradient color pair (start, end).
     * Uses case-insensitive matching and handles common synonyms.
     * 
     * @param moodName The name of the mood (e.g., "Happy", "Chill", "Energetic")
     * @return Pair of Color values (startColor, endColor) for gradient
     */
    fun getGradientForMood(moodName: String): Pair<Color, Color> {
        return when (moodName.lowercase().trim()) {
            // Happy / Positive moods - Yellow to Orange
            "happy", "joy", "joyful", "cheerful", "positive" -> 
                Pair(Color(0xFFFFC837), Color(0xFFFF8008))
            
            // Energetic / Active moods - Red to Orange
            "energetic", "energy", "pump up", "energize", "active", "hype", "motivated", "motivation" -> 
                Pair(Color(0xFFFF512F), Color(0xFFF09819))
            
            // Chill / Relaxed moods - Blue to Purple
            "chill", "relax", "relaxed", "calm", "mellow", "peaceful", "peace" -> 
                Pair(Color(0xFF4FACFE), Color(0xFF00F2FE))
            
            // Sad / Melancholy moods - Blue to Indigo
            "sad", "melancholy", "blue", "down", "moody" -> 
                Pair(Color(0xFF667EEA), Color(0xFF764BA2))
            
            // Romantic / Love moods - Red to Pink
            "romantic", "love", "romance", "passionate" -> 
                Pair(Color(0xFFFF6B6B), Color(0xFFFFE66D))
            
            // Focus / Study moods - Teal to Blue
            "focus", "concentration", "study", "work", "productive" -> 
                Pair(Color(0xFF11998E), Color(0xFF38EF7D))
            
            // Workout / Fitness moods - Orange to Red
            "workout", "gym", "fitness", "exercise", "training", "sport" -> 
                Pair(Color(0xFFFF416C), Color(0xFFFF4B2B))
            
            // Party / Celebration moods - Pink to Purple
            "party", "celebration", "dance", "club", "nightlife", "fun" -> 
                Pair(Color(0xFFFF0099), Color(0xFF493240))
            
            // Sleep / Night moods - Deep Purple to Navy
            "sleep", "sleepy", "bedtime", "night", "dreamy" -> 
                Pair(Color(0xFF2E3192), Color(0xFF1BFFFF))
            
            // Angry / Aggressive moods - Dark Red to Orange
            "angry", "rage", "mad", "aggressive" -> 
                Pair(Color(0xFFEB3349), Color(0xFFF45C43))
            
            // Nostalgic / Reflective moods - Warm Purple to Rose
            "nostalgic", "nostalgia", "throwback", "memories", "reflective" -> 
                Pair(Color(0xFFDA4453), Color(0xFF89216B))
            
            // Excited moods - Yellow to Pink
            "excited", "excitement", "thrilled" -> 
                Pair(Color(0xFFFBDA61), Color(0xFFF76B1C))
            
            // Groovy / Funky moods - Purple to Pink
            "groovy", "funky", "disco", "retro" -> 
                Pair(Color(0xFFB92B27), Color(0xFF1565C0))
            
            // Commute / Travel moods - Sky Blue to Cyan
            "commute", "travel", "road trip", "drive", "journey" -> 
                Pair(Color(0xFF56CCF2), Color(0xFF2F80ED))
            
            // Feel good / Positive uplifting moods - Orange to Pink
            "feel good", "feel-good", "uplifting", "uplifted" -> 
                Pair(Color(0xFFFFD89B), Color(0xFF19547B))
            
            // Gaming / Play moods - Green to Blue
            "gaming", "game", "play", "esports", "gamer" -> 
                Pair(Color(0xFF36D1DC), Color(0xFF5B86E5))
            
            // Decades / Classic moods - Warm Vintage Sunset (Orange to Amber)
            "decades", "decade", "classic", "classics", "oldies" -> 
                Pair(Color(0xFFFFB75E), Color(0xFFED8F03))
            
            // Default fallback - Neutral Gray Gradient
            else -> Pair(Color(0xFF757F9A), Color(0xFFD7DDE8))
        }
    }
    
    /**
     * Returns the gradient opacity/alpha level for mood cards.
     * Can be adjusted for different UI contexts.
     * 
     * @param isPressed Whether the card is currently pressed
     * @return Alpha value between 0.0 and 1.0
     */
    fun getGradientAlpha(isPressed: Boolean = false): Float {
        return if (isPressed) 0.9f else 0.85f
    }
    
    /**
     * Returns overlay alpha for text readability on gradients.
     * Useful when placing white text over bright gradients.
     * 
     * @return Alpha value for dark overlay
     */
    fun getTextOverlayAlpha(): Float = 0.15f
}
