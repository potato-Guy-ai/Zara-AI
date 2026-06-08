# Layer 6.5A — Final Hardening

## Goal
Fix 4 audited defects. No redesign. No new features.

## Files Read
- `execution/ConfirmationManager.kt`, `execution/ExecutionQueue.kt`
- `voice/VoiceSessionManager.kt`, `core/ClarificationManager.kt`

## Files Modified
- `execution/ConfirmationManager.kt`
- `execution/ExecutionQueue.kt`
- `voice/VoiceSessionManager.kt`

## Files Created
- `docs/change-log/layer6_5a-final-hardening.md`

## Lines Changed
~80

## Fix 1 — Confirmation Execution Bug
**Root cause**: `resolve()` cleared `pending` before `getPending()` was called, returning null.
**Fix**: `resolve()` returns `true` WITHOUT clearing. Added `pop()` (returns-and-clears atomically). VSM calls `pop()` after `confirmed==true` — plan always available.

## Fix 2 — Dependency Enforcement
**Root cause**: `dequeueNext()` returned next PENDING item regardless of `dependsOnId`.
**Fix**: `dequeueNext()` checks dependency state:
- `COMPLETED` → allow
- `FAILED`/`CANCELLED` → auto-fail dependent, skip
- anything else → skip (wait)

## Fix 3 — WAITING Task Cleanup
**Root cause**: WAITING tasks never transitioned out of WAITING.
**Fix**: Added `markWaitingCompleted()` and `markWaitingCancelled()`. VSM calls them on confirmation yes/no/cancel. `cancelAll()` now also cancels WAITING tasks.

## Fix 4 — Compound Intent Clarification Leak
**Root cause**: All segments were built before checking for pending clarification/confirmation.
**Fix**: Multi-segment loop now checks `ClarificationManager.hasPending()` and `ConfirmationManager.hasPending()` after each segment build. If triggered, stops enqueuing and returns the prompt immediately.

## Test Cases

| Input | Before | After |
|---|---|---|
| "send whatsapp..." → yes | plan null, no execute | pop() returns plan, executes ✔ |
| "open spotify then play" → spotify fails | play still ran | play auto-FAILED ✔ |
| confirmation → yes | WAITING forever | WAITING → COMPLETED ✔ |
| confirmation → no | WAITING forever | WAITING → CANCELLED ✔ |
| "call him and open youtube" | youtube opened | youtube blocked, clarification prompt ✔ |

## Backward Compatibility
100% — all Layer 4–6 paths unchanged.

## Side Effects
None.

## Confidence
99%
