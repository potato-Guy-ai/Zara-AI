# Contact Execution Consistency Fix

## Goal
Enforce invariant: if `PHONE_NUMBER` exists in intent extras, never re-resolve contacts.
Execution must use `PHONE_NUMBER` directly for all contact-based actions.

## Root Cause
Four paths in `ActionExecutor` ignored `PHONE_NUMBER` after clarification:
1. `executeRaw SEND_WHATSAPP` — called `AppActions.sendWhatsApp(contact)` → `resolveNumber()` re-ran.
2. `executeRaw SEND_SMS` — called `AppActions.sendSms(contact)` → `resolveNumber()` re-ran. No fast-path existed.
3. `executeContract` phone branch — called `callActions.call(contract.target)` → `resolveAll()` re-ran.
4. `executePlan` phone branch — called `callActions.call(target)` → `resolveAll()` re-ran.

All four could regenerate an AMBIGUOUS sentinel after clarification had already resolved the contact.

## Fix
Added three private helper methods to `ActionExecutor`:
- `executeResolvedCall(intent)` — checks `PHONE_NUMBER`, calls `dialNumber()` directly.
- `executeResolvedWhatsApp(intent)` — checks `PHONE_NUMBER`, opens `wa.me/` URI directly.
- `executeResolvedSms(intent)` — checks `PHONE_NUMBER`, opens `smsto:` URI directly.

All return `null` when `PHONE_NUMBER` is absent → caller falls back to existing name-based resolution.

Applied to:
- `executeRaw SEND_WHATSAPP`: `executeResolvedWhatsApp(intent) ?: appActions.sendWhatsApp(...)`
- `executeRaw SEND_SMS`: `executeResolvedSms(intent) ?: appActions.sendSms(...)`
- `executeContract` phone: `executeResolvedCall(intent) ?: callActions.call(...)`
- `executePlan` phone: `executeResolvedCall(intent) ?: callActions.call(...)`

`CALL executeRaw` unchanged — already correct.

## Files Modified
- `actions/ActionExecutor.kt`

## Lines Changed
~40

## Execution Invariant
```
PHONE_NUMBER present → direct execution (no ContactResolver)
PHONE_NUMBER absent  → existing name-based resolution (unchanged)
```

## Fixed Paths
| Action | Path | Before | After |
|---|---|---|---|
| SEND_WHATSAPP | executeRaw | resolveNumber() always | PHONE_NUMBER fast-path |
| SEND_SMS | executeRaw | resolveNumber() always | PHONE_NUMBER fast-path |
| CALL | executeContract phone | callActions.call() | PHONE_NUMBER fast-path |
| CALL | executePlan phone | callActions.call() | PHONE_NUMBER fast-path |

## Test Cases
| Case | Expected |
|---|---|
| WhatsApp atha → choose atha 2 | Direct WhatsApp, no AMBIGUOUS |
| WhatsApp him → yes | Direct WhatsApp, no lookup |
| SMS atha → choose atha 2 | Direct SMS, no resolveNumber() |
| call atha → choose atha 2 | Direct call, no re-resolution |
| call him | Direct call, no resolveAll() |

## Backward Compatibility
100% — fast-paths return `null` when `PHONE_NUMBER` absent, falling back to existing behavior.
No changes to EntityResolver, ClarificationManager, or contact resolution logic.

## Confidence
99%
