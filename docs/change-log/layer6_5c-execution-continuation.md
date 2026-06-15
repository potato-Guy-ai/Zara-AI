# Layer 6.5C — Execution Continuation

## Goal
Conversational continuation handling. Continuation signals (yes/no/retry/resume/continue)
are intercepted before the NLP pipeline when a continuation-eligible system is active.

## Files Read
- `execution/ConfirmationManager.kt`
- `execution/RecoveryManager.kt`
- `workflow/WorkflowEngine.kt`
- `execution/TaskRegistry.kt`
- `voice/VoiceSessionManager.kt`
- `core/ZaraIntent.kt`
- `actions/ActionExecutor.kt`

## Files Created
- `continuation/ContinuationType.kt`
- `continuation/ContinuationContext.kt`
- `continuation/ContinuationResolver.kt`

## Files Modified
- `voice/VoiceSessionManager.kt`

## Continuation Types
CONFIRM, REJECT, RETRY, RESUME, CONTINUE, NEXT, PREVIOUS, PAUSE, CANCEL

## Resolution Order
1. ConfirmationManager (CONFIRM/REJECT)
2. RecoveryManager (RETRY/RESUME/CONTINUE)
3. WorkflowEngine/ExecutionQueue (CONTINUE/NEXT)
4. TaskRegistry (PAUSE/PREVIOUS — architecture only, no execution)
5. Safety message or null → NLP pipeline

## Confirmation Flow
- `yes/ok/sure/proceed/do it` → CONFIRM → `ConfirmationManager.pop()` → `executeIntent()`
- `no/cancel/never mind` → REJECT → cancel waiting queue item, clear confirmation
- `ContinuationContext.CONFIRMATION` activated when confirmation prompt issued

## Recovery Flow
- `retry/try again` → RETRY → `RecoveryManager.popForRetry()` → `executeIntent()`
- `resume/continue` → RESUME → same path
- `ContinuationContext.RECOVERY` activated when step failure recorded

## Workflow Continuation
- `continue/next/proceed` → `ExecutionQueue.dequeueNext()` → `executeIntent()`
- `ContinuationContext.WORKFLOW` activated on workflow start, deactivated when queue empty
- Architecture-only for PAUSE/PREVIOUS

## Expiration Rules
- CONFIRMATION: 2 minutes (lazy, checked on access)
- RECOVERY: 5 minutes
- WORKFLOW: 5 minutes
- No timers. No polling. Lazy expiration only.

## Safety Rules
- `yes` with no pending confirmation → "Nothing is waiting for confirmation."
- `retry/resume` with no recoverable failure → "Nothing to retry or resume."
- `continue/next` with no active workflow → null (falls to NLP)
- `cancel/no` with no active system → null (falls to NLP / cancellation block)

## Backward Compatibility
100% — `ContinuationResolver.resolve()` returns null when no continuation is active;
`?.let { return it }` passes through to existing pipeline unchanged.
Confirmation/Recovery/Clarification existing code paths remain; Continuation intercepts first.
VSM cancellation block now also clears `ContinuationContext.clearAll()`.

## Test Cases
| Input | State | Expected |
|---|---|---|
| `yes` | Confirmation pending | Executes confirmed action |
| `no` | Confirmation pending | Cancels, "Okay, cancelled." |
| `yes` | Nothing pending | "Nothing is waiting for confirmation." |
| `retry` | Recovery present | Re-executes failed intent |
| `retry` | No failure | "Nothing to retry or resume." |
| `continue` | Workflow PENDING items | Executes next step |
| `continue` | No workflow | null → NLP classifies normally |
| `cancel` | Anything | Clears all, "Okay, cancelled." |
| `open youtube` | No continuation | null → normal NLP |

## Side Effects
None. New `continuation/` package is additive. All existing signals still work.
VSM removed old inline confirmation/recovery blocks (now handled by ContinuationResolver).

## Confidence
98%
