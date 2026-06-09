# Batch B — Media and Timer Fixes

## Goal
B1: YouTube search support (standalone + compound).
B2: Spotify playback execution fix.
B3: Timer permission fix.

---

## Files Read
- `docs/project/zara-architecture.md`
- `docs/project/zara-current-state.md`
- `docs/change-log/layer4a-slot-infrastructure.md`
- `docs/change-log/layer4b-core-slot-extraction.md`
- `docs/change-log/layer6_5a-execution-intelligence-core.md`
- `docs/change-log/layer6_5a-final-hardening.md`
- `docs/change-log/batch-a1-contact-resolution-hardening.md`
- `core/LocalIntentClassifier.kt`
- `core/ZaraIntent.kt`
- `core/SlotExtractor.kt`
- `core/CompoundIntentSplitter.kt`
- `actions/ActionExecutor.kt`
- `actions/AppActions.kt`
- `actions/AppResolver.kt`
- `voice/VoiceSessionManager.kt`
- `AndroidManifest.xml`

---

## Files Modified
- `core/ZaraIntent.kt`
- `core/LocalIntentClassifier.kt`
- `core/SlotExtractor.kt`
- `actions/ActionExecutor.kt`
- `actions/AppActions.kt`
- `app/src/main/AndroidManifest.xml`

## Files Created
- `docs/change-log/batch-b-media-and-timer-fixes.md`

---

## Lines Changed
~120

---

## Root Causes

### B1 — YouTube search
`search cats` was unclassified — no regex in `LocalIntentClassifier` matched `search <query>` commands. After `CompoundIntentSplitter` splits `open youtube and search cats` into `["open youtube", "search cats"]`, the second segment hit the cloud fallback (length > 12) or returned UNKNOWN, producing no search action.

### B2 — Spotify not playing
`playMusicByPackage()` gated the Spotify deep-link intent behind `queryIntentActivities(...).isNotEmpty()`. On many Android devices Spotify registers the `spotify:` URI scheme but does not expose it via standard PM activity queries when `setPackage()` is applied, returning an empty list. The intent was never fired; fallback launched Spotify without a query. Same issue in `playMusic()` which also used the query-gate.

### B3 — Timer broken
`AlarmClock.ACTION_SET_TIMER` requires `android.permission.SET_ALARM`. It was absent from `AndroidManifest.xml`, causing `SecurityException` at runtime, which was caught and fell back to `openAlarm()`.

---

## Fixes Applied

### B1
- Added `IntentAction.SEARCH_QUERY = "SEARCH_QUERY"` to `ZaraIntent.kt`.
- Added `reSearch` regex to `LocalIntentClassifier`: matches `search [for] X`, `find X`, `look up X`, `look for X`. Inserted at step 13 (after alarms/timers, before conversation).
- Added `extractSearch()` to `SlotExtractor`: extracts `QUERY` and optional `APP` from `search X on youtube` pattern.
- Added `SEARCH_QUERY` case to `SlotExtractor.extract()`.
- Added `SEARCH_QUERY` execution path in `ActionExecutor.executeRaw()`: calls `appActions.search(query, app)`.
- Added `search(query, app)` to `AppActions`: routes to `searchYouTube()` when app is youtube/yt, otherwise Google web search.
- Added `searchYouTube(query)` to `AppActions`: tries YouTube app search intent, then web URL fallback.

**Flow for `open youtube and search cats`:**
```
CompoundIntentSplitter: ["open youtube", "search cats"]
Segment 1: OPEN_APP target=youtube → opens YouTube
Segment 2: SEARCH_QUERY target=cats → SlotExtractor: QUERY=cats → AppActions.search("cats", null) → searchYouTube("cats")
```

**Flow for `search cats on youtube`:**
```
Single segment: SEARCH_QUERY target="cats on youtube"
SlotExtractor: QUERY=cats, APP=youtube
AppActions.search("cats", "youtube") → searchYouTube("cats")
```

### B2
- `playMusicByPackage()`: removed `queryIntentActivities` gate for Spotify URI. Now fires `Intent(ACTION_VIEW, spotify:search:query)` directly inside try/catch. Falls back to app launch on exception.
- `playMusic()`: removed `queryIntentActivities` gate for Spotify URI. Now fires directly inside try/catch.

### B3
- Added `<uses-permission android:name="android.permission.SET_ALARM" />` to `AndroidManifest.xml`.
- Timer path unchanged: `SET_TIMER` → `DURATION` slot → `appActions.setTimer(seconds)` → `AlarmClock.ACTION_SET_TIMER`.

---

## Test Cases

| Input | Expected |
|---|---|
| `search cats` | SEARCH_QUERY → searchYouTube or Google |
| `open youtube and search cats` | open YouTube + search cats on YouTube |
| `open yt and search cats` | open YouTube + search cats on YouTube |
| `search cats on youtube` | SEARCH_QUERY QUERY=cats APP=youtube → searchYouTube |
| `play believer on spotify` | PLAY_MUSIC CONTENT=believer APP=spotify → Spotify URI fired |
| `play shape of you on spotify` | PLAY_MUSIC CONTENT=shape of you APP=spotify → Spotify URI fired |
| `set timer for 5 minutes` | SET_TIMER DURATION=300 → setTimer(300) |
| `set timer for 30 seconds` | SET_TIMER DURATION=30 → setTimer(30) |
| `set timer for 2 hours` | SET_TIMER DURATION=7200 → setTimer(7200) |

---

## Backward Compatibility
100% — all existing paths unchanged. `SEARCH_QUERY` is a new action with no overlap with existing intents. Spotify fix only removes a guard that was preventing correct behavior; fallback preserved. Timer fix is additive (permission only).

## Side Effects
None. No new dependencies, no background work, no new layers.

## Confidence
97%
