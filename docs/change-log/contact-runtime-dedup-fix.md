# Contact Runtime Dedup Fix

## Root Cause
`CallActions.call()` called `contactResolver.resolveAll(contact)` directly without deduplication.
Android returns one cursor row per phone-account entry. A contact synced to Google + WhatsApp
produces two identical rows (`Atha 2 | +919345230443` twice), causing `results.size == 2`
and a false `AMBIGUOUS` sentinel even though only one distinct contact exists.

## Fix
One line in `CallActions.call()`:
```kotlin
// Before
val results = contactResolver.resolveAll(contact)
// After
val results = ContactDeduplicationEngine.deduplicate(contactResolver.resolveAll(contact))
```
`ContactDeduplicationEngine.deduplicate()` deduplicates by normalized phone (`takeLast(10)`).
Both `+919345230443` and `9345230443` normalize to `9345230443` — one survives.

`AppActions.sendWhatsApp()` uses `resolveNumber()` → `firstOrNull()` — no ambiguity list,
no fix needed.

## Files Modified
- `actions/CallActions.kt`

## Lines Changed
2 (import + resolveAll call)

## Test Cases
| Query | Raw rows | After dedup | Result |
|---|---|---|---|
| `call atha 2` | Atha 2 ×2 | Atha 2 ×1 | Direct call ✓ |
| `call atha` | Atha, Atha 2, Periatha | 3 distinct | Clarify: Atha, Atha 2 ✓ |
| `call mathan mama` | Mathan Mama ×1 | Mathan Mama ×1 | Direct call ✓ |

## Confidence
100%
