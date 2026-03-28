package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.database.dao.ChatMessageDao
import com.stocksense.app.data.database.entities.ChatMessage
import com.stocksense.app.data.repository.StockRepository
import com.stocksense.app.engine.LLMInsightEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val chatMessageDao: ChatMessageDao,
    private val llmEngine: LLMInsightEngine,
    private val stockRepository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            try {
                val history = chatMessageDao.getRecentMessages(limit = 20)
                val uiMessages = history.flatMap { msg ->
                    listOf(
                        ChatUiMessage(text = msg.userMessage, isUser = true, timestamp = msg.timestamp),
                        ChatUiMessage(text = msg.aiResponse, isUser = false, timestamp = msg.timestamp + 1)
                    )
                }.sortedBy { it.timestamp }
                _uiState.update { it.copy(messages = uiMessages) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatUiMessage(text = text, isUser = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val symbol = extractSymbol(text)
                val recentPrices = if (symbol != null) {
                    stockRepository.getRecentHistory(symbol, 10).map { it.close }
                } else {
                    emptyList()
                }

                val aiResponseText = llmEngine.chat(
                    userMessage = text,
                    symbol = symbol ?: "GENERAL",
                    recentPrices = recentPrices
                )

                val aiMessage = ChatUiMessage(text = aiResponseText, isUser = false)
                _uiState.update {
                    it.copy(
                        messages = it.messages + aiMessage,
                        isLoading = false
                    )
                }

                chatMessageDao.insert(
                    ChatMessage(
                        userMessage = text,
                        aiResponse = aiResponseText
                    )
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to get response: ${e.message}"
                    )
                }
            }
        }
    }

    private fun extractSymbol(text: String): String? {
        val upper = text.uppercase()
        val knownPatterns = listOf(
            "RELIANCE", "TCS", "INFY", "HDFC", "ICICI", "SBIN",
            "WIPRO", "HCLTECH", "BAJFINANCE", "KOTAKBANK", "LT",
            "ITC", "AXISBANK", "BHARTIARTL", "MARUTI", "ADANIENT"
        )
        return knownPatterns.firstOrNull { upper.contains(it) }
    }
}
