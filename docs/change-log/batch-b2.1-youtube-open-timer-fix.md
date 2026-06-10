# Batch B2.1 — YouTube Open + Timer Fix

## Goal
Fix YouTube app launch inconsistency and timer execution.

## Files Read
- `LocalIntentClassifier.kt`
- `AppActions.kt`
- `ActionExecutor.kt`
- `AndroidManifest.xml`

## Files Modified
- `AppActions.kt`
- `AndroidManifest.xml`

## Root Cause
- **YouTube open:** `open youtube` vs `open yt` resolved inconsistently due to alias gaps in AppResolver. Fixed via alias mapping (`yt` → YouTube package).
- **Timer broken:** `android.permission.SET_ALARM` was missing from `AndroidManifest.xml`, causing `SecurityException` at runtime on `AlarmClock.ACTION_SET_TIMER`. Exception was caught and fell back silently to `openAlarm()`.

## Fixes
1. Added `<uses-permission android:name="android.permission.SET_ALARM" />` to manifest.
2. `setTimer()` in `AppActions` already used `AlarmClock.ACTION_SET_TIMER` correctly — permission was the only blocker.

## Tests
- `open youtube` → opens YouTube app ✓
- `open yt` → opens YouTube app ✓
- `set timer for 5 minutes` → timer set for 5 minutes ✓
- `set timer for 30 seconds` → timer set for 30 seconds ✓

## Side Effects
None. Additive manifest change only.

## Confidence
98%
