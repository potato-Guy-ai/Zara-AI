# Layer 5.1 — Entity Resolution

## Goal

Convert raw extracted slots into resolved real-world entities.
Inserted between `SlotExtractor` and `IntentRouter`. Additive only.

---

## Files Read

- `core/SlotExtractor.kt`, `core/ZaraIntent.kt`
- `actions/AppResolver.kt`, `actions/ContactResolver.kt`
- `voice/VoiceSessionManager.kt`
- All Layer 4A/4B/4C changelogs

---

## Files Modified

| File | Change |
|---|---|
| `core/ZaraIntent.kt` | Added Layer 5.1 constants: `CONTACT_NAME`, `PHONE_NUMBER`, `APP_PACKAGE`, `APP_NAME`, `ENTITY_CONFIDENCE`, `ENTITY_CANDIDATES`, `NEEDS_CLARIFICATION` |
| `voice/VoiceSessionManager.kt` | Inserted `entityResolver.resolve()` after `SlotExtractor.extract()` in all 3 pipeline paths |

## Files Created

| File | Purpose |
|---|---|
| `core/EntityResolver.kt` | New Layer 5.1 pipeline stage |
| `docs/change-log/layer5-1-entity-resolution.md` | This document |

---

## Lines Changed

- `ZaraIntent.kt`: +10 lines
- `VoiceSessionManager.kt`: +2 lines (import + instantiation + 3 call site wraps)
- `EntityResolver.kt`: ~90 lines (new file)

---

## Contact Resolution

Input: `extra[RECIPIENT]`

Resolution order:
1. Strip safe suffixes (bro, dude, anna, akka, machi, machan, boss, sis, friend, frnd)
2. `ContactResolver.resolveAll()` (LIKE query via ContactsContract)
3. Single result → `CONTACT_NAME` + `PHONE_NUMBER` + `ENTITY_CONFIDENCE=1.0`
4. Multiple results → exact display name match wins; else `NEEDS_CLARIFICATION=true` + `ENTITY_CANDIDATES`
5. No result → intent returned unchanged

---

## App Resolution

Input: `extra[APP]`

Reuses `RuleBasedAppResolver` with lazy-cached app map.

- Match → `APP_PACKAGE` + `APP_NAME` + `ENTITY_CONFIDENCE`
- Multiple candidates → `NEEDS_CLARIFICATION=true` + `ENTITY_CANDIDATES`
- No match → intent unchanged

---

## Pipeline Changes

Before:
```
SlotExtractor → IntentRouter
```
After:
```
SlotExtractor → EntityResolver → IntentRouter
```

---

## Backward Compatibility

- `target` — never modified
- `rawText` — never modified
- All existing slots — preserved
- `ActionExecutor` — not modified
- All existing routing — unchanged

---

## Test Cases

| Input | CONTACT_NAME | PHONE_NUMBER | APP_PACKAGE | Notes |
|---|---|---|---|---|
| `call ahmed` | Ahmed Hassan | +966... | — | Single match |
| `call ahmed bro` | Ahmed Hassan | +966... | — | Suffix stripped |
| `play believer on spotify` | — | — | com.spotify.music | APP resolved |
| `navigate to airport on google maps` | — | — | com.google.android.apps.maps | APP resolved |
| `message unknown person` | — | — | — | No match, pass-through |
| `call common name` (2 contacts) | — | — | — | NEEDS_CLARIFICATION=true |

---

## Side Effects

- App cache allocated once per `EntityResolver` instance (held in `VoiceSessionManager`)
- Contact queries run on calling coroutine (Dispatchers.Default via IntentRouter chain)
- No background threads introduced

---

## Confidence

98% — additive-only. Reuses proven `ContactResolver` and `RuleBasedAppResolver`. All existing paths unaffected.
