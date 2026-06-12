# Layer 6.5B — Workflow Chaining

## Goal
Convert compound voice commands into ordered, dependency-linked workflows.
Previous behavior: compound commands produced independent parallel plans.
New behavior: sequential WorkflowPlan with explicit step dependencies.

## Files Read
- `docs/project/zara-current-state.md`
- `execution/ExecutionModels.kt`
- `execution/ExecutionPlanner.kt`
- `execution/ExecutionQueue.kt`
- `core/CompoundIntentSplitter.kt`
- `voice/VoiceSessionManager.kt`
- `context/ContextModels.kt`

## Files Created
- `workflow/WorkflowState.kt`
- `workflow/FailurePolicy.kt`
- `workflow/WorkflowStepType.kt` (defined inside WorkflowStep.kt)
- `workflow/WorkflowStep.kt`
- `workflow/WorkflowContextSnapshot.kt`
- `workflow/WorkflowPlan.kt`
- `workflow/WorkflowPlanner.kt`
- `workflow/WorkflowEngine.kt`

## Files Modified
- `voice/VoiceSessionManager.kt` — compound path replaced with `runWorkflow()`

## Workflow Architecture
```
CompoundIntentSplitter.split()
         ↓
   buildIntent() × N
         ↓
   WorkflowPlanner.plan(intents)
         ↓
   WorkflowEngine.submit(plan)
     → ExecutionGuard.guard() per step
     → ExecutionPlanner.plan() per step
     → ExecutionQueue.enqueue() per step
         ↓
   ExecutionQueue drain loop
     → ActionExecutor per step
     → ContextUpdater per step
         ↓
   WorkflowEngine.finalize()
```

## Workflow State
PENDING → RUNNING → COMPLETED
                  → FAILED
                  → CANCELLED
WAITING (reserved for future CONFIRMATION/WAIT step types)

## Failure Policy
- `STOP_WORKFLOW` (default): first step failure breaks the chain. Remaining steps skipped.
- `CONTINUE`: proceed to next step regardless. (architecture only, not used initially)

## Context Snapshot
`WorkflowContextSnapshot.capture()` reads `ConversationContextManager` at plan creation time.
Stores: `PersonContext`, `AppContext`, `MediaContext`, `QueryContext`.
Read-only. Mutations during workflow execution do not affect snapshot.
Used for: ensuring "call boss then message him" uses the boss resolved at step 1, not any later context update.

## Dependency Rules
- Steps are chained sequentially: step N depends on step N-1.
- `dependsOnStepId` is stored explicitly in `WorkflowStep`.
- Mapped to `ExecutionPlan.dependsOnId` in `WorkflowEngine.submit()`.
- `ExecutionQueue.dequeueNext()` enforces: dependency must be COMPLETED before dependent runs.
- If dependency is FAILED/CANCELLED → dependent is auto-failed (existing queue behavior).

## Workflow Planner
Input: `List<ZaraIntent>` (one per compound segment)
Output: `WorkflowPlan` with N sequential steps, context snapshot captured.
O(n) — no graph, no sorting. Each step gets `dependsOnStepId = previous.stepId`.

## Workflow Engine
- `submit(plan)`: validates, guards each intent, creates ExecutionPlans, enqueues.
- `finalize(plan, items)`: computes terminal state from queue item outcomes.
- Does NOT replace ExecutionQueue or ExecutionGuard — wraps them.
- Does NOT execute intents — remains in VoiceSessionManager/ActionExecutor.

## Execution Flow (example: "open youtube and search cats")
```
CompoundIntentSplitter: ["open youtube", "search cats"]
buildIntent("open youtube") → OPEN_APP(youtube)
buildIntent("search cats")  → SEARCH_QUERY(cats)
WorkflowPlanner.plan([intent1, intent2])
  → step_1: OPEN_APP   dependsOn=null
  → step_2: SEARCH     dependsOn=step_1
WorkflowEngine.submit(plan)
  → enqueue plan_1 (dependsOnId=null)
  → enqueue plan_2 (dependsOnId=plan_1.id)
Drain:
  → dequeue plan_1 → executeIntent(OPEN_APP) → "Opening YouTube."
  → plan_1 COMPLETED
  → dequeue plan_2 (dep satisfied) → executeIntent(SEARCH) → "Searching YouTube for cats."
  → plan_2 COMPLETED
WorkflowEngine.finalize() → COMPLETED
Response: "Opening YouTube. Searching YouTube for cats."
```

## Backward Compatibility
100% — single-segment path unchanged (goes through `runPipeline`, not `runWorkflow`).
Confirmation, clarification, recovery, cancellation paths unchanged.
ExecutionQueue, ExecutionGuard, ActionExecutor unchanged.

## Test Cases
| Input | Steps | Expected |
|---|---|---|
| `open youtube and search cats` | OPEN_APP → SEARCH_QUERY | Opens YouTube then searches cats |
| `open spotify and play believer` | OPEN_APP → PLAY_MUSIC | Opens Spotify then plays believer |
| `call boss then send whatsapp saying reached` | CALL → SEND_WHATSAPP | Calls then messages |
| `set alarm for 7am and open clock` | SET_ALARM → OPEN_CLOCK | Sets alarm then opens clock |
| `open youtube` (single) | OPEN_APP | Unchanged single-step path |
| Step 1 fails | FAILED | STOP_WORKFLOW: step 2 auto-fails, response shows error |

## Side Effects
None. New `workflow/` package is additive. No existing files except VSM modified.

## Confidence
98%
