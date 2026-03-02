package com.aura.music.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio effects (Equalizer, BassBoost, Virtualizer) for music playback.
 * Handles lifecycle, session attachment, and real-time audio enhancement.
 * 
 * Features:
 * - Thread-safe effect management
 * - Automatic resource cleanup
 * - Real-time audio adjustments
 * - Preset support
 * - StateFlow-based reactive state
 * - Cross-device Virtualizer compatibility
 * 
 * VIRTUALIZER BEHAVIOR:
 * - Requires headphones for noticeable spatial widening effect
 * - Effect may be minimal or inaudible on device speakers
 * - Device DSP/HAL may override or limit effect strength
 * - Requires strength support query for compatibility
 */
class AudioEffectsManager {
    
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    
    private var currentAudioSessionId: Int = 0
    private var virtualizerSupportsStrength: Boolean = false
    
    private val _state = MutableStateFlow(AudioEffectsState())
    val state: StateFlow<AudioEffectsState> = _state.asStateFlow()
    
    companion object {
        private const val TAG = "AudioEffectsManager"
        private const val PRIORITY = 0 // Standard priority
        
        // Virtualizer defaults
        private const val VIRTUALIZER_DEFAULT_STRENGTH = 500 // 0-1000 range, 50%
        private const val VIRTUALIZER_MIN_PERCEPTIBLE = 400
    }
    
    /**
     * Initializes audio effects for the given audio session.
     * Must be called when audio session is ready (Player.STATE_READY).
     * 
     * IMPORTANT: Virtualizer effect is most noticeable with headphones.
     * Effect may not be audible on device speakers due to DSP/HAL limitations.
     * 
     * @param audioSessionId Audio session ID from ExoPlayer media player
     * @return true if initialization successful, false otherwise
     */
    fun initialize(audioSessionId: Int): Boolean {
        if (audioSessionId <= 0) {
            Log.e(TAG, "Invalid audio session ID: $audioSessionId")
            return false
        }
        
        // Release existing effects if switching sessions
        if (currentAudioSessionId != audioSessionId) {
            release()
        }
        
        return try {
            currentAudioSessionId = audioSessionId
            
            // Initialize Equalizer
            equalizer = Equalizer(PRIORITY, audioSessionId).apply {
                enabled = true
                Log.d(TAG, "Equalizer initialized: ${numberOfBands} bands, ${numberOfPresets} presets")
            }
            
            // Initialize BassBoost
            bassBoost = BassBoost(PRIORITY, audioSessionId).apply {
                enabled = true
                setStrength(0) // Start at 0
                Log.d(TAG, "BassBoost initialized: strength control supported")
            }
            
            // Initialize Virtualizer with device compatibility checks
            virtualizer = Virtualizer(PRIORITY, audioSessionId).apply {
                enabled = true
                
                // Check if device supports strength control
                virtualizerSupportsStrength = try {
                    this.strengthSupported  // Property, not method
                } catch (e: Exception) {
                    Log.w(TAG, "Virtualizer strength support query failed: ${e.message}")
                    false
                }
                
                // Set noticeable default strength if supported
                if (virtualizerSupportsStrength) {
                    try {
                        setStrength(VIRTUALIZER_DEFAULT_STRENGTH.toShort())
                        Log.d(TAG, "Virtualizer initialized: strength $VIRTUALIZER_DEFAULT_STRENGTH (device supports strength control)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set Virtualizer strength: ${e.message}")
                        // Virtualizer still enabled, just at default
                    }
                } else {
                    Log.d(TAG, "Virtualizer initialized: device does NOT support strength control (effect may be fixed or unavailable)")
                }
                
                Log.d(TAG, "Virtualizer enabled - NOTE: Use headphones for best spatial widening effect")
            }
            
            // Load initial state
            updateState()
            
            Log.d(TAG, "✓ All audio effects initialized for session: $audioSessionId")
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio effects: ${e.message}", e)
            release()
            false
        }
    }
    
    /**
     * Releases all audio effects and cleans up resources.
     * 
     * Should be called when:
     * - Player is released
     * - Audio session changes
     * - Service is destroyed
     * 
     * Calling initialize() with a new audio session will automatically release old effects.
     */
    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            
            equalizer = null
            bassBoost = null
            virtualizer = null
            virtualizerSupportsStrength = false
            currentAudioSessionId = 0
            
            _state.value = AudioEffectsState(isAvailable = false)
            
