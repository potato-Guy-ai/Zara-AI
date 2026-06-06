# Build Fix — Suspend Propagation for ContactResolver (Layer 5.1.1)

## Build Error
```
Suspend function 'resolveAll' should be called only from a coroutine or another suspend function.
Suspend function 'resolveNumber' should be called only from a coroutine or another suspend function.
```

## Root Cause
Layer 5.1.1 made `ContactResolver.resolveAll()` and `resolveNumber()` suspend functions.
`CallActions.call()`, `AppActions.sendSms()`, and `AppActions.sendWhatsApp()` call these methods
but were not marked suspend — compile-time error.

## Fix Applied
Added `suspend` to:
- `CallActions.call()`
- `AppActions.sendSms()`
- `AppActions.sendWhatsApp()`

`ActionExecutor.execute()` is already `suspend`, so all call sites compile correctly.

## Files Read
- `actions/ContactResolver.kt`
- `actions/CallActions.kt`
- `actions/AppActions.kt`
- `actions/ActionExecutor.kt`
- `voice/VoiceSessionManager.kt`

## Files Modified
- `actions/CallActions.kt` — `call()` marked `suspend`
- `actions/AppActions.kt` — `sendSms()`, `sendWhatsApp()` marked `suspend`

## Files Created
- `docs/change-log/build-fix-suspend-propagation.md`

## Lines Changed
3 lines (one `suspend` keyword each)

## Pipeline Changes
None. Suspend propagation only — no logic, routing, or behavior changed.

## Backward Compatibility
100%. `ActionExecutor.execute()` was already `suspend`; callers unaffected.

## Confidence
100%
