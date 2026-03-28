package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.preferences.UserPreferences
import com.stocksense.app.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileUiState(
    val preferences: UserPreferences = UserPreferences(),
    val isEditing: Boolean = false,
    val editName: String = "",
    val editEmail: String = "",
    val isSaving: Boolean = false
)

class ProfileViewModel(
    private val prefsManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsManager.userPreferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        preferences = prefs,
                        editName = if (!it.isEditing) prefs.displayName else it.editName,
                        editEmail = if (!it.isEditing) prefs.email else it.editEmail
                    )
                }
            }
        }
    }

    fun startEditing() {
        _uiState.update {
            it.copy(
                isEditing = true,
                editName = it.preferences.displayName,
                editEmail = it.preferences.email
            )
        }
    }

    fun updateEditName(name: String) {
        _uiState.update { it.copy(editName = name) }
    }

    fun updateEditEmail(email: String) {
        _uiState.update { it.copy(editEmail = email) }
    }

    fun saveProfile() {
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            prefsManager.login(state.editName, state.editEmail)
            _uiState.update { it.copy(isEditing = false, isSaving = false) }
        }
    }

    fun cancelEditing() {
        _uiState.update {
            it.copy(
                isEditing = false,
                editName = it.preferences.displayName,
                editEmail = it.preferences.email
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            prefsManager.logout()
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            prefsManager.updateNotificationsEnabled(enabled)
        }
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            prefsManager.updateDarkTheme(enabled)
        }
    }

    fun updateQualityMode(mode: String) {
        viewModelScope.launch {
            prefsManager.updateDefaultQualityMode(mode)
        }
    }
}
