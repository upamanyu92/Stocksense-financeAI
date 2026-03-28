package com.stocksense.app.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Persistent user preferences backed by Jetpack DataStore. */
data class UserPreferences(
    val displayName: String = "",
    val email: String = "",
    val notificationsEnabled: Boolean = true,
    val darkThemeEnabled: Boolean = true,
    val defaultQualityMode: String = "BALANCED",
    val isLoggedIn: Boolean = false
)

class UserPreferencesManager(private val context: Context) {

    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val EMAIL = stringPreferencesKey("email")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DEFAULT_QUALITY_MODE = stringPreferencesKey("default_quality_mode")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
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
                isLoggedIn = prefs[Keys.IS_LOGGED_IN] ?: false
            )
        }

    suspend fun updateDisplayName(name: String) {
        context.dataStore.edit { it[Keys.DISPLAY_NAME] = name }
    }

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
}
