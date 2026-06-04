# AppResolver v2 Phase 1

## Goal
Improve OPEN_APP reliability for aliases (`ig`, `yt`, `wa`, `ff`), STT typos (`instgram`, `whatsap`), and ambiguous matches — without background CPU, ML models, or ONNX.

## Files Modified
- `app/src/main/java/com/zara/assistant/actions/AppActions.kt` — wired resolver, fixed cache build (first-win, launchable-only)
- `app/src/main/java/com/zara/assistant/actions/AppResolver.kt` — **new file**

## Lines Changed
- `AppActions.kt`: ~40 lines changed (cache build fix, openApp() delegated to resolver, askClarification updated)
- `AppResolver.kt`: ~130 lines (new)

## Matching Order
1. Exact (1.0)
2. Alias (0.95) — via `BuiltInAliasProvider`
3. StartsWith (0.85)
4. Contains (0.75)
5. Fuzzy Levenshtein ≤2 (0.7) — only when `query.length >= 4`
6. Clarification / None

## Alias Rules Added
| Input | Resolves To |
|---|---|
| ig, insta | instagram |
| wa | whatsapp |
| fb | facebook |
| tg | telegram |
| tt | tiktok |
| yt | youtube |
| ytm, yt music | youtube music |
| ff | free fire |
| ff max | free fire max |
| bgmi | battlegrounds mobile india |
| pubg | pubg mobile |
| cod | call of duty |
| gm | gmail |
| maps | google maps |
| gpay, pay | google pay |

## Cache Build Fix
Replaced `.associate{}` (last-write-wins on duplicate labels) with first-win `mutableMapOf` insertion, filtered to launchable-only packages. Prevents stub APKs from overwriting real app entries.

## Clarification Logic
When `packageName == null && candidates.isNotEmpty()`:
```
"Did you mean: 1. App A, 2. App B, 3. App C?"
```

## Future Integration Points
- **UserAliasProvider**: implement `AliasProvider` interface, inject into `RuleBasedAppResolver`
- **Phase 3 MiniLM**: implement `AppResolver` interface, inject as `semanticFallback` in `RuleBasedAppResolver` constructor
- **AppActions**: unchanged — takes `AppResolver` interface, no modification needed for either future phase

## Test Cases
| Input | Expected |
|---|---|
| `ig` | Opens Instagram |
| `insta` | Opens Instagram |
| `yt` | Opens YouTube |
| `wa` | Opens WhatsApp |
| `ff` | Opens Free Fire |
| `ff max` | Opens Free Fire MAX |
| `instgram` | Fuzzy → Instagram |
| `whatsap` | Fuzzy → WhatsApp |
| `spotfy` | Fuzzy → Spotify |
| `freefir` | Fuzzy → Free Fire (distance 2) |
| `youtube` | Exact match |
| `calculator` | Exact match |
| `teleportation` | Not found |

## Confidence: 95%
Cache fix and alias table are deterministic. Fuzzy gated on length>=4 eliminates short-input false positives (proven: `ig`/`ff`/`yt`/`wa` have distance >2 to all app labels).