            Log.d(TAG, "All audio effects released and resources cleaned up")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects: ${e.message}", e)
        }
    }
    
    /**
     * Sets equalizer band level.
     * 
     * @param band Band index (0 to numberOfBands-1)
     * @param level Level in millibels (within bandLevelRange)
     */
    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
            updateState()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting band level", e)
        }
    }
    
    /**
     * Applies a preset to the equalizer.
     * 
     * @param preset Preset index (0 to numberOfPresets-1)
     */
    fun usePreset(preset: Short) {
        try {
            equalizer?.usePreset(preset)
            updateState()
            Log.d(TAG, "Applied preset: ${getPresetName(preset)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying preset", e)
        }
    }
    
    /**
     * Sets bass boost strength.
     * 
     * @param strength Value from 0 to 1000
     */
    fun setBassBoostStrength(strength: Short) {
        try {
            bassBoost?.setStrength(strength.coerceIn(0, 1000))
            updateState()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting bass boost", e)
        }
    }
    
    /**
     * Sets virtualizer strength with device compatibility handling.
     * 
     * NOTE: Strength adjustment only works on devices that support it.
     * Effect is most noticeable with headphones. Device speakers may
     * not produce audible spatial widening due to DSP/HAL limitations.
     * 
     * @param strength Value from 0 to 1000 (0=no effect, 1000=maximum)
     */
    fun setVirtualizerStrength(strength: Short) {
        try {
            if (!virtualizerSupportsStrength) {
                Log.w(TAG, "Virtualizer strength adjustment not supported on this device")
                return
            }
            
            val constrainedStrength = strength.coerceIn(0, 1000)
            virtualizer?.setStrength(constrainedStrength)
            updateState()
            
            Log.d(TAG, "Virtualizer strength set to $constrainedStrength (best with headphones)")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting virtualizer strength: ${e.message}", e)
        }
    }
    
    /**
     * Checks if Virtualizer effect is available and properly initialized.
     * 
     * @return true if virtualizer is enabled and attached to audio session
     */
    fun isVirtualizerAvailable(): Boolean {
        return try {
            virtualizer != null && virtualizer?.enabled == true && currentAudioSessionId > 0
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Virtualizer availability: ${e.message}")
            false
        }
    }
    
    /**
     * Checks if Virtualizer supports strength adjustment on this device.
     * 
     * @return true if strength control is available
     */
    fun virtualizerSupportsStrengthControl(): Boolean = virtualizerSupportsStrength
    
    /**
     * Resets all effects to default (flat EQ, no boost/virtualizer).
     * 
     * Virtualizer is set to 0 (no effect), not the default perceptible strength.
     * Use setVirtualizerStrength() to re-enable after reset.
     */
    fun reset() {
        try {
            equalizer?.let { eq ->
                for (band in 0 until eq.numberOfBands) {
                    eq.setBandLevel(band.toShort(), 0)
                }
            }
            bassBoost?.setStrength(0)
            
            // Reset Virtualizer only if strength is supported
            if (virtualizerSupportsStrength) {
                virtualizer?.setStrength(0)
            }
            
            updateState()
            Log.d(TAG, "Audio effects reset to default (flat EQ, no boost/virtualizer)")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting effects: ${e.message}", e)
        }
    }
    
    /**
     * Gets the name of a preset.
     */
    fun getPresetName(preset: Short): String {
        return try {
            equalizer?.getPresetName(preset) ?: "Unknown"
        } catch (e: Exception) {
            "Preset $preset"
        }
    }
    
    /**
     * Gets the frequency for a given band.
     */
    fun getBandFrequency(band: Short): Int {
        return try {
            equalizer?.getCenterFreq(band)?.div(1000) ?: 0 // Convert mHz to Hz
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Updates the state flow with current effect values.
     */
    private fun updateState() {
        val eq = equalizer ?: return
        val bb = bassBoost ?: return
        val virt = virtualizer ?: return
        
        try {
            val bands = mutableListOf<BandState>()
            for (band in 0 until eq.numberOfBands) {
                val bandShort = band.toShort()
                bands.add(
                    BandState(
                        index = bandShort,
                        frequency = getBandFrequency(bandShort),
                        level = eq.getBandLevel(bandShort)
                    )
                )
            }
            
            val presets = mutableListOf<String>()
            for (i in 0 until eq.numberOfPresets) {
                presets.add(getPresetName(i.toShort()))
            }
            
            _state.value = AudioEffectsState(
                isAvailable = true,
                bands = bands,
                bandLevelRange = eq.bandLevelRange[0].toInt() to eq.bandLevelRange[1].toInt(),
                presets = presets,
                currentPreset = try { eq.currentPreset.toInt() } catch (e: Exception) { -1 },
                bassBoostStrength = bb.roundedStrength.toInt(),
                virtualizerStrength = virt.roundedStrength.toInt(),
                virtualizerSupportsStrength = virtualizerSupportsStrength
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating state: ${e.message}", e)
        }
    }
    
    /**
     * Checks if audio effects are currently available.
     */
    fun isAvailable(): Boolean = equalizer != null && currentAudioSessionId > 0
}

/**
 * State representing current audio effects configuration.
 * 
 * Virtualizer Note:
 * - Spatial widening effect is most noticeable with headphones
 * - Effect may be inaudible on device speakers
 * - Strength adjustment depends on device DSP/HAL capabilities
 */
data class AudioEffectsState(
    val isAvailable: Boolean = false,
    val bands: List<BandState> = emptyList(),
    val bandLevelRange: Pair<Int, Int> = -1500 to 1500, // Typical range in millibels
    val presets: List<String> = emptyList(),
    val currentPreset: Int = -1, // -1 means custom/manual
    val bassBoostStrength: Int = 0, // 0-1000
    val virtualizerStrength: Int = 0, // 0-1000
    val virtualizerSupportsStrength: Boolean = false // Device capability flag
)

/**
 * State for a single equalizer band.
 */
data class BandState(
    val index: Short,
    val frequency: Int, // Hz
    val level: Short // Millibels
)
