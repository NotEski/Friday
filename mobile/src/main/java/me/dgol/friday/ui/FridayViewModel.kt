package me.dgol.friday.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class FridayUiState(
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val transcript: String = "",
    val showPermissionRationale: Boolean = false,
    val errorMessage: String? = null
)

class FridayViewModel : ViewModel() {

    private val _ui = MutableStateFlow(FridayUiState())
    val ui: StateFlow<FridayUiState> = _ui

    fun startListening() {
        _ui.update { it.copy(isListening = true, isThinking = false) }
        // TODO: Hook your real audio/LLM pipeline here.
        // You can send interim text to appendTranscript(...)
    }

    fun stopListening() {
        _ui.update { it.copy(isListening = false, isThinking = false) }
        // TODO: Stop your audio/LLM pipeline.
    }

    fun appendTranscript(line: String) {
        _ui.update { it.copy(transcript = (it.transcript + if (it.transcript.isEmpty()) "" else "\n") + line) }
    }

    fun showError(message: String) {
        _ui.update { it.copy(errorMessage = message) }
    }

    fun dismissError() {
        _ui.update { it.copy(errorMessage = null) }
    }

    fun onPermissionDenied() {
        _ui.update { it.copy(showPermissionRationale = true) }
    }

    fun dismissPermissionRationale() {
        _ui.update { it.copy(showPermissionRationale = false) }
    }
}
