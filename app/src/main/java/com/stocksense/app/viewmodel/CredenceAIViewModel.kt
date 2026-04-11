package com.stocksense.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.database.dao.CredenceAnalysisDao
import com.stocksense.app.data.database.entities.CredenceAnalysis
import com.stocksense.app.data.model.credence.*
import com.stocksense.app.engine.LLMInsightEngine
import com.stocksense.app.engine.LlmStatus
import com.stocksense.app.engine.QualityMode
import com.stocksense.app.engine.credence.CredenceAIEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "CredenceAIViewModel"

// ── UI State ──────────────────────────────────────────────────────────────────

/**
 * Holds the form state for the New Analysis tab.
 * Each field mirrors a UI text field; conversion to [CreditAnalysisRequest] is
 * done just before submitting via [buildRequest].
 */
data class CredenceFormState(
    val companyName: String   = "",
    val industry: String      = "",
    val sector: String        = "",
    val description: String   = "",
    val analystNotes: String  = "",
    // Financial profile fields (stored as strings for text-field binding)
    val totalRevenue: String         = "",
    val ebit: String                 = "",
    val totalAssets: String          = "",
    val totalLiabilities: String     = "",
    val workingCapital: String       = "",
    val retainedEarnings: String     = "",
    val marketValueEquity: String    = "",
    val totalDebt: String            = "",
    val shareholdersEquity: String   = "",
    val priorYearRevenue: String     = "",
    val priorYearEbit: String        = ""
) {
    /** True if minimum required fields are filled. */
    val canSubmit: Boolean
        get() = companyName.isNotBlank() &&
                totalRevenue.toDoubleOrNull()?.let { it > 0 } == true &&
                totalAssets.toDoubleOrNull()?.let { it > 0 } == true

    /** Converts form strings to a validated [CreditAnalysisRequest]. */
    fun buildRequest(): CreditAnalysisRequest = CreditAnalysisRequest(
        companyName   = companyName.trim(),
        industry      = industry.trim(),
        sector        = sector.trim(),
        description   = description.trim(),
        analystNotes  = analystNotes.trim(),
        financialProfile = FinancialProfile(
            totalRevenue       = totalRevenue.toDoubleOrNull()       ?: 0.0,
            ebit               = ebit.toDoubleOrNull()               ?: 0.0,
            totalAssets        = totalAssets.toDoubleOrNull()        ?: 0.0,
            totalLiabilities   = totalLiabilities.toDoubleOrNull()   ?: 0.0,
            workingCapital     = workingCapital.toDoubleOrNull()     ?: 0.0,
            retainedEarnings   = retainedEarnings.toDoubleOrNull()   ?: 0.0,
            marketValueEquity  = marketValueEquity.toDoubleOrNull()  ?: 0.0,
            totalDebt          = totalDebt.toDoubleOrNull()          ?: 0.0,
            shareholdersEquity = shareholdersEquity.toDoubleOrNull() ?: 0.0,
            priorYearRevenue   = priorYearRevenue.toDoubleOrNull()   ?: 0.0,
            priorYearEbit      = priorYearEbit.toDoubleOrNull()      ?: 0.0
        )
    )
}

/**
 * Complete UI state for the CredenceAI screen.
 * Screen has 4 tabs: 0=New Analysis, 1=Results, 2=Evaluation, 3=History.
 */
