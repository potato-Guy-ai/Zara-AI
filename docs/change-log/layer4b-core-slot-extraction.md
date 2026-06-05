# Layer 4B — Core Slot Extraction

## Goal

Implement the first real extraction logic in `SlotExtractor`.
Deterministic, regex/rule-based, lightweight, no AI, no threads.
Slots are additive only — no existing behavior is modified.

---

## Files Read

- `docs/project/zara-architecture.md`
- `docs/project/zara-current-state.md`
- `docs/change-log/stt-correction-boundary-fix.md`
- `docs/change-log/layer4a-slot-infrastructure.md`
- `core/SlotExtractor.kt`
- `core/ZaraIntent.kt`
- `core/LocalIntentClassifier.kt`
- `actions/ActionExecutor.kt`
- `actions/AppActions.kt`
- `voice/VoiceSessionManager.kt`

---

## Files Modified

| File | Change |
|---|---|
| `core/SlotExtractor.kt` | Replaced pass-through with real extraction logic |
| `core/ZaraIntent.kt` | Added `CONTENT` slot key to `IntentExtra` |
| `actions/ActionExecutor.kt` | Wired `PLAY_MUSIC`, `SET_TIMER`, `NAVIGATE_TO` to slot data |
| `actions/AppActions.kt` | Added `setTimer()`, updated `playMusic()` and `navigateTo()` signatures |

---

## Lines Changed

- `SlotExtractor.kt`: ~70 lines (replaced 3-line stub)
- `ZaraIntent.kt`: +2 lines (`CONTENT` key)
- `ActionExecutor.kt`: ~15 lines modified
- `AppActions.kt`: ~40 lines added/modified

---

## Slot Namespace Added

| Key | Constant | Purpose |
|---|---|---|
| `content` | `IntentExtra.CONTENT` | Media content / song title (Layer 4B) |

All other required keys (`APP`, `QUERY`, `DURATION`, `CHANNEL`) were already present from Layer 4A.

---

## Extraction Rules

### Rule 1 — APP Extraction
Pattern: last occurrence of `" on "` in `intent.target`.
- Applies to: `PLAY_MUSIC`, `NAVIGATE_TO`
- Uses `lastIndexOf(" on ")` to handle titles containing the word "on".
- Example: `"play on my way on spotify"` → `CONTENT="on my way"`, `APP="spotify"`

### Rule 2 — CONTENT / QUERY Extraction
- `PLAY_MUSIC`: text before last `" on "` → `CONTENT`
- `NAVIGATE_TO`: text before last `" on "` → `QUERY`
- If no `" on "` found, intent returned unchanged.

### Rule 3 — DURATION Extraction
- Applies to: `SET_TIMER`
- Regex: matches `N hour(s)`, `N minute(s)`, `N second(s)` anywhere in `rawText`
- Accumulates all matches (e.g. "2 hours 30 minutes" = 9000s)
- Normalizes to integer seconds stored in `IntentExtra.DURATION`
- Examples:
  - `"5 minutes"` → `300`
  - `"30 seconds"` → `30`
  - `"2 hours"` → `7200`
  - `"1 hour 30 minutes"` → `5400`

---

## Backward Compatibility

- `target` is NEVER modified. Slots are additive via `extra` map copy.
- `OPEN_APP`, `CALL`, `SEND_SMS`, `SEND_WHATSAPP` paths in `ActionExecutor` — unchanged.
- `SET_TIMER` without a duration slot — falls back to `openAlarm()` as before.
- `NAVIGATE_TO` without app slot — falls back to generic geo URI as before.
- `PLAY_MUSIC` without slots — falls back to Spotify search on `target`, then generic music app.

---

## Test Cases

| Input | CONTENT / QUERY | APP | DURATION |
|---|---|---|---|
| `play believer on spotify` | `believer` | `spotify` | — |
| `play on my way on spotify` | `on my way` | `spotify` | — |
| `navigate to airport on google maps` | `airport` | `google maps` | — |
| `set timer for 5 minutes` | — | — | `300` |
| `set timer for 30 seconds` | — | — | `30` |
| `set timer for 2 hours` | — | — | `7200` |
| `set timer for 1 hour 30 minutes` | — | — | `5400` |
| `play music` (no "on") | — (unchanged) | — | — |
| `navigate to airport` (no "on") | — (unchanged) | — | — |
| `call ahmed` | — (not touched) | — | — |
| `open instagram` | — (not touched) | — | — |

---

## Side Effects

None. Extraction runs only during intent processing, inline, on the calling coroutine.
No background threads, no caches, no services introduced.

---

## Confidence

98% — regex rules are deterministic. `lastIndexOf` correctly handles edge cases with "on" in content titles. Timer accumulation handles compound durations. Fallback paths preserve all prior behavior.
