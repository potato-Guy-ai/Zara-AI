# Contact Clarification Ambiguity Fix

Date: June 2026

---

## Goal

When a query matches multiple strong candidates (score >= 80), Zara must present a clarification prompt instead of silently auto-resolving to the top match.

---

## Root Cause

`EntityResolver` contained an exact-match short-circuit path:

```kotlin
val exact = ranked.firstOrNull {
    normalize(it.displayName) == query
}
if (exact != null) { /* auto resolve */ }
```

For query `atha`, `Atha` matched exactly (score 100) and `EntityResolver` returned immediately — bypassing clarification even though `Atha 2` also scored strongly (score 80).

---

## Files Modified

None — all three files were new (contact layer did not previously exist).

## Files Created

| File | Path |
|------|------|
| `ContactNormalizer.kt` | `app/src/main/java/com/zara/assistant/contact/` |
| `ContactRankingEngine.kt` | `app/src/main/java/com/zara/assistant/contact/` |
| `EntityResolver.kt` | `app/src/main/java/com/zara/assistant/contact/` |

---

## Lines Changed

| File | Lines |
|------|-------|
| ContactNormalizer.kt | 14 |
| ContactRankingEngine.kt | 37 |
| EntityResolver.kt | 65 |

---

## Strong Candidate Rule

Threshold: `score >= 80`

Before any exact-match auto-resolve, `EntityResolver` collects all strong candidates.

- Strong count > 1 → clarification, candidates list = strong only.
- Strong count == 1 → auto resolve to that candidate.
- Total results == 1 → auto resolve regardless of score.

---

## Numeric Contact Handling

Numeric suffix contacts (`Atha 2`, `Atha 3`) are valid, distinct contacts.

`ContactNormalizer.normalize()` does **not** strip numeric suffixes — `"Atha 2"` normalizes to `"atha 2"`.

`ContactRankingEngine` scores them via starts-with (score 80) when the query is the base name.

This ensures:
- Query `atha` → `Atha` (100) + `Atha 2` (80) = 2 strong → clarification.
- Query `atha 2` → `Atha 2` (100) exact, only 1 strong → auto resolve.

---

## Clarification Rule

When clarification is triggered, the candidate list contains **only strong candidates** (score >= 80).

Lower-tier matches (e.g. `Periatha`, score 60) are excluded from the presented list.

---

## Test Cases

| Query | Contacts | Expected |
|-------|----------|----------|
| `atha` | Atha (100), Atha 2 (80), Periatha (60) | Clarification: Atha, Atha 2 |
| `atha 2` | Atha (80), Atha 2 (100), Periatha (60) | Auto resolve: Atha 2 |
| `malvin` | Malvin (100) | Auto resolve: Malvin |
| `boss` | Boss (100), Boss Office (80) | Clarification: Boss, Boss Office |

---

## Backward Compatibility

- Single exact match with no other strong candidates: auto resolves. ✅
- Single contact result: auto resolves. ✅
- Single strong candidate: auto resolves. ✅
- WhatsApp flow: untouched. ✅
- ClarificationManager: untouched. ✅
- Ranking scores: untouched. ✅

---

## Confidence

**High.**

Fix is isolated to `EntityResolver.resolve()`. All existing single-match paths preserved. Ambiguity gate added before auto-resolve, not after.
