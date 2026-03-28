package com.stocksense.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.preferences.UserPreferences
import com.stocksense.app.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedbackAttachment(
    val uri: Uri,
    val name: String
)

data class FeedbackUiState(
    val userPreferences: UserPreferences = UserPreferences(),
    val feedbackText: String = "",
    val attachments: List<FeedbackAttachment> = emptyList()
)

class FeedbackViewModel(
    private val userPreferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesManager.userPreferences.collect { preferences ->
                _uiState.update { it.copy(userPreferences = preferences) }
            }
        }
    }

    fun updateFeedbackText(text: String) {
        _uiState.update { it.copy(feedbackText = text) }
    }

    fun addAttachment(uri: Uri, name: String) {
        _uiState.update { state ->
            state.copy(
                attachments = state.attachments + FeedbackAttachment(
                    uri = uri,
                    name = name.ifBlank { "Attachment ${state.attachments.size + 1}" }
                )
            )
        }
    }

    fun removeAttachment(uri: Uri) {
        _uiState.update { state ->
            state.copy(attachments = state.attachments.filterNot { it.uri == uri })
        }
    }
}
