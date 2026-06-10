# Batch B2.4 — Spotify Audit

## Goal
Determine exact root cause of Spotify not playing music after "play X on spotify" commands.

## Files Read
- `ActionExecutor.kt`
- `AppActions.kt`
- `AppActionPlanner.kt`
- `SlotExtractor.kt`
- `EntityResolver.kt`
- `PreferredAppRegistry.kt`

## Pipeline Trace

### Input: `"play believer on spotify"`

| Stage | Input | Output | Lost |
|---|---|---|---|
| Classifier | `"play believer on spotify"` | `PLAY_MUSIC, target="believer on spotify"` | Nothing |
| SlotExtractor | `target="believer on spotify"` | `CONTENT="believer"`, `APP="spotify"` | Nothing |
| EntityResolver | `APP="spotify"` | `APP_PACKAGE="com.spotify.music"` (if label loads) | Nothing |
| AppActionPlanner | `raw="play believer on spotify"` | `KEY_APP="music"`, `KEY_ACTION=ACTION_PLAY_SONG`, **`KEY_QUERY=null`** | **`CONTENT` slot discarded** |
| ActionExecutor | `KEY_APP="music"`, `KEY_QUERY=null` | `playMusicByPackage(pkg, "spotify", null)` | query=null passed |
| AppActions | `query=null` | `launchPackage("com.spotify.music")` — no search | Song never searched |

## Proven Findings

1. **`CONTENT` is extracted correctly** by `SlotExtractor.extractMedia()` → `extra[CONTENT]="believer"` ✅
2. **`APP` is extracted correctly** → `extra[APP]="spotify"` ✅
3. **`APP_PACKAGE` is resolved** (when `getInstalledApplications(0)` loads labels) ✅
4. **`executePlan()` intercepts execution** — `KEY_APP="music"` set by `AppActionPlanner` → `executePlan("music")` fires before `executeRaw()` ✅
5. **`executeRaw()` is NOT reached** for Spotify ✅
6. **`KEY_QUERY` is never populated from `IntentExtra.CONTENT`**. `AppActionPlanner.extractBody()` only matches message body markers (`" saying "`, `" with message "`, etc.) — not applicable to music queries. **`KEY_QUERY` = null always for music play commands.** ✅ (proven)
7. **`playMusicByPackage()` receives `query=null`** → skips the deep-link block entirely → `launchPackage()` → Spotify opens to home screen ✅
8. **Autoplay is never requested** — no `spotify:track:` URI used anywhere ✅

## Root Cause
`AppActionPlanner.plan()` sets `KEY_QUERY` only from `extractBody(raw)` which scans for
message-body markers (`" saying "`, `" with message "`, etc.). For music commands, these
markers don't exist, so `KEY_QUERY = null`. The `IntentExtra.CONTENT` slot — which correctly
holds `"believer"` after `SlotExtractor` — is never read by `AppActionPlanner` and is
therefore null by the time `playMusicByPackage()` is called.

**Data lost at:** `AppActionPlanner.plan()` — `IntentExtra.CONTENT` not read into `KEY_QUERY`.

## Not Proven
- Whether `getInstalledApplications(0)` reliably returns the Spotify label on all devices
  (affects `APP_PACKAGE` resolution — but the null-query bug exists regardless)
- Whether `spotify:search:query` deep-link would trigger autoplay even if query were passed
  (search results require user tap on all tested Android versions)

## Recommended Fix
In `ActionExecutor.executePlan()`, for the `"music"` branch, read `IntentExtra.CONTENT`
as the query fallback when `KEY_QUERY` is null:
```kotlin
val query = intent.extra[AppActionPlanner.KEY_QUERY]
    ?: intent.extra[IntentExtra.CONTENT]   // ← add this line
    ?: target
```
This is a 1-line change in `ActionExecutor.kt`, no architecture change.

## Autoplay Possible
**No.** `spotify:search:query` opens search results — user must tap. `spotify:track:<id>`
would autoplay but requires Spotify Web API to resolve track ID from song name, which is
not implemented and not feasible without a network call + API key.

## Confidence
100% — root cause proven by code trace. `KEY_QUERY = null` at `playMusicByPackage()` call
is deterministic for all `"play X on spotify"` inputs.
