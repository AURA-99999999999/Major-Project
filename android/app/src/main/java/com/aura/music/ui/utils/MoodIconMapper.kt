package com.aura.music.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Centralized utility for mapping mood names to Material Design icons.
 * Provides consistent, reusable icon selection for mood-based UI components.
 * 
 * Features:
 * - Case-insensitive matching
 * - Synonym support for common mood variations
 * - Material Design 3 icons
 * - Theme-compatible (works in dark/light mode)
 * - Lightweight performance (simple map lookup)
 */
object MoodIconMapper {
    
    /**
     * Maps a mood name to its corresponding Material Design icon.
     * Uses case-insensitive matching and handles common synonyms.
     * 
     * @param moodName The name of the mood (e.g., "Happy", "Chill", "Energetic")
     * @return ImageVector representing the mood icon, or default music note if no match
     */
    fun getIconForMood(moodName: String): ImageVector {
        return when (moodName.lowercase().trim()) {
            // Happy / Positive moods
            "happy", "joy", "joyful", "cheerful", "upbeat", "positive" -> 
                Icons.Default.SentimentSatisfied
            
            // Energetic / Active moods
            "energetic", "energy", "pump up", "energize", "active", "hype", "motivated", "motivation" -> 
                Icons.Default.Bolt
            
            // Chill / Relaxed moods
            "chill", "relax", "relaxed", "calm", "mellow", "peaceful", "peace" -> 
                Icons.Default.LocalCafe
            
            // Sad / Melancholy moods
            "sad", "melancholy", "blue", "down", "moody" -> 
                Icons.Default.SentimentDissatisfied
            
            // Romantic / Love moods
            "romantic", "love", "romance", "passionate" -> 
                Icons.Default.Favorite
            
            // Focus / Study moods
            "focus", "concentration", "study", "work", "productive" -> 
                Icons.Default.Psychology
            
            // Workout / Fitness moods
            "workout", "gym", "fitness", "exercise", "training", "sport" -> 
                Icons.Default.FitnessCenter
            
            // Party / Celebration moods
            "party", "celebration", "dance", "club", "nightlife", "fun" -> 
                Icons.Default.Celebration
            
            // Sleep / Night moods
            "sleep", "sleepy", "bedtime", "night", "dreamy" -> 
                Icons.Default.Bedtime
            
            // Angry / Aggressive moods
            "angry", "rage", "mad", "aggressive" -> 
                Icons.Default.Warning
            
            // Nostalgic / Reflective moods
            "nostalgic", "nostalgia", "throwback", "memories", "reflective" -> 
                Icons.Default.History
            
            // Excited moods
            "excited", "excitement", "thrilled" -> 
                Icons.Default.Star
            
            // Groovy / Funky moods
            "groovy", "funky", "disco", "retro" -> 
                Icons.Default.Album
            
            // Commute / Travel moods
            "commute", "travel", "road trip", "drive", "driving", "journey" -> 
                Icons.Default.DirectionsCar
            
            // Meditation / Mindfulness moods
            "meditation", "meditate", "mindfulness", "zen", "peaceful", "tranquil" ->
                Icons.Default.SelfImprovement
            
            // Feel good / Positive uplifting moods
            "feel good", "feel-good", "uplifting", "uplifted" -> 
                Icons.Default.Grade
            
            // Gaming / Play moods
            "gaming", "game", "play", "esports", "gamer" -> 
                Icons.Default.Games
            
            // Decades / Classic moods
            "decades", "decade", "classic", "classics", "oldies" -> 
                Icons.Default.AccessTime
            
            // Default fallback
            else -> Icons.Default.MusicNote
        }
    }
    
    /**
     * Gets a descriptive label for a mood icon for accessibility.
     * Used for content descriptions in Compose.
     * 
     * @param moodName The name of the mood
     * @return A human-readable description of the icon
     */
    fun getIconContentDescription(moodName: String): String {
        return when (moodName.lowercase().trim()) {
            "happy", "joy", "joyful", "cheerful" -> "Happy face icon"
            "energetic", "energy", "pump up", "energize" -> "Lightning bolt icon"
            "chill", "relax", "relaxed", "calm" -> "Coffee cup icon"
            "sad", "melancholy", "blue" -> "Sad face icon"
            "romantic", "love" -> "Heart icon"
            "focus", "concentration", "study" -> "Brain icon"
            "workout", "gym", "fitness" -> "Dumbbell icon"
            "party", "celebration", "dance" -> "Party icon"
            "sleep", "sleepy", "bedtime" -> "Moon icon"
            "angry", "rage" -> "Warning icon"
            "nostalgic", "nostalgia" -> "History icon"
            "excited", "excitement" -> "Star icon"
            "groovy", "funky" -> "Album icon"
            "commute", "travel", "drive", "driving" -> "Car icon"
            "meditation", "meditate", "mindfulness" -> "Meditation icon"
            "feel good", "feel-good" -> "Grade icon"
            "gaming", "game" -> "Game controller icon"            "decades", "decade", "classic" -> "Clock icon"            else -> "Music note icon"
        }
    }
}
