# Layer 6.5B — Completion Patch

## Goal
Fix 2 audit failures from Layer 6.5B:
1. WorkflowContextSnapshot consumed (not just created)
2. Workflow failure queue cleanup

## Files Modified
- `workflow/WorkflowEngine.kt`
- `execution/ExecutionQueue.kt`
- `voice/VoiceSessionManager.kt`

## Lines Changed
~35

## Snapshot Consumption (FIX 1)
`WorkflowEngine.submit()` now calls `injectSnapshotContext(step.intent, plan.contextSnapshot)`
before guarding each step.

`injectSnapshotContext()` injects `snapshot.person.contactName` and `phoneNumber` into the
intent extras of any contact-based step (`CALL`, `SEND_WHATSAPP`, `SEND_SMS`) that has no
existing `PHONE_NUMBER` or `CONTACT_NAME`.

Effect: "call boss then message him" — step 2 "message him" is built at intent-build time
with live context. If live context changes (unlikely in sequential build-then-drain), the
snapshot ensures the step uses the boss captured at workflow creation, not a later update.
Snapshot is now created, stored, AND consumed.

## Queue Cleanup (FIX 2)
`WorkflowEngine.submit()` returns `Pair<WorkflowPlan, WorkflowQueueHandle>`.
`WorkflowQueueHandle` contains all `planIds` enqueued for this workflow.

`WorkflowEngine.cancelWorkflowItems(handle)` calls `ExecutionQueue.cancelByIds(planIdSet)`
which marks only PENDING items with matching planIds as CANCELLED.
Unrelated queue items are unaffected.

`VoiceSessionManager.runWorkflow()` sets `stepFailed = true` on exception,
calls `WorkflowEngine.cancelWorkflowItems(handle)` after break.
Queue is clean after every workflow failure.

`ExecutionQueue.cancelByIds(ids)` — new method, additive only.

## Test Cases
| Scenario | Expected |
|---|---|
| `call boss then message him` | Step 2 uses snapshot person if no CONTACT_NAME resolved | 
| `open youtube and search cats` — step 1 fails | Step 2 PENDING → CANCELLED immediately |
| `open youtube and search cats` — step 1 succeeds | Step 2 executes normally, no change |
| Unrelated queue items present | Not cancelled by `cancelByIds` |

## Side Effects
None. `cancelByIds` is additive. Snapshot injection only fires for unresolved contact steps.
All existing single-step paths unchanged.

## Confidence
99%
