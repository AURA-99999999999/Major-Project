package com.aura.music.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val AUTH_PREFS_NAME = "auth_prefs"

val Context.authDataStore by preferencesDataStore(name = AUTH_PREFS_NAME)

class AuthPreferences(private val context: Context) {

    private val KEY_IS_LOGGED_IN: Preferences.Key<Boolean> =
        booleanPreferencesKey("is_logged_in")

    val isLoggedIn: Flow<Boolean> = context.authDataStore.data
        .map { prefs -> prefs[KEY_IS_LOGGED_IN] ?: false }

    suspend fun setLoggedIn(isLoggedIn: Boolean) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = isLoggedIn
        }
    }
}


