package com.zara.assistant.tasks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zara.assistant.R
import com.zara.assistant.voice.VoiceSessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phase 5: voice-first "Add Task" entry point for the task widget.
 *
 * Voice-first, single pipeline: opens the mic through the EXISTING
 * [VoiceSessionManager.startManualListening] — same STT → classifier →
 * ActionExecutor path as the in-app mic button. No second STT pipeline.
 * Typing is a fallback only and rides the existing processText() path.
 *
 * Owns a short-lived VoiceSessionManager instance, mirroring the existing
 * AssistantViewModel pattern (multi-instance consolidation is tracked as a
 * known issue in docs/OPTIMIZATION_STATE.md). Deliberately does NOT call
 * stop() on teardown: stop() resets global pipeline state (PipelineStateMachine,
 * ConversationModeManager) and could stomp an in-flight wakeword session owned
 * by the foreground service; the recognizer self-terminates on result/error.
 */
class TaskQuickAddActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var input: EditText

    private val voiceSession by lazy { VoiceSessionManager(applicationContext) }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoice()
        else showTypingFallback("Voice unavailable without mic permission — type below.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_quick_add)

        status = findViewById(R.id.quick_add_status)
        input  = findViewById(R.id.quick_add_input)

        findViewById<android.widget.Button>(R.id.quick_add_send)
            .setOnClickListener { submitTyped() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submitTyped(); true } else false
        }

        // Voice-first: start listening immediately when permission is held;
        // otherwise ask once, then fall back to typing if denied.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startVoice()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoice() {
        status.text = "Listening… say your reminder."
        voiceSession.startManualListening { response -> onVoiceResponse(response) }
    }

    private fun onVoiceResponse(response: String) {
        if (isDestroyed || isFinishing) return
        findViewById<android.widget.ProgressBar>(R.id.quick_add_spinner).visibility =
            android.view.View.GONE

        // A task may have been created — refresh the widget now.
        TaskWidgetSync.updateAll(this)

        if (response.isBlank()) {
            // Mic failed / blank result — stay open with typing fallback.
            showTypingFallback("Didn't catch that — type it below.")
            return
        }
        status.text = response
        delayedFinish()
    }

    private fun submitTyped() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        status.text = "Adding task…"
        voiceSession.processText(text) { response ->
            if (isDestroyed || isFinishing) return@processText
            TaskWidgetSync.updateAll(this)
            status.text = response
            delayedFinish()
        }
    }

    private fun showTypingFallback(message: String) {
        status.text = message
        input.requestFocus()
    }

    /** Lets the spoken/typed reply be read before returning to the home screen. */
    private fun delayedFinish() {
        lifecycleScope.launch {
            delay(FINISH_DELAY_MS)
            finish()
        }
    }

    companion object {
        private const val FINISH_DELAY_MS = 2_500L
    }
}
