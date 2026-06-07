# Layer 6 Critical Architecture Fixes

## Goal
Remove duplicate clarification system (`pendingContextText`).
Route all MEDIUM context confirmations through `ClarificationManager` (sole authority).

## Files Read
- `context/ContextResolver.kt`, `context/ConversationContextManager.kt`
- `voice/VoiceSessionManager.kt`
- `core/ClarificationManager.kt`, `models/PendingClarification.kt`
- `core/IntentRouter.kt`

## Files Modified
- `context/ContextResolver.kt`
- `core/ClarificationManager.kt`
- `models/PendingClarification.kt`
- `voice/VoiceSessionManager.kt`

## Files Created
- `docs/change-log/layer6-critical-architecture-fixes.md`

## Lines Changed
~120

## Architecture Violations Removed

1. `pendingContextText` — REMOVED
2. `YES_WORDS` / `NO_WORDS` in ContextResolver — REMOVED
3. `clearPending()` in ContextResolver — REMOVED
4. Second confirmation state machine — ELIMINATED

## Clarification Flow

```
MEDIUM context detected:
  ContextResolver.storeMediumClarification()
    → ClarificationManager.store(PendingClarification(entityType=CONTEXT, ...))
    → resolvedValue = resolved text to re-classify

Next user turn:
  ClarificationManager.hasPending() == true
  ClarificationManager.resolve(userText)
    CONTEXT + yes-word → confirmedContextText = resolvedText, returns null
    CONTEXT + cancel   → clears, returns null
    CONTEXT + other    → abandons, returns null
  VoiceSessionManager.popConfirmedContextText()
    → non-null: runPipelineOnText(resolvedText)
    → null: normal path
```

## Timeout Behavior
Inherited from `PendingClarification.TIMEOUT_MS = 30_000L`.
No second timeout system.

## Clarification Engine Count
1 (ClarificationManager only)

## Timeout System Count
1 (PendingClarification.TIMEOUT_MS only)

## Test Cases

| Sequence | Expected |
|---|---|
| MEDIUM ctx "call him" → "yes" | ClarificationManager confirms → runPipelineOnText("call Malvin") |
| MEDIUM ctx "call him" → "no" | cancelled → normal pipeline |
| MEDIUM ctx → 30s timeout | hasPending() false → normal pipeline |
| HIGH ctx "call him" | direct ResolvedText → classify once, no ClarificationManager |
| Layer 5 contact clarification | CONTACT type unchanged → no regression |

## Backward Compatibility
100% — CONTACT and APP clarification paths unchanged.
All Layer 4–5.7 behavior preserved.

## Confidence
99%
