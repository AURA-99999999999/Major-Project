package com.aura.music

import android.app.Application
import android.util.Log
import com.aura.music.di.ServiceLocator

class AuraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Initialize ServiceLocator for dependency injection
            ServiceLocator.initialize(this)
            Log.d(TAG, "AuraApplication initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AuraApplication", e)
            // Don't rethrow - let the app continue to show error UI
        }
    }

    companion object {
        private const val TAG = "AuraApplication"
    }
}

