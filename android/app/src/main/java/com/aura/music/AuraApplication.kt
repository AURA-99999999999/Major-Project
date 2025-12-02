package com.aura.music

import android.app.Application
import com.aura.music.data.local.AuthPreferences
import com.aura.music.di.ServiceLocator

class AuraApplication : Application() {
    lateinit var authPreferences: AuthPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize ServiceLocator for dependency injection
        ServiceLocator.initialize(this)
        authPreferences = AuthPreferences(this)
    }
}

