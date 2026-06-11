# Contact EntityResolver — Strong Candidate Fix

## Root Cause
In the multiple-results branch of `EntityResolver.resolveContact()`, an exact-match check
(`normalized(displayName) == query`) auto-resolved unconditionally even when other strong
candidates (score >= 80) existed. For query `"atha"` with contacts `[Atha(100), Atha 2(80),
Periatha(60)]`, `exact = Atha` was found and auto-resolved, bypassing clarification entirely.

## Fix
Before any auto-resolve in the multiple-results branch, compute `strongCandidates`: all ranked
contacts whose score (`ContactRankingEngine.scoreOf()`) is >= `STRONG_THRESHOLD` (80).

- `strongCandidates.size > 1` → clarify using **only** strong candidates (score-60 excluded)
- `strongCandidates.size == 1` → auto-resolve to that one candidate
- `strongCandidates.size == 0` → existing exact-match / clarification fallback unchanged

`ContactRankingEngine.scoreOf(normQuery, normName)` extracted as public function to avoid
duplicate scoring logic.

## Files Modified
- `core/EntityResolver.kt`
- `core/ContactRankingEngine.kt`

## Lines Changed
~35

## Test Cases
| Query | Contacts | Strong | Result |
|---|---|---|---|
| `atha` | Atha(100), Atha 2(80), Periatha(60) | 2 | Clarify: Atha, Atha 2 |
| `atha 2` | Atha(80), Atha 2(100), Periatha(60) | 2 | Clarify: Atha 2, Atha |
| `malvin` | Malvin(100) | 1 | Auto-resolve: Malvin |
| `boss` | Boss Akka(100), Boss Amma(80) | 2 | Clarify: Boss Akka, Boss Amma |
| `mathan mama` | Mathan Mama(100) | 1 | Auto-resolve: Mathan Mama |

## Backward Compatibility
100% — single-candidate path unchanged. No-strong-candidate fallback preserves existing logic.

## Confidence
99%
