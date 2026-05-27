# Zara-AI
Android Based AI voice Assistant for the local computation and user support
# Zara — Private AI Voice Assistant for Android

## Quick Start

### 1. Clone & Open
```bash
git clone <repo>
cd zara
```

### 2. Download required AI models

#### Vosk STT model (~40MB)
```bash
cd app/src/main/assets
wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip vosk-model-small-en-us-0.15.zip
mv vosk-model-small-en-us-0.15 vosk-model-small-en-us
```

#### Wake word model
- Option A: Use [openWakeWord](https://github.com/dscripka/openWakeWord) to train "hey zara"
- Option B: Use [Porcupine](https://picovoice.ai/platform/porcupine/) free tier
- Place as: `app/src/main/assets/models/wake_word.tflite`
- Without a model, falls back to energy-based VAD (always triggers on loud audio)

#### Optional: Piper TTS (high quality voice)
```bash
# Download piper for Android (ARM64)
# https://github.com/rhasspy/piper/releases
mkdir -p app/src/main/assets/piper
# Place: piper binary + en_US-amy-low.onnx
```

### 3. Build APK (no Android Studio needed)

#### Via Gitpod (free cloud IDE)
1. Open https://gitpod.io/#<your-repo-url>
2. Run: `cd zara && ./gradlew assembleDebug`
3. Download APK from: `app/build/outputs/apk/debug/app-debug.apk`

#### Via GitHub Actions
Push to main → CI auto-builds APK → download from Actions artifacts

### 4. Install on Phone (no Android Studio needed)

#### ADB over Wi-Fi
```bash
# On phone: Settings > Developer Options > Wireless Debugging > enable
# Note the IP:PORT shown
adb connect 192.168.x.x:PORT
adb install app/build/outputs/apk/debug/app-debug.apk
adb logcat -s Zara   # live logs
```

#### Direct sideload
- Transfer APK to phone via USB/cloud
- Enable "Install unknown apps" for Files app
- Tap APK to install

### 5. First-time setup on phone
1. Open Zara
2. Grant all permissions when prompted
3. Go to **Settings > Accessibility > Zara Assistant** → Enable
4. Go to **Settings > Notifications > Notification Access > Zara** → Enable
5. Say "Hey Zara" — it should respond!

---

## Testing Without Phone

### Python unit tests (AI intent engine)
```bash
pip install pytest
python -m pytest ai_core/tests/ -v
```
Expected: 30+ tests pass in ~1 second, no Android needed.

### Browser emulator
Upload APK to https://appetize.io (free 100 min/month)

---

## Architecture

```
ZaraApplication
    └── ZaraCoreService (foreground, always running)
            ├── WakeWordEngine   → TFLite model / energy fallback
            ├── SpeechRecognizer → Vosk offline STT
            ├── ZaraAIEngine     → Intent matching + dispatch
            └── TTSEngine        → Android TTS / Piper

Automation layer:
    ├── DeviceAutomation    → WiFi, BT, volume, camera, apps
    ├── PhoneAutomation     → calls, answer, hang up
    ├── MessagingAutomation → SMS, WhatsApp
    ├── CalendarAutomation  → events, schedule
    ├── ZaraAccessibilityService → lock, home, back, screenshot
    └── ZaraNotificationListener → read all notifications
```

## Wake Phrases
- "Hey Zara"
- "Wake up Zara"

## Voice Commands (examples)
| Command | Action |
|---------|--------|
| Call mom | Dials contact |
| Turn on WiFi | Enables WiFi |
| Send message to John saying hi | SMS |
| Open WhatsApp | Launches app |
| Volume up | Raises media volume |
| Turn on flashlight | Torch on |
| Lock the phone | Screen lock |
| What time is it | Speaks time |
| Set alarm for 7am | Opens clock |

## Privacy
- All processing is on-device
- No data sent to OpenAI, Google AI, Claude, or any external AI
- Vosk STT runs locally
- TFLite wake word runs locally
- Optional: llama.cpp local LLM on device or LAN
