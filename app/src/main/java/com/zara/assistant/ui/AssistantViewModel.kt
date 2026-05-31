package com.zara.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zara.assistant.voice.VoiceSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val voiceSession = VoiceSessionManager(app)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    fun processText(text: String) {
        addMessage(text, MessageRole.USER)
        voiceSession.processText(text) { response ->
            addMessage(response, MessageRole.ZARA)
        }
    }

    /**
     * CR-2 fix: mic button now triggers real STT via startManualListening().
     * Previously sent "hey zara" to the classifier, which returned a greeting
     * and never opened the microphone.
     */
    fun startVoice() {
        if (_isListening.value) return
        _isListening.value = true
        voiceSession.startManualListening { response ->
            _isListening.value = false
            if (response.isNotBlank()) addMessage(response, MessageRole.ZARA)
        }
    }

    fun onPermissionsResult() {}

    private fun addMessage(text: String, role: MessageRole) {
        viewModelScope.launch {
            _messages.value = _messages.value + ChatMessage(text, role)
        }
    }

    override fun onCleared() {
        voiceSession.stop()
        super.onCleared()
    }
}
