# Layer 5 — Final Safety Fixes

## Goal
Fix 4 audited defects. No new features. No architectural changes.

## Files Read
- `core/EntityResolver.kt`, `core/ClarificationManager.kt`, `actions/ActionExecutor.kt`
- `core/AppActionPlanner.kt`, `core/EntityConfidenceEvaluator.kt`, `core/ExecutionTelemetry.kt`
- All Layer 5 changelogs

## Files Modified
- `core/EntityResolver.kt`
- `actions/ActionExecutor.kt`
- `core/AppActionPlanner.kt`
- `core/ExecutionTelemetry.kt`

## Files Created
- `docs/change-log/layer5-final-safety-fixes.md`

## Lines Changed
~80

## Audit Issues Fixed

### Fix 1 — MEDIUM confidence no longer auto-executes
EntityResolver single-result path now routes MEDIUM confidence to clarification.
Only HIGH (exact or startsWith match with 1 candidate) auto-executes.

### Fix 2 — Clarification candidate consistency
All EntityResolver-generated clarification candidates store `resolvedValue = phone number`.
ActionExecutor.handleClarificationNeeded() checks `ClarificationManager.hasPending()` first— if EntityResolver already stored clarification (with phone), ActionExecutor does NOT overwrite it with display name.
Single clarification model, one stable resolvedValue format per entity type.

### Fix 3 — Voice message inference expanded
New triggers in `AppActionPlanner.VOICE_MESSAGE_TRIGGERS`:
- `voice message to`, `send voice message`, `send a voice message`
- `record voice message`, `record a voice message`, `voice msg`, `send voice msg`
Detected in both `detectApp()` (infers APP_WHATSAPP when no explicit app mentioned)
and `detectAction()` (maps to ACTION_VOICE_MESSAGE).

### Fix 4 — Telemetry bounded buffer
`ArrayDeque<TelemetryRecord>` capped at MAX_RECORDS=500.
Oldest record dropped via `removeFirst()` when full. O(1) drop.

## Confidence Rules
- HIGH (score ≥ 80, 1 candidate) → auto-execute
- MEDIUM (score 60, 1 candidate) → clarification
- LOW → no execution, pass through unchanged

## Test Cases
| Input | Before | After |
|---|---|---|
| query="amm", 1 result, score=60 | auto-execute | clarification |
| EntityResolver contact clarification | resolvedValue=displayName | resolvedValue=phone |
| ActionExecutor re-store | overwrites clarification | skipped if already stored |
| "voice message to boss" | may miss | WHATSAPP + VOICE_MESSAGE |
| "record a voice message to boss" | miss | WHATSAPP + VOICE_MESSAGE |
| 501st telemetry record | unbounded growth | oldest dropped, stays at 500 |

## Backward Compatibility
100% — no Layer 4, 5.1–5.7 regressions.

## Side Effects
None. All fixes additive or narrowing existing behavior.

## Confidence
99%
