# Layer 4A — Slot Extraction Infrastructure

## Goal

Insert a stateless `SlotExtractor` stage between `LocalIntentClassifier` and `IntentRouter`.
This is infrastructure only. v1 is a pass-through — no extraction logic.
Future phases will plug feature-specific extractors into `SlotExtractor.extract()`.

---

## Files Read

- `docs/project/zara-architecture.md`
- `docs/project/zara-current-state.md`
- `docs/change-log/stt-correction-boundary-fix.md`
- `app/src/main/java/com/zara/assistant/core/ZaraIntent.kt`
- `app/src/main/java/com/zara/assistant/core/LocalIntentClassifier.kt`
- `app/src/main/java/com/zara/assistant/core/IntentRouter.kt`
- `app/src/main/java/com/zara/assistant/voice/VoiceSessionManager.kt`

---

## Files Modified

| File | Change |
|---|---|
| `core/ZaraIntent.kt` | Added Layer 4A slot namespace to `IntentExtra` |
| `voice/VoiceSessionManager.kt` | Inserted `SlotExtractor.extract()` into all three pipeline call sites |

## Files Created

| File | Purpose |
|---|---|
| `core/SlotExtractor.kt` | New Layer 4A infrastructure component |
| `docs/change-log/layer4a-slot-infrastructure.md` | This document |

---

## Lines Changed

**ZaraIntent.kt:** +7 lines (slot namespace constants)

**VoiceSessionManager.kt:** +1 import, +1 line per pipeline call site × 3 call sites = +4 lines net

**SlotExtractor.kt:** 24 lines (new file)

---

## Pipeline Changes

Before:
```
STT → SttCorrectionLayer → LocalIntentClassifier → IntentRouter → ActionExecutor
```

After:
```
STT → SttCorrectionLayer → LocalIntentClassifier → SlotExtractor → IntentRouter → ActionExecutor
```

All three call sites in `VoiceSessionManager` updated:
- `startListeningSession()` (wake word path)
- `startListeningSession(onResponse)` (manual mic path)
- `processText()` (typed text path)

---

## Slot Namespace Added

Added to `IntentExtra` in `ZaraIntent.kt`:

| Key | Value | Purpose |
|---|---|---|
| `APP` | `"app"` | Target application name |
| `SONG` | `"song"` | Song title |
| `ARTIST` | `"artist"` | Artist name |
| `TIME` | `"time"` | Time string |
| `DESTINATION` | `"destination"` | Navigation destination |
| `QUERY` | `"query"` | General search/query string |

Not populated in v1. Reserved for future extractors.

---

## SlotExtractor API

```kotlin
object SlotExtractor {
    fun extract(intent: ZaraIntent): ZaraIntent
}
```

- `object` — stateless singleton, no instantiation required
- No Android dependencies
- No coroutines
- No background threads
- No model loading
- Pure function: same input always produces same output
- v1 returns intent unchanged

---

## Test Results

Infrastructure is a transparent pass-through. All existing behavior is preserved.
No functional change in v1 — `SlotExtractor.extract(intent)` returns `intent` unchanged.
Existing intent routing and action execution unaffected.

---

## Side Effects

None. Pass-through adds zero overhead beyond a single function call and return.

---

## Confidence

100% — pass-through is deterministic. Pipeline insertion is verified across all three call sites in `VoiceSessionManager`.
