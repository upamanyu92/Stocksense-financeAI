package com.stocksense.app.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Persistent user preferences backed by Jetpack DataStore. */
data class UserPreferences(
    val displayName: String = "",
    val email: String = "",
    val notificationsEnabled: Boolean = true,
    val darkThemeEnabled: Boolean = true,
    val defaultQualityMode: String = "BALANCED",
    val isLoggedIn: Boolean = false,
    val isInitialSetupComplete: Boolean = false
)

class UserPreferencesManager(private val context: Context) {

    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val EMAIL = stringPreferencesKey("email")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DEFAULT_QUALITY_MODE = stringPreferencesKey("default_quality_mode")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val INITIAL_SETUP_COMPLETE = booleanPreferencesKey("initial_setup_complete")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            UserPreferences(
                displayName = prefs[Keys.DISPLAY_NAME] ?: "",
                email = prefs[Keys.EMAIL] ?: "",
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                darkThemeEnabled = prefs[Keys.DARK_THEME] ?: true,
                defaultQualityMode = prefs[Keys.DEFAULT_QUALITY_MODE] ?: "BALANCED",
                isLoggedIn = prefs[Keys.IS_LOGGED_IN] ?: false,
                isInitialSetupComplete = prefs[Keys.INITIAL_SETUP_COMPLETE] ?: false
            )
        }

    @Suppress("unused")
    suspend fun updateDisplayName(name: String) {
        context.dataStore.edit { it[Keys.DISPLAY_NAME] = name }
    }

    @Suppress("unused")
    suspend fun updateEmail(email: String) {
        context.dataStore.edit { it[Keys.EMAIL] = email }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun updateDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun updateDefaultQualityMode(mode: String) {
        context.dataStore.edit { it[Keys.DEFAULT_QUALITY_MODE] = mode }
    }

    /** Register a new user with name, email, and hashed PIN. */
    suspend fun register(name: String, email: String, hashedPin: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = name
            prefs[Keys.EMAIL] = email
            prefs[Keys.PIN_HASH] = hashedPin
            prefs[Keys.IS_LOGGED_IN] = true
        }
    }

    /** Validate email and hashed PIN against stored credentials. */
    suspend fun validatePin(email: String, hashedPin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val storedEmail = prefs[Keys.EMAIL] ?: ""
        val storedHash = prefs[Keys.PIN_HASH] ?: ""
        return storedEmail.equals(email, ignoreCase = true) && storedHash == hashedPin
    }

    /** Mark user as logged in (after PIN validation). */
    @Suppress("UNUSED_PARAMETER")
    suspend fun login(email: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_LOGGED_IN] = true
        }
    }

    /** Legacy login – kept for backward compatibility with ProfileViewModel. */
    suspend fun login(name: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = name
            prefs[Keys.EMAIL] = email
            prefs[Keys.IS_LOGGED_IN] = true
        }
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_LOGGED_IN] = false
        }
    }

    suspend fun markInitialSetupComplete() {
        context.dataStore.edit { it[Keys.INITIAL_SETUP_COMPLETE] = true }
    }

    suspend fun isInitialSetupComplete(): Boolean =
        context.dataStore.data.catch { emit(emptyPreferences()) }
            .map { it[Keys.INITIAL_SETUP_COMPLETE] ?: false }
            .first()
}
