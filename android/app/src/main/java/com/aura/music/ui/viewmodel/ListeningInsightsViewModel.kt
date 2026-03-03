package com.aura.music.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.InsightsUiState
import com.aura.music.data.model.ListeningInsights
import com.aura.music.data.repository.ListeningInsightsRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel for Listening Insights analytics screen.
 * 
 * Responsibilities:
 * - Manage insights UI state (loading, success, error, empty)
 * - Fetch and compute insights from repository
 * - Cache computed results for performance
 * - Handle auth state changes
 * - Refresh insights on demand
 * 
 * Architecture:
 * - Uses StateFlow for reactive UI updates
 * - Caches computed results in memory
 * - Background coroutines for heavy computation
 * - Validates auth before operations
 */
class ListeningInsightsViewModel(
    application: Application,
    private val repository: ListeningInsightsRepository = ListeningInsightsRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ListeningInsightsViewModel"
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000  // 5 minutes
    }

    private val _uiState = MutableStateFlow<InsightsUiState>(InsightsUiState.Loading)
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private var cachedInsights: ListeningInsights? = null
    private var lastFetchTimeMs: Long = 0
    private var currentUserId: String? = null
    private var dayBoundaryRefreshJob: Job? = null

    init {
        observeAuthStateChanges()
        startDayBoundaryRefreshLoop()
    }

    /**
     * Load listening insights from repository or cache.
     * 
     * Flow:
     * 1. Check if cache is valid (updated within 5 min)
     * 2. If valid, use cached data
     * 3. If expired or empty, fetch from repository
     * 4. Update UI state based on result
     * 5. Cache successful results
     */
    fun loadInsights() {
        viewModelScope.launch {
            try {
                // Check cache validity
                val now = System.currentTimeMillis()
                if (cachedInsights != null && (now - lastFetchTimeMs) < CACHE_VALIDITY_MS) {
                    Log.d(TAG, "Using cached insights (age: ${now - lastFetchTimeMs}ms)")
                    val insights = cachedInsights!!
                    _uiState.value = if (insights.isEmpty) {
                        InsightsUiState.Empty
                    } else {
                        InsightsUiState.Success(insights)
                    }
                    return@launch
                }

                _uiState.value = InsightsUiState.Loading

                val result = repository.getListeningInsights()
                result.fold(
                    onSuccess = { insights ->
                        lastFetchTimeMs = System.currentTimeMillis()
                        cachedInsights = insights

                        _uiState.value = if (insights.isEmpty) {
                            Log.d(TAG, "Empty insights state")
                            InsightsUiState.Empty
                        } else {
                            Log.d(TAG, "✓ Insights loaded and cached")
                            InsightsUiState.Success(insights)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to load insights", error)
                        _uiState.value = InsightsUiState.Error(error.message ?: "Unknown error")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading insights", e)
                _uiState.value = InsightsUiState.Error(e.message ?: "Unexpected error")
            }
        }
    }

    /**
     * Force refresh insights, bypassing cache.
     * Useful when user returns to the screen or manually refreshes.
     */
    fun refreshInsights() {
        Log.d(TAG, "Refreshing insights (bypassing cache)")
        cachedInsights = null
        lastFetchTimeMs = 0
        loadInsights()
    }

    /**
     * Clear cached insights when user logs out.
     */
    fun clearCache() {
        Log.d(TAG, "Clearing insights cache")
        cachedInsights = null
        lastFetchTimeMs = 0
    }

    /**
     * Observe auth state and reload insights when user changes.
     * 
     * Ensures user-specific data isolation:
     * - When user logs in, load their insights
     * - When user logs out, clear cache
     * - When user switches accounts, reload with new user's data
     */
    private fun observeAuthStateChanges() {
        auth.addAuthStateListener { firebaseAuth ->
            val newUserId = firebaseAuth.currentUser?.uid
            
            if (newUserId != currentUserId) {
                currentUserId = newUserId
                clearCache()
                
                if (newUserId != null) {
                    Log.d(TAG, "Auth state changed to user: $newUserId")
                    loadInsights()
                } else {
                    Log.d(TAG, "User logged out")
                    _uiState.value = InsightsUiState.Empty
                }
            }
        }
    }

    private fun startDayBoundaryRefreshLoop() {
        dayBoundaryRefreshJob?.cancel()
        dayBoundaryRefreshJob = viewModelScope.launch {
            while (isActive) {
                val delayMs = millisUntilNextDayBoundary()
                delay(delayMs)

                if (auth.currentUser != null) {
                    Log.d(TAG, "Day boundary reached, refreshing rolling insights")
                    clearCache()
                    loadInsights()
                }
            }
        }
    }

    private fun millisUntilNextDayBoundary(): Long {
        val now = System.currentTimeMillis()
        val nextMidnight = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return (nextMidnight - now + 1000L).coerceAtLeast(60_000L)
    }

    override fun onCleared() {
        dayBoundaryRefreshJob?.cancel()
        super.onCleared()
    }
}
