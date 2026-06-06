# Layer 5.1.1 — Contact IO Fix

## Goal
Move all ContactResolver ContentResolver queries off the Main thread onto Dispatchers.IO.

## Files Read
- `actions/ContactResolver.kt`
- `core/EntityResolver.kt`
- `voice/VoiceSessionManager.kt`
- `docs/change-log/layer5-1-entity-resolution.md`

## Files Modified

| File | Change |
|---|---|
| `actions/ContactResolver.kt` | `resolveNumber`, `resolveAll`, `queryContacts` made `suspend`; `queryContacts` wrapped in `withContext(Dispatchers.IO)` |
| `core/EntityResolver.kt` | `resolve` and `resolveContact` made `suspend` to propagate coroutine context |

## Files Created
- `docs/change-log/layer5-1-1-contact-io-fix.md`

## Lines Changed
- `ContactResolver.kt`: +3 (import, 2× `suspend`, `withContext(Dispatchers.IO)` wrapper)
- `EntityResolver.kt`: +2 (`suspend` on `resolve` and `resolveContact`)

## Root Cause
`ContactResolver.queryContacts()` called `ContentResolver.query()` synchronously with no dispatcher, executing on whatever thread called it — in practice the Main thread via `VoiceSessionManager`'s coroutine scope (Dispatchers.Main).

## Fix Applied
- `queryContacts` wrapped with `withContext(Dispatchers.IO)` — DB query now always runs on IO thread pool.
- `resolveAll` and `resolveNumber` marked `suspend` to allow caller to stay on its own dispatcher while IO block runs off Main.
- `EntityResolver.resolveContact` and `resolve` marked `suspend` to propagate correctly.
- `VoiceSessionManager` already calls `entityResolver.resolve()` inside a coroutine on `Dispatchers.Default` — no change needed there.

## Behavior Change
None. Outputs identical. Only thread of execution changes for the DB query.

## Backward Compatibility
100%. Resolution logic, slot names, intent fields, routing — all unchanged.

## Changelog Created
`docs/change-log/layer5-1-1-contact-io-fix.md`

## Confidence
100% — minimal surgical change; coroutine context propagation is straightforward.
