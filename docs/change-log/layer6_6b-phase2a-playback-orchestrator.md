# Layer 6.6B — Phase 2A Playback Orchestrator

# Goal
Build orchestration layer that turns a resolved PlaybackTarget + UserTier
into an immutable PlaybackExecutionPlan. No execution, no Spotify API,
no UI automation, no accessibility.

# Files Read
- docs/project/zara-current-state.md (stale, references Layer 4)
- docs/change-log/layer6_6a-smart-playback-foundation.md
- PlaybackModels.kt, PlaybackIntentParser.kt (confirmed recommendation
  patch already applied), PlaybackResolver.kt, StrategySelector.kt,
  UserTierDetector.kt, PlaybackCache.kt, PerformanceGuard.kt,
  DeepLinkManager.kt, VoiceSessionManager.kt, ActionExecutor.kt

# Files Created
- PlaybackExecutionType.kt
- PlaybackRoute.kt
- PlaybackExecutionPlan.kt
- PlaybackOrchestrator.kt

# Files Modified
None.

# Orchestrator Flow
```
PlaybackTarget + UserTier
 → cache lookup (PlaybackCache)
 → weak-resolution check
 → route determination
 → execution type determination
 → build PlaybackExecutionPlan
 → validate
 → cache store (on miss)
 → return plan | null
```
Not called from VoiceSessionManager/ActionExecutor. Inert until wired.

# Routing Rules
- weak resolution → FALLBACK_SEARCH (always, regardless of tier)
- PREMIUM → PREMIUM_DIRECT
- FREE → FREE_ASSISTED
- NOT_CONNECTED → FALLBACK_SEARCH

# Execution Plan
Immutable data class: route, executionType, resolvedQuery, targetApp,
userTier, cacheKey. `targetApp` left null (app-hint wiring deferred,
not in Phase 2A scope).

# Validation Rules
- blank resolvedQuery rejected unless type is PLAY_LIKED or SEARCH_ONLY
- SEARCH_ONLY must pair with FALLBACK_SEARCH route
- invalid plans return null, never throw

# Cache Integration
Checks `PlaybackCache.get(cacheKey)` before building; reuses cached
PlaybackTarget metadata if present. Stores new resolutions on cache miss
only. No execution triggered by cache hit/miss.

# Performance Rules
No threads, polling, timers, services, or coroutines. O(1) per call.
PerformanceGuard device-stress route tagging deferred — PerformanceGuard
currently exposes only result-count guards (MAX_RESULTS/shouldStop), not
a device-stress signal, so no real tagging hook exists yet. Documented
in code rather than faked.

# Backward Compatibility
No existing file modified. Media control fallback, Layers 4/5/6,
existing parser untouched.

# Test Cases
- SONG + PREMIUM → PREMIUM_DIRECT / PLAY_SONG
- PLAYLIST + FREE → FREE_ASSISTED / PLAY_PLAYLIST
- LIKED + FREE → FREE_ASSISTED / PLAY_LIKED (blank query allowed)
- UNKNOWN + PREMIUM → FALLBACK_SEARCH / SEARCH_ONLY
- blank query + SONG type → validate() rejects → null
- NOT_CONNECTED + SONG → FALLBACK_SEARCH / SEARCH_ONLY

# Confidence
90% — deviation flagged above (PerformanceGuard route-tagging deferred
since no stress signal exists; not faked). Everything else matches spec.
