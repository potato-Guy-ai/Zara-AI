# Layer 6.6A — Smart Playback Foundation (Phase 1)

# Goal
Build the architecture foundation for smart music playback handling,
without implementing any execution, Spotify API integration, or
playback automation. Phase 1 only.

# Files Read
- VoiceSessionManager.kt
- ActionExecutor.kt
- ZaraIntent.kt
- MediaControlManager.kt
- TaskRegistry.kt
- docs/project/zara-architecture.md (stale — references Layer 4 as current; not authoritative for this task)
- docs/project/zara-current-state.md (stale — same)

# Files Created
- app/src/main/java/com/zara/assistant/playback/PlaybackModels.kt
- app/src/main/java/com/zara/assistant/playback/PlaybackIntentParser.kt
- app/src/main/java/com/zara/assistant/playback/PlaybackResolver.kt
- app/src/main/java/com/zara/assistant/playback/UserTierDetector.kt
- app/src/main/java/com/zara/assistant/playback/StrategySelector.kt
- app/src/main/java/com/zara/assistant/playback/PlaybackCache.kt
- app/src/main/java/com/zara/assistant/playback/PerformanceGuard.kt
- app/src/main/java/com/zara/assistant/playback/DeepLinkManager.kt

# Files Modified
None.

# Lines Changed
+296 lines total (new files only). Zero lines changed in any existing file.

# Playback Architecture
```
Voice
 → PlaybackIntentParser   (rule-based extraction)
 → PlaybackResolver       (classification → PlaybackTarget)
 → UserTierDetector       (placeholder tier state)
 → StrategySelector       (tier → strategy)
 → PlaybackCache          (in-memory ring buffer)
 → PerformanceGuard       (low-end device limits)
 → DeepLinkManager        (URI string generation)
 → Executor (future, not built)
```
None of these components are wired into `VoiceSessionManager`,
`IntentRouter`, or `ActionExecutor`. The package is fully standalone
and inert at runtime until a future phase wires it in.

# Parser
`PlaybackIntentParser.parse(rawText)` — deterministic, regex/string-based.
Strips "play " trigger, detects app hint (spotify/youtube/gaana/jiosaavn),
detects type hint (LIKED/PLAYLIST/ALBUM/ARTIST/SONG), extracts target string.
No AI, no ML, O(n) string operations only.

# Resolver
`PlaybackResolver.resolve(intent)` — pure classification, maps
`PlaybackIntent` → `PlaybackTarget`. No API calls, no network.

# Tier Detection
`UserTierDetector.detect()` — returns placeholder in-memory `UserTier`
(default `NOT_CONNECTED`). No OAuth, no Spotify SDK/API calls. A test
hook (`setPlaceholderTier`) exists for future wiring but is not called
anywhere yet.

# Strategy Selection
`StrategySelector.select(tier)` — deterministic map:
PREMIUM → API_MODE, FREE → DEEPLINK_MODE, NOT_CONNECTED → LOCAL_ASSIST_MODE.

# Cache
`PlaybackCache` — `ArrayDeque`-backed ring buffer, max 20 entries, 10-minute
TTL, lazy eviction on read/write. No Room, no DataStore, no disk I/O.

# Performance Guard
`PerformanceGuard` — `MAX_RESULTS = 3`, list-truncation helper, early-stop
helper for loop guards. `TIMEOUT_HINT_MS` is metadata only — not enforced
via any timer or thread in this phase.

# Deep Link Manager
`DeepLinkManager.generate(target)` — builds `spotify:track:search:...`,
`spotify:playlist:search:...`, `spotify:album:search:...`,
`spotify:artist:search:...`, `spotify:track:liked` URI strings. Returns the
string only — no `Intent`, no `Context`, no launch.

# Backward Compatibility
No existing file was modified. Layers 4, 5, 6, 6.5A–6.5F, media control,
contact resolution, workflow, confirmation, and recovery are all untouched.
New package is additive and currently dead code (not called from any
existing pipeline entry point).

# Test Cases (manual, not yet automated)
- "play believer" → SONG, target="believer"
- "play my gym playlist" → PLAYLIST, target="my gym"
- "play liked songs" → LIKED, target=null
- "play arijit songs" → ARTIST, target="arijit"
- "play relaxing music" → SONG, target="relaxing music" (no explicit marker matched)
- StrategySelector: PREMIUM→API_MODE, FREE→DEEPLINK_MODE, NOT_CONNECTED→LOCAL_ASSIST_MODE
- PlaybackCache: 21st `put()` evicts oldest entry; entries older than 10 min evicted on read

# Side Effects
None. No existing file touched. No new manifest entries. No new
permissions. No new dependencies. Package is unreferenced by the rest
of the app, so it has zero runtime effect until wired in a future phase.

# Confidence
95% — straightforward, scoped, additive-only implementation matching
the spec exactly. 5% reserved for "play relaxing music" type-detection
ambiguity (no ARTIST/PLAYLIST/ALBUM marker present, falls through to
SONG, which may not match Master's intended classification — flagging
for review, not fixing speculatively).
