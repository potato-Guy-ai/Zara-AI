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

    fun startVoice() {
        _isListening.value = true
        voiceSession.processText("__voice_trigger__") {}
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
