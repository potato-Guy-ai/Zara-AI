# Layer 6.5E Final Cleanup

## Goal
Remove duplicate `ExecutionStarted` / `ExecutionCompleted` / `ExecutionFailed` events
emitted during workflow step execution. Establish `executeIntent()` as the single
canonical event source.

## Files Modified
- `app/src/main/java/com/zara/assistant/voice/VoiceSessionManager.kt`

## Lines Changed
Removed 3 lines from `runWorkflow()` step loop:
- `InteractionEventPublisher.publish(ZaraInteractionEvent.ExecutionStarted(plan.intent.action))` (was line 229)
- `InteractionEventPublisher.publish(ZaraInteractionEvent.ExecutionCompleted(result))` (was line 234)
- `InteractionEventPublisher.publish(ZaraInteractionEvent.ExecutionFailed(e.message ?: "unknown"))` (was line 247)

## Root Cause
`runWorkflow()` explicitly published `ExecutionStarted`, `ExecutionCompleted`, and
`ExecutionFailed` around each call to `executeIntent()`. `executeIntent()` also
publishes all three internally. Every workflow step caused double-emission.

## Fix Applied
Removed the three manual publish calls from the workflow loop. `executeIntent()` remains
the single source of truth for execution lifecycle events.

Added comment at both removal sites:
```kotlin
// ExecutionStarted/Completed published inside executeIntent() — single source of truth
```

## Event Flow Before (per workflow step)
```
WorkflowStepStarted
ExecutionStarted       ← duplicate (workflow loop)
  ExecutionStarted     ← from executeIntent()
  ExecutionCompleted   ← from executeIntent()
ExecutionCompleted     ← duplicate (workflow loop)
WorkflowStepCompleted
```

## Event Flow After (per workflow step)
```
WorkflowStepStarted
  ExecutionStarted     ← from executeIntent() only
  ExecutionCompleted   ← from executeIntent() only
WorkflowStepCompleted
```

## Backward Compatibility
No execution logic changed. No event model changed. Observers receive fewer
(correct) events — UI surfaces no longer see duplicate execution flashes per step.

## Test Cases
| Command | Expected |
|---|---|
| `open youtube` (single) | `ExecutionStarted` × 1, `ExecutionCompleted` × 1 |
| `open youtube and search cats` (workflow) | Per step: `ExecutionStarted` × 1, `ExecutionCompleted` × 1 |

## Confidence
100%
