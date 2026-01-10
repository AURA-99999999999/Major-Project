package com.aura.music

import android.app.Application
import com.aura.music.di.ServiceLocator

class AuraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize ServiceLocator for dependency injection
        ServiceLocator.initialize(this)
    }
}

