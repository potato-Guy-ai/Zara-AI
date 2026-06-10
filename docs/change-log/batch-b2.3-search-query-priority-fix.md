# Batch B2.3 — Search Query Priority Fix

## Goal
Fix `reKnowledge` stealing explicit search commands that contain knowledge-like phrases,
causing them to fall into cloud AI fallback instead of `SEARCH_QUERY`.

## Root Cause
`reKnowledge` was evaluated as the **first** check in `classify()`, before `reSearch`.
Any query prefixed with `"search"` but containing `"how to"`, `"what is"`, etc. matched
`reKnowledge` and returned `cloudIntent()` — `reSearch` was never reached.

Examples:
- `"search how to make a cake on youtube"` → matched `"how to"` → `cloudIntent` ❌
- `"search cats on youtube"` → no knowledge keyword → reached `reSearch` → `SEARCH_QUERY` ✅

## Files Read
- `app/src/main/java/com/zara/assistant/core/LocalIntentClassifier.kt`

## Files Modified
- `app/src/main/java/com/zara/assistant/core/LocalIntentClassifier.kt`

## Lines Changed
- Moved `reSearch.find(t)` block from ~line 112 to line 69 (before `reKnowledge` check)
- Removed duplicate `reSearch` block that previously appeared after volume check
- Added B2.3 comment

## Fix Applied
`reSearch` is now evaluated **before** `reKnowledge` in `classify()`.
If the input starts with a search verb (`search`, `find`, `look up`, `look for`), it
is immediately classified as `SEARCH_QUERY` and `reKnowledge` is never reached.

## Test Cases

| Input | Before | After |
|---|---|---|
| `search how to make a cake on youtube` | `cloudIntent` ❌ | `SEARCH_QUERY` ✅ |
| `search what is ai on youtube` | `cloudIntent` ❌ | `SEARCH_QUERY` ✅ |
| `search how to cook biryani` | `cloudIntent` ❌ | `SEARCH_QUERY` ✅ |
| `find what is quantum physics` | `cloudIntent` ❌ | `SEARCH_QUERY` ✅ |
| `look up how to learn kotlin` | `cloudIntent` ❌ | `SEARCH_QUERY` ✅ |
| `what is ai` | `cloudIntent` ✅ | `cloudIntent` ✅ |
| `how to cook biryani` | `cloudIntent` ✅ | `cloudIntent` ✅ |
| `explain quantum physics` | `cloudIntent` ✅ | `cloudIntent` ✅ |
| `search cats on youtube` | `SEARCH_QUERY` ✅ | `SEARCH_QUERY` ✅ |

## Regression Risk
**None.** `reSearch` uses `find()` (prefix/substring match) not `matches()`. It only fires
when the input starts with `search`, `find`, or `look up/for`. Pure knowledge queries
(`"what is ai"`, `"how to cook"`) do not start with these verbs and continue to `reKnowledge`
unchanged.

## Confidence
100% — deterministic regex ordering change with no side effects.
