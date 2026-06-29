# Layer 6.5G Phase 1 — Interaction Core

# Goal
Add mode/event foundation (InteractionMode, VoiceInteractionEvent,
VoiceInteractionManager) for future transcript streaming + conversation
window phases. No UI, no transcript, no conversation loop yet.

# Files Read
VoiceSessionManager.kt, MainActivity.kt

# Files Created
InteractionMode.kt, VoiceInteractionEvent.kt, VoiceInteractionManager.kt

# Files Modified
None.

# Lines Changed
+71 (new files only)

# Interaction Modes
TEXT_MODE / VOICE_ACTION_MODE / VOICE_CONVERSATION_MODE per spec.
MainActivity confirms typed input goes through `vm.processText()`,
mic through `vm.startVoice()` — action vs conversation mode split
happens after classification, not wired here yet.

# Events
MIC_STARTED, MIC_STOPPED, VOICE_REPLY_STARTED, VOICE_REPLY_FINISHED
only (Phase 1 subset). Transcript/conversation-window events deferred.

# Performance
No timers, polling, or services. Plain listener list + volatile fields.

# Backward Compatibility
No existing file modified. Not wired into VoiceSessionManager or
MainActivity — inert until a future phase connects it.

# Test Cases
setMode() updates currentMode; onMicStarted/Stopped toggle
isVoiceSessionActive and fire correct event to listeners.

# Side Effects
None — standalone, unreferenced by existing code.

# Confidence
90%
