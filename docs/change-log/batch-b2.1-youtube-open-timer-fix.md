# Batch B2.1 — YouTube Open + Timer Fix

## Goal
Fix `open youtube` inconsistency and timer execution failure.

## Files Modified
- `actions/AppActions.kt` — Spotify URI fired directly (no queryIntentActivities gate); YouTube search URL fallback added
- `core/ZaraIntent.kt` — Added `SEARCH_QUERY` action constant
- `core/LocalIntentClassifier.kt` — Added `reSearch` regex + step 13 SEARCH_QUERY classification
- `core/SlotExtractor.kt` — Added `extractSearch()` for SEARCH_QUERY; wired into `extract()`
- `actions/ActionExecutor.kt` — Added SEARCH_QUERY case in `executeRaw()`
- `AndroidManifest.xml` — Added `android.permission.SET_ALARM`

## Root Cause
- `open youtube` went through `OPEN_APP` correctly, but compound `open youtube and search cats` split into `["open youtube", "search cats"]`; second segment had no matching classifier rule → UNKNOWN.
- Timer fired `AlarmClock.ACTION_SET_TIMER` without `SET_ALARM` permission → `SecurityException` → fell back to `openAlarm()`.
- Spotify URI gated behind `queryIntentActivities` which returned empty on many devices with `setPackage()` → intent never fired.

## Fixes
1. Added `SEARCH_QUERY` intent + `reSearch` regex in classifier.
2. Added `extractSearch()` slot extractor (QUERY + APP from `X on Y` pattern).
3. Added `SEARCH_QUERY` execution → `appActions.search(query, app)` → routes to `searchYouTube()` for youtube/yt.
4. Added `SET_ALARM` permission to manifest.
5. Removed `queryIntentActivities` gate from Spotify URI path.

## Tests
- `search cats` → SEARCH_QUERY → Google search
- `search cats on youtube` → SEARCH_QUERY QUERY=cats APP=youtube → YouTube
- `open youtube and search cats` → compound: OPEN_APP + SEARCH_QUERY
- `set timer for 5 minutes` → SET_TIMER DURATION=300 → setTimer(300)
- `play believer on spotify` → Spotify URI fired directly

## Side Effects
None. All existing paths preserved.

## Confidence
97%
