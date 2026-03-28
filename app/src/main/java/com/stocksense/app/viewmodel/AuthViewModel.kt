package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val isCheckingAuth: Boolean = true,
    val loginEmail: String = "",
    val loginPin: String = "",
    val registerName: String = "",
    val registerEmail: String = "",
    val registerPin: String = "",
    val registerConfirmPin: String = "",
    val termsAccepted: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val prefsManager: UserPreferencesManager
) : ViewModel() {

    companion object {
        /** Maximum PIN length (digits). */
        const val MAX_PIN_LENGTH = 6
        /** Minimum PIN length (digits). */
        const val MIN_PIN_LENGTH = 4
    }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsManager.userPreferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = prefs.isLoggedIn,
                        isCheckingAuth = false
                    )
                }
            }
        }
    }

    fun updateLoginEmail(email: String) {
        _uiState.update { it.copy(loginEmail = email, error = null) }
    }

    fun updateLoginPin(pin: String) {
        if (pin.length <= MAX_PIN_LENGTH) {
            _uiState.update { it.copy(loginPin = pin, error = null) }
        }
    }

    fun updateRegisterName(name: String) {
        _uiState.update { it.copy(registerName = name, error = null) }
    }

    fun updateRegisterEmail(email: String) {
        _uiState.update { it.copy(registerEmail = email, error = null) }
    }

    fun updateRegisterPin(pin: String) {
        if (pin.length <= MAX_PIN_LENGTH) {
            _uiState.update { it.copy(registerPin = pin, error = null) }
        }
    }

    fun updateRegisterConfirmPin(pin: String) {
        if (pin.length <= MAX_PIN_LENGTH) {
            _uiState.update { it.copy(registerConfirmPin = pin, error = null) }
        }
    }

    fun updateTermsAccepted(accepted: Boolean) {
        _uiState.update { it.copy(termsAccepted = accepted, error = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.loginEmail.isBlank()) {
            _uiState.update { it.copy(error = "Email is required") }
            return
        }
        if (state.loginPin.length < MIN_PIN_LENGTH) {
            _uiState.update { it.copy(error = "PIN must be at least 4 digits") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val hashedPin = hashPin(state.loginPin)
            val valid = prefsManager.validatePin(state.loginEmail, hashedPin)
            if (valid) {
                prefsManager.login(state.loginEmail)
                _uiState.update { it.copy(isLoading = false) }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = "Invalid email or PIN")
                }
            }
        }
    }

    fun register() {
        val state = _uiState.value
        if (state.registerName.isBlank()) {
            _uiState.update { it.copy(error = "Name is required") }
            return
        }
        if (state.registerEmail.isBlank()) {
            _uiState.update { it.copy(error = "Email is required") }
            return
        }
        if (state.registerPin.length < MIN_PIN_LENGTH) {
            _uiState.update { it.copy(error = "PIN must be at least 4 digits") }
            return
        }
        if (state.registerPin != state.registerConfirmPin) {
            _uiState.update { it.copy(error = "PINs do not match") }
            return
        }
        if (!state.termsAccepted) {
            _uiState.update { it.copy(error = "You must accept the Terms and Conditions to continue") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val hashedPin = hashPin(state.registerPin)
            prefsManager.register(state.registerName, state.registerEmail, hashedPin)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            prefsManager.logout()
        }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
