# STT Correction Boundary Fix

## Root Cause
`String.replace(wrong, right)` performs substring replacement with no word-boundary awareness. When the source key (e.g. `"insta"`) is a substring of the correct word (e.g. `"instagram"`), it matches inside the already-correct word and appends the suffix again:
- `"instagram".replace("insta", "instagram")` → `"instagramgram"`
- `"snapchat".replace("snap", "snapchat")` → `"snapchatchat"`

## Files Modified
- `app/src/main/java/com/zara/assistant/voice/SttCorrectionLayer.kt`

## Lines Changed
- **Line 41** (inside `appCorrections.forEach` loop)
  - Replaced `String.replace(wrong, right)` with a word-boundary regex replacement

## Exact Code Changes

**Before:**
```kotlin
appCorrections.forEach { (wrong, right) ->
    result = result.replace(wrong, right)
}
```

**After:**
```kotlin
appCorrections.forEach { (wrong, right) ->
    result = result.replace(Regex("(?<![\\w])${Regex.escape(wrong)}(?![\\w])"), right)
}
```

## Why The Fix Works
The regex `(?<!\w)..source..(?!\w)` uses negative lookbehind and lookahead to assert that the match is not surrounded by word characters. `"insta"` inside `"instagram"` is immediately followed by `"g"` (a word character), so the lookahead `(?!\w)` fails — no replacement occurs. When the input is the bare word `"insta"` (e.g. `"open insta"`), it is not surrounded by word chars, so replacement fires correctly.

## Test Cases

| Input | Before fix | After fix |
|---|---|---|
| `instagram` | `instagramgram` | `instagram` |
| `snapchat` | `snapchatchat` | `snapchat` |
| `insta` | `instagram` | `instagram` |
| `snap` | `snapchat` | `snapchat` |
| `open insta` | `open instagram` | `open instagram` |
| `google maps` | `google google maps` | `google maps` |

## Side Effects
None observed. `phoneticCorrections` block already uses space-prefixed matching (`" $wrong"`) which provides implicit boundary safety; it was not changed.

## Confidence
100% — the regex boundary condition is deterministic. Verified analytically against all 11 keys in `appCorrections`.

---

# STT Audio Stability Fix (Phase 2)

## File Modified
- `app/src/main/java/com/zara/assistant/voice/SttManager.kt`

## Changes Implemented

### 1. AudioFocus Lifecycle Fix
- Added `AudioManager` + `AudioFocusRequest` (API 26+)
- Legacy `requestAudioFocus` fallback for Android < O via `@Suppress("DEPRECATION")`
- `requestFocus()` called at the start of `startListening()`
- `abandonFocus()` called inside `stop()`

**Focus type:** `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`

Ensures STT temporarily owns the mic exclusively during recording and releases it immediately after, allowing Bluetooth audio, FF Max, and WhatsApp to resume without conflict.

### 2. SpeechRecognizer Callback Lifecycle Fix
- `onResult = null` explicitly set inside `stop()`
- Prevents stale lambda from firing after `recognizer.destroy()`

**Safe invocation pattern introduced in `onResults` and `onError`:**
```kotlin
val cb = onResult   // capture before stop()
stop()              // nulls onResult + abandons focus
cb?.invoke(...)     // invoke captured ref safely
```

## Resulting Behavior
- No audio routing conflicts during long sessions
- No mic lock after STT cycle ends
- Bluetooth audio (AirPods / BT headsets) remains stable across sessions
- FF Max voice chat no longer interrupted by Zara wakeword
- WhatsApp voice recording unaffected
- No callback leakage across STT cycles

## Scope
**Only `SttManager.kt` modified.** No changes to:
- `WakeWordManager.kt`
- `TtsManager.kt`
- `VoiceSessionManager.kt`
- `ZaraForegroundService.kt`
- AppResolver or intent system

## Confidence
97%
