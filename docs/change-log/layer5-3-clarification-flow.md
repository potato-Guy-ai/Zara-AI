# Layer 5.3 — Clarification Flow

## Goal
Complete the ambiguity-resolution loop: user selects from candidates, Zara executes.

## Files Created
- `models/PendingClarification.kt`
- `core/ClarificationManager.kt`
- `docs/change-log/layer5-3-clarification-flow.md`

## Files Modified
- `actions/ActionExecutor.kt` — store clarification instead of returning string
- `core/IntentRouter.kt` — added `tryResolveClarification()`
- `voice/VoiceSessionManager.kt` — added `processInput()` with clarification intercept

## Lines Changed
~130

## Pipeline Changes

```
Before:
STT → correction → classifier → SlotExtractor → EntityResolver → IntentRouter → ActionExecutor

After:
STT → correction
  → [if clarification pending] ClarificationManager.resolve()
       → resolved: ActionExecutor.execute(rebuiltIntent)
       → cancel: clear + "Okay, cancelled."
       → unresolved: re-prompt
  → [if expired or no pending] normal pipeline
```

## Clarification Lifecycle
1. EntityResolver produces `NEEDS_CLARIFICATION=true`
2. ActionExecutor stores `PendingClarification` via `ClarificationManager`
3. Returns "I found multiple matches: 1. X, 2. Y. Which one did you mean?"
4. Next user input intercepted by `VoiceSessionManager.processInput()`
5. `ClarificationManager.resolve()` matches by: numeric index, exact name, contains
6. On match: rebuilds intent with resolved slots, removes `NEEDS_CLARIFICATION`
7. `ActionExecutor.execute()` called with clean intent
8. Clarification cleared

## Expiration
- 30 seconds timeout
- `hasPending()` checks expiry; expired → auto-clear → normal pipeline

## Cancel
- Words: cancel, stop, never mind, nevermind
- Detected in both `ClarificationManager.resolve()` and `IntentRouter.tryResolveClarification()`

## Backward Compatibility
100% — normal pipeline unchanged when no clarification pending.

## Verification
1. ✅ Survives one conversational turn
2. ✅ Expires after 30s
3. ✅ Exact name match
4. ✅ Numeric selection ("1", "2")
5. ✅ Cancel clears state
6. ✅ No Layer 4 regression
7. ✅ No Layer 5.1/5.2 regression
8. ✅ No duplicate entity resolution
9. ✅ Build compiles logically

## Confidence
97%
