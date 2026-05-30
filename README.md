# Zara AI — Private Android Assistant

Version 2.0 | Orchestration-first architecture

---

## Architecture

```
Voice/Text
  → STT (Android SpeechRecognizer)
  → SttCorrectionLayer       ← Tanglish / app-name fixes
  → LocalIntentClassifier    ← structured ZaraIntent
  → IntentRouter
      ├── ActionExecutor     ← local device actions
      ├── Conversation       ← local responses
      └── CloudReasoningClient (optional, privacy-filtered)
  → TtsManager
```

### Package structure
```
com.zara.assistant/
├── core/          IntentRouter, ZaraIntent, LocalIntentClassifier, PrivacyFilter
├── voice/         VoiceSessionManager, SttManager, TtsManager, WakeWordManager, SttCorrectionLayer
├── actions/       ActionExecutor, AppActions, CallActions, MediaActions
├── services/      ZaraForegroundService, AccessibilityAutomationService, ZaraNotificationListener
├── memory/        MemoryManager
├── cloud/         AiProvider, GeminiProvider, CloudReasoningClient
├── permissions/   PermissionManager
├── ui/            MainActivity, AssistantViewModel, ChatMessage
└── utils/         ZaraLogger
```

---

## What changed in v2

| Old | New |
|-----|-----|
| Vosk offline STT | Android SpeechRecognizer (multilingual, Tanglish) |
| Raw regex command matching | Structured ZaraIntent + LocalIntentClassifier |
| Monolithic ZaraAIEngine | IntentRouter + ActionExecutor |
| Multiple wake phrases | Only "Hey Zara" |
| No correction layer | SttCorrectionLayer (app names, Tanglish) |
| No cloud abstraction | Optional AiProvider (Gemini pluggable) |
| No privacy filter | PrivacyFilter strips PII before any cloud call |

---

## Setup

### 1. Clone & open
```bash
git clone https://github.com/potato-Guy-ai/Zara-AI.git
```
Open in Android Studio.

### 2. Wake word (choose one)
- **Porcupine (recommended):** Get free API key at [picovoice.ai](https://picovoice.ai), add keyword file, implement `WakeWordEngine` interface in `WakeWordManager.kt`
- **Fallback:** Energy VAD is active by default (triggers on loud audio — replace before production)

### 3. Optional: Piper TTS
Place Piper binary + ONNX model in `assets/piper/`, implement alternative `TtsManager` provider.

### 4. Optional: Cloud AI
```kotlin
// In ZaraApplication.onCreate():
CloudReasoningClient.configure(GeminiProvider(apiKey = "YOUR_KEY"))
```
Cloud is never called for device actions. Only used for complex reasoning queries.

### 5. Build
```bash
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## First-time on device
1. Install APK
2. Grant permissions on launch
3. Settings → Accessibility → Zara Assistant → Enable
4. Settings → Notifications → Notification Access → Zara → Enable
5. Say **"Hey Zara"**

---

## Performance targets

| Metric | Target |
|--------|--------|
| APK size | 50–120 MB |
| RAM idle | 150–300 MB |
| RAM active | 300–600 MB |

---

## Privacy
- All voice processing on-device
- Cloud AI optional, off by default
- PII stripped before any cloud call
- No analytics, no tracking
- Full user data deletion via `MemoryManager.clearAll()`
