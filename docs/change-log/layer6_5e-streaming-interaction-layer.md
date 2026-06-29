# Layer 6.5E — Streaming Interaction Layer

## Goal
Expose Zara's internal execution state as lightweight UI events to improve perceived responsiveness.
No intelligence changes. No execution changes. Visibility only.

## Files Read
- `VoiceSessionManager.kt`
- `WorkflowEngine.kt`
- `ExecutionQueue.kt`
- `ConfirmationManager.kt`
- `RecoveryManager.kt`
- `ClarificationManager.kt`
- `TaskRegistry.kt`

## Files Created
- `app/src/main/java/com/zara/assistant/streaming/PipelineState.kt`
- `app/src/main/java/com/zara/assistant/streaming/ZaraInteractionEvent.kt`
- `app/src/main/java/com/zara/assistant/streaming/InteractionEventPublisher.kt`
- `app/src/main/java/com/zara/assistant/streaming/StablePartialRenderer.kt`

## Files Modified
- `app/src/main/java/com/zara/assistant/voice/VoiceSessionManager.kt`

## Lines Changed
- VoiceSessionManager: +4 imports, +1 `PipelineStateMachine.transition()` per key state,
  +`WorkflowStarted/StepStarted/StepCompleted/WorkflowCompleted` events in `runWorkflow()`,
  +`ExecutionStarted/Completed/Failed` in `executeIntent()`,
  +partial STT wiring via `StablePartialRenderer`.

## Pipeline State Machine
`PipelineState` enum with 11 states. `PipelineStateMachine` singleton holds one `@Volatile`
active state. Each transition fires a `PipelineStateChanged` event via `InteractionEventPublisher`.
No timers. No background work.

## Stable Partial Renderer
`StablePartialRenderer` suppresses noisy partial STT updates. Publishes `PartialStt` only when:
- New text is 4+ characters longer than last published, OR
- Last word has changed.
Stateless between sessions. O(1) per call.

## Interaction Events
Sealed class `ZaraInteractionEvent` with 20 event types covering STT, resolution,
clarification, confirmation, recovery, workflow steps, and execution lifecycle.

## Event Publisher
`InteractionEventPublisher` — in-memory `ArrayDeque` ring buffer capped at 50 events.
Observers registered/unregistered by UI. Published inline — no threads, no coroutines,
no frameworks.

## Workflow Visibility
`runWorkflow()` publishes: `WorkflowStarted(stepCount)` → per step: `WorkflowStepStarted`,
`ExecutionStarted`, `ExecutionCompleted`/`ExecutionFailed`, `WorkflowStepCompleted` →
`WorkflowCompleted(results)`.

## Clarification Visibility
`ClarificationRequired(candidates)` published when `needs_clarification == true` in
`buildIntent()` and at workflow clarification intercept point.

## Confirmation Visibility
`ConfirmationRequired(prompt)` published when `ConfirmationManager` becomes active,
both in workflow loop and single-command paths.

## Recovery Visibility
`RecoveryRequired` published when `RecoveryManager.recordFailure()` is called inside
workflow step failure handler.

## Performance
- No polling, no timers, no handlers, no background threads.
- All publishing is inline, synchronous.
- Ring buffer caps memory at 50 events.
- `StablePartialRenderer` is O(1) per partial update.

## Backward Compatibility
All execution logic unchanged. Events are fire-and-forget with no return values.
Layers 4/5/6/6.5A/6.5B/6.5C unaffected.

## Test Cases
| Command | Expected Events |
|---|---|
| `call atha` | `ListeningStarted` → `FinalStt` → `RESOLVING_CONTACT` → `ContactResolutionCompleted` → `ExecutionStarted` → `ExecutionCompleted` |
| `send hi to malvin on whatsapp` | `WAITING_CONFIRMATION` → `ConfirmationRequired` → (user says yes) → `ExecutionStarted` → `ExecutionCompleted` |
| `open youtube and search cats` | `WorkflowStarted(2)` → `WorkflowStepStarted(0)` → `ExecutionCompleted` → `WorkflowStepStarted(1)` → `ExecutionCompleted` → `WorkflowCompleted` |
| `call am` (ambiguous) | `ContactResolutionStarted` → `ClarificationRequired([...])` → `WAITING_CLARIFICATION` |

## Side Effects
None. `InteractionEventPublisher.publish()` is a no-op if no observers are registered.

## Confidence
100%
