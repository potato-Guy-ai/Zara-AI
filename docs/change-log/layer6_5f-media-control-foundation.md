# Layer 6.5F Phase 1 — Media Control Foundation

## Goal
Create media control foundation using Android MediaSession APIs.
Phase 1: architecture only. No Spotify-specific logic. No accessibility automation.
No search. No song/artist matching.

## Files Read
- `docs/project/zara-architecture.md`
- `docs/project/zara-current-state.md`
- `execution/TaskRegistry.kt`
- `execution/ExecutionModels.kt`
- `core/ZaraIntent.kt`
- `core/LocalIntentClassifier.kt`
- `voice/VoiceSessionManager.kt`

## Files Created
- `media/MediaControlAction.kt`
- `media/MediaSessionLocator.kt`
- `media/MediaControlManager.kt`

## Files Modified
- `core/ZaraIntent.kt` — Added `MEDIA_CONTROL` action, `MEDIA_ACTION` extra key
- `core/LocalIntentClassifier.kt` — Added MEDIA_CONTROL classification step
- `actions/ActionExecutor.kt` — Added MEDIA_CONTROL case in executeRaw()

## Media Actions
PLAY, PAUSE, STOP, NEXT, PREVIOUS
Mapped from: pause / resume / play / next / previous / stop music / next song / etc.

## MediaSession Integration
`MediaSessionLocator.findActiveSession(context)` — called on demand only.
Uses `MediaSessionManager.getActiveSessions()` via `ZaraNotificationListener` component.
Prefers STATE_PLAYING session; falls back to first available.
Returns null if no session — caller returns "No active media is playing."
No polling. No background work.

## TaskRegistry Integration
After successful media command: `TaskRegistry.register(ActiveTask("music", pkg, pkg))`.
Allows future pronoun/continuation resolution ("pause it", "resume it").

## Continuation Integration
`MediaControlAction.fromText()` matches: "pause it", "resume it", "next song", "previous song".
These are classified as MEDIA_CONTROL — no change to ContinuationResolver needed.
Layer 6.5C PAUSE/PREVIOUS signals now have an execution path via MEDIA_CONTROL.

## Safety Rules
- No active session → "No active media is playing."
- No app launch on failure.
- No search on failure.
- SecurityException from MediaSessionManager handled gracefully → null session → safety message.

## Performance Notes
O(1) dispatch. MediaSession lookup is synchronous, on-demand only.
No timers, no polling, no services, no background coroutines.

## Backward Compatibility
100% — MEDIA_CONTROL is a new action. All existing paths unchanged.
`MediaControlAction.fromText()` only fires for exact media command phrases;
it is checked before `rePlay` to avoid conflict with `play believer on spotify`
(which has a target and goes to PLAY_MUSIC, not MEDIA_CONTROL).

## Test Cases
| Input | Expected |
|---|---|
| `pause` | MEDIA_CONTROL(PAUSE) → session paused / "Paused." |
| `resume` | MEDIA_CONTROL(PLAY) → session resumed / "Resuming." |
| `next song` | MEDIA_CONTROL(NEXT) → skip track |
| `previous` | MEDIA_CONTROL(PREVIOUS) → previous track |
| `stop music` | MEDIA_CONTROL(STOP) → stopped |
| `pause` (no session) | "No active media is playing." |
| `play believer on spotify` | PLAY_MUSIC (unchanged, has target) |
| `open youtube` | OPEN_APP (unchanged) |

## Side Effects
None. New `media/` package is additive.
`ZaraNotificationListener` must be active for `MediaSessionManager.getActiveSessions()` to succeed.
If not active, `SecurityException` is caught and returns null gracefully.

## Confidence
98%
