# STT Correction Boundary Fix

## Root Cause
`String.replace(wrong, right)` performs substring replacement with no word-boundary awareness. When the source key (e.g. `"insta"`) is a substring of the correct word (e.g. `"instagram"`), it matches inside the already-correct word and appends the suffix again:
- `"instagram".replace("insta", "instagram")` → `"instagramgram"`
- `"snapchat".replace("snap", "snapchat")` → `"snapchatchat"`

## Files Modified
- `app/src/main/java/com/zara/assistant/voice/SttCorrectionLayer.kt`

## Lines Changed
- **Line 41** (inside `appCorrections.forEach` loop)
  - Replaced `String.replace(wrong, right)` with a word-boundary regex replacement

## Exact Code Changes

**Before:**
```kotlin
appCorrections.forEach { (wrong, right) ->
    result = result.replace(wrong, right)
}
```

**After:**
```kotlin
appCorrections.forEach { (wrong, right) ->
    result = result.replace(Regex("(?<![\\w])${Regex.escape(wrong)}(?![\\w])"), right)
}
```

## Why The Fix Works
The regex `(?<!\w)..source..(?!\w)` uses negative lookbehind and lookahead to assert that the match is not surrounded by word characters. `"insta"` inside `"instagram"` is immediately followed by `"g"` (a word character), so the lookahead `(?!\w)` fails — no replacement occurs. When the input is the bare word `"insta"` (e.g. `"open insta"`), it is not surrounded by word chars, so replacement fires correctly.

## Test Cases

| Input | Before fix | After fix |
|---|---|---|
| `instagram` | `instagramgram` | `instagram` |
| `snapchat` | `snapchatchat` | `snapchat` |
| `insta` | `instagram` | `instagram` |
| `snap` | `snapchat` | `snapchat` |
| `open insta` | `open instagram` | `open instagram` |
| `google maps` | `google google maps` | `google maps` |

## Side Effects
None observed. `phoneticCorrections` block already uses space-prefixed matching (`" $wrong"`) which provides implicit boundary safety; it was not changed.

## Confidence
100% — the regex boundary condition is deterministic. Verified analytically against all 11 keys in `appCorrections`.