data class CredenceUiState(
    val form: CredenceFormState              = CredenceFormState(),
    val report: TatvaAnkReport?              = null,
    val history: List<CredenceAnalysis>      = emptyList(),
    val isLoading: Boolean                   = false,
    val error: String?                       = null,
    val selectedTab: Int                     = 0,
    /** Whether the feedback text fields are visible per agent. */
    val feedbackVisible: Map<String, Boolean> = emptyMap(),
    val feedbackDraft: Map<String, String>    = emptyMap(),
    /** LLM agent status — shows if CredenceAI agents use real LLM or template. */
    val llmStatus: LlmStatus                 = LlmStatus.NATIVE_UNAVAILABLE,
    val llmQualityMode: QualityMode          = QualityMode.BALANCED
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class CredenceAIViewModel(
    private val engine: CredenceAIEngine,
    private val dao: CredenceAnalysisDao,
    private val llmEngine: LLMInsightEngine
) : ViewModel() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults    = true
        isLenient         = true
    }

    private val _uiState = MutableStateFlow(CredenceUiState())
    val uiState: StateFlow<CredenceUiState> = _uiState.asStateFlow()

    init {
        observeHistory()
        refreshLlmStatus()
    }

    /** Refresh LLM agent status so the UI reflects whether agents use real LLM. */
    private fun refreshLlmStatus() {
        _uiState.update {
            it.copy(
                llmStatus = llmEngine.status,
                llmQualityMode = llmEngine.currentQualityMode()
            )
        }
    }

    // ── Tab navigation ────────────────────────────────────────────────────────

    fun selectTab(tab: Int) = _uiState.update { it.copy(selectedTab = tab) }

    // ── Form updates ──────────────────────────────────────────────────────────

    fun updateForm(transform: CredenceFormState.() -> CredenceFormState) =
        _uiState.update { it.copy(form = it.form.transform()) }

    // ── Analysis ─────────────────────────────────────────────────────────────

    /**
     * Run the full CredenceAI pipeline using current form state.
     * Persists the result to Room and switches to the Results tab.
     */
    fun startAnalysis() {
        val request = _uiState.value.form.buildRequest()
        if (!request.isValid) {
            _uiState.update { it.copy(error = "Please provide company name, revenue and total assets.") }
            return
        }
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val report = engine.analyze(request)
                persistReport(report)
                refreshLlmStatus()
                _uiState.update {
                    it.copy(
                        isLoading   = false,
                        report      = report,
                        selectedTab = 1,  // Jump to Results
                        feedbackDraft  = emptyMap(),
                        feedbackVisible = emptyMap()
                    )
                }
                Log.i(TAG, "Analysis complete: score=${report.tatvaAnkScore}")
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed: ${e.message}", e)
                refreshLlmStatus()
                _uiState.update {
                    it.copy(isLoading = false, error = "Analysis failed: ${e.message}")
                }
            }
        }
    }

    // ── HITL Feedback ─────────────────────────────────────────────────────────

    /** Toggle the feedback text area visibility for [agentName]. */
    fun toggleFeedbackPanel(agentName: String) {
        _uiState.update {
            val current = it.feedbackVisible[agentName] ?: false
            it.copy(feedbackVisible = it.feedbackVisible + (agentName to !current))
        }
    }

    /** Update the draft feedback text for [agentName]. */
    fun updateFeedbackDraft(agentName: String, text: String) {
        _uiState.update {
            it.copy(feedbackDraft = it.feedbackDraft + (agentName to text))
        }
    }

    /**
     * Persist HITL feedback for [agentName] into the current report.
     * Updates the report JSON in Room to preserve feedback.
     */
    fun submitFeedback(agentName: String) {
        val draft = _uiState.value.feedbackDraft[agentName] ?: return
        if (draft.isBlank()) return

        val existingReport = _uiState.value.report ?: return
        viewModelScope.launch {
            val updated = existingReport.copy(
                hitlFeedback = existingReport.hitlFeedback + (agentName to draft)
            )
            persistReport(updated)
            _uiState.update {
                it.copy(
                    report = updated,
                    feedbackVisible = it.feedbackVisible + (agentName to false),
                    feedbackDraft   = it.feedbackDraft - agentName
                )
            }
            Log.i(TAG, "Feedback submitted for agent '$agentName'")
        }
    }

    // ── History ───────────────────────────────────────────────────────────────

    /**
     * Load a historical analysis from [analysis] and display it in the Results tab.
     */
    fun loadHistoricalReport(analysis: CredenceAnalysis) {
        try {
            val report = json.decodeFromString<TatvaAnkReport>(analysis.reportJson)
            _uiState.update { it.copy(report = report, selectedTab = 1) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialise historical report id=${analysis.id}: ${e.message}")
            _uiState.update { it.copy(error = "Could not load historical report.") }
        }
    }

    /** Clear analysis history from the database. */
    fun clearHistory() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }

    // ── Error handling ────────────────────────────────────────────────────────

    fun clearError() = _uiState.update { it.copy(error = null) }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun observeHistory() {
        viewModelScope.launch {
            dao.getAll()
                .catch { e -> Log.e(TAG, "History flow error: ${e.message}") }
                .collect { list -> _uiState.update { it.copy(history = list) } }
        }
    }

    private suspend fun persistReport(report: TatvaAnkReport) {
        try {
            val reportJson = json.encodeToString(report)
            val entity = CredenceAnalysis(
                companyName     = report.request.companyName,
                industry        = report.request.industry,
                sector          = report.request.sector,
                tatvaAnkScore   = report.tatvaAnkScore,
                altmanZScore    = report.altmanZScore,
                sentimentScore  = report.sentimentScore,
                riskLabel       = report.riskLabel.name,
                processingTimeMs = report.processingTimeMs,
                reportJson      = reportJson,
                createdAt       = report.createdAt
            )
            dao.insert(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist report: ${e.message}")
        }
    }
}

