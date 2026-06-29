# Layer 6.6B — Phase 2C.1 Premium Direct Playback Architecture Foundation

# Goal
Build full PREMIUM_DIRECT architecture using a mocked SpotifyApiClient.
No HTTP, no OAuth, no real network, no credentials. Ready for Phase 2C.2
to swap in a real implementation without redesign.

# Files Read
- docs/change-log/layer6_6b_phase2b-free-assisted-engine.md (latest 6.6 changelog)
- PlaybackOrchestrator.kt, PlaybackExecutionPlan.kt, PlaybackRoute.kt,
  UserTierDetector.kt, StrategySelector.kt, FreePlaybackEngine.kt

# Files Created
- SpotifyAuthState.kt
- SpotifyPlaybackState.kt
- SpotifyPlaybackResult.kt
- SpotifySearchResult.kt
- SpotifyApiClient.kt (interface + MockSpotifyApiClient)
- PremiumPlaybackEngine.kt

# Files Modified
None.

# Engine Flow
```
PlaybackExecutionPlan
 → route != PREMIUM_DIRECT? → ignore (FAILED, IDLE)
 → client.getAuthState() != AUTHENTICATED? → AUTH_REQUIRED
 → search(plan) via execution-type dispatch → null? → FAILED
 → client.play(uri) → SUCCESS / FAILED
```

# API Client Interface
`SpotifyApiClient`: getAuthState, searchTrack, searchPlaylist,
searchArtist, getRecommendations, play(uri). `MockSpotifyApiClient`
implements it with deterministic in-memory responses, no network.
`PremiumPlaybackEngine` takes the client via constructor injection
(defaults to the mock) — Phase 2C.2 swaps the default/call-site only.

# Mock Behavior
- searchTrack/searchPlaylist/searchArtist: blank query → null, else
  deterministic `spotify:<type>:mock:<normalized_query>` URI.
- getRecommendations: never null; falls back to "your top tracks" name
  on blank query.
- play(uri): succeeds for any non-blank uri.
- Default mock auth state: AUTHENTICATED (toggle via `setMockAuthState`
  for testing AUTH_REQUIRED/TOKEN_EXPIRED paths).

# Auth Rules
NOT_AUTHENTICATED or TOKEN_EXPIRED → AUTH_REQUIRED result, no search,
no playback attempted. Matches spec exactly.

# Search Rules
Dispatch by `PlaybackExecutionType` (not raw `PlaybackType`), consistent
with FreePlaybackEngine's existing pattern:
- PLAY_SONG → searchTrack
- PLAY_PLAYLIST → searchPlaylist
- PLAY_RECOMMENDATION, PLAY_LIKED → getRecommendations
- SEARCH_ONLY → no search, treated as no-match → FAILED

# Known Limitation (not fixed — flagged, not patched)
Spec asks for ARTIST → searchArtist(). `searchArtist` exists on the
interface/mock as specified, but `PlaybackOrchestrator` (frozen Phase 2A)
maps PlaybackType.ARTIST → PlaybackExecutionType.PLAY_SONG, same
limitation already documented in the Phase 2B changelog. Under current
Orchestrator output, `PremiumPlaybackEngine.search()` cannot route to
`searchArtist()` — it receives PLAY_SONG and calls searchTrack() instead.
Not patching Orchestrator now (explicitly frozen / out of scope).

# Playback Rules
Mocked `play(uri)` only. No real playback. No app UI. No media control
fallback in this engine — strictly PREMIUM_DIRECT, no silent downgrade.

# Phase 2C.2 Boundary
To go live: implement `SpotifyApiClient` with real OkHttp/Retrofit +
OAuth token storage, swap `MockSpotifyApiClient` for the real impl at
`PremiumPlaybackEngine` construction sites. No changes needed to
`PremiumPlaybackEngine`, the state/result enums, or `SpotifySearchResult`
— shape is already HTTP-response-compatible.

# Backward Compatibility
No existing file modified. Does not touch Orchestrator, FreePlaybackEngine,
ActionExecutor, MediaControlManager, or Layers 4/5/6/6.5/6.6A/2A/2B.

# Test Cases
- PREMIUM_DIRECT + PLAY_SONG, mock AUTHENTICATED → SUCCESS/PLAYING
- PREMIUM_DIRECT + auth=NOT_AUTHENTICATED → AUTH_REQUIRED/AUTH_REQUIRED, no search/play called
- PREMIUM_DIRECT + PLAY_PLAYLIST → searchPlaylist path → SUCCESS
- PREMIUM_DIRECT + SEARCH_ONLY → FAILED (no match)
- route=FREE_ASSISTED passed in → ignored, FAILED/IDLE

# Confidence
92% — clean mocked architecture, matches spec. 8% reserved for the
ARTIST-routing limitation above (inherited from frozen Orchestrator,
not introduced here) and for interface shape assumptions Phase 2C.2's
real API may need to adjust slightly (e.g. pagination, multiple
candidates) when real responses arrive.
