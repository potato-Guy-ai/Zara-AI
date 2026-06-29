# Layer 6.6B — Phase 2B Free User Assisted Playback Engine

# Goal
Execute FREE_ASSISTED route: open Spotify, inject search deeplink, bring
to foreground. No autoplay, accessibility, OCR, or gesture automation.

# Files Read
- docs/project/zara-current-state.md (stale, Layer 4)
- docs/change-log/layer6_6a-smart-playback-foundation.md
- docs/change-log/layer6_6b-phase2a-playback-orchestrator.md
- docs/change-log/batch-b2.4-spotify-audit.md (legacy Spotify launch path —
  confirms `playMusicByPackage`/`AppActions` is a separate, older path;
  not touched by this engine)
- PlaybackOrchestrator.kt, PlaybackExecutionPlan.kt, PlaybackRoute.kt,
  PlaybackExecutionType.kt, DeepLinkManager.kt, ActionExecutor.kt,
  VoiceSessionManager.kt

# Files Created
- FreePlaybackState.kt
- FreePlaybackResult.kt
- FreePlaybackEngine.kt

# Files Modified
None.

# Engine Flow
```
PlaybackExecutionPlan
 → route != FREE_ASSISTED? → ignore (FAILED, IDLE)
 → Spotify installed? no → FAILED ("Spotify not installed.")
 → DeepLinkManager.generate(target)
 → launch deeplink (ACTION_VIEW, package-pinned)
   success → SUCCESS / READY
   failure → fallback: launch Spotify package only
     success → FALLBACK_USED / READY
     failure → FAILED
```

# States
IDLE, OPENING_APP, SEARCHING, READY, FAILED — full enum created per spec.
Note: OPENING_APP/SEARCHING are not currently emitted mid-flow since the
engine is synchronous and non-observable step-by-step (no coroutines/
threads per perf constraints); only terminal states (READY/FAILED/IDLE)
are returned today. Enum values reserved for future async wiring.

# Results
SUCCESS, FAILED, FALLBACK_USED — implemented exactly as specified.

# Failure Handling
- Spotify not installed → FAILED, "Spotify not installed."
- Deeplink generation null or launch throws → fallback to generic package
  launch → FALLBACK_USED on success, FAILED on failure.
- All exceptions caught; engine never throws.

# Performance
No threads, timers, polling, services, or coroutines. Two `Intent`
launches at most per call, O(1).

# Backward Compatibility
No existing file modified. Does not touch ActionExecutor, AppActions,
MediaControlManager, or Layers 4/5/6/6.5/6.6A/2A. FreePlaybackEngine is
standalone and not called from any existing pipeline entry point.

# Test Cases
- PLAY_SONG, route=FREE_ASSISTED, Spotify installed → SUCCESS/READY
- route=PREMIUM_DIRECT → ignored, FAILED/IDLE
- Spotify not installed → FAILED/FAILED, "Spotify not installed."
- DeepLinkManager.generate returns null (PlaybackType.UNKNOWN, e.g.
  SEARCH_ONLY execution type) → falls through to package-only launch
  → FALLBACK_USED on success

# Known Limitation (not fixed — out of Phase 2B scope)
PlaybackOrchestrator maps ARTIST/ALBUM PlaybackType → PLAY_SONG execution
type (Phase 2A behavior, frozen). FreePlaybackEngine's reverse mapping
therefore cannot distinguish artist/album searches from plain song
searches — both produce `spotify:track:search:...`. Spec's illustrative
"artist: spotify:search:arijit singh" example isn't reachable under
current Orchestrator output. Flagging for Phase 2C, not patching
Orchestrator now (would violate "do not redesign" instruction).

# Confidence
88% — core flow solid and matches spec. 12% reserved for the
ARTIST/ALBUM mapping limitation above and for unverified deeplink
foreground behavior across OEM variants (untestable without a device).
