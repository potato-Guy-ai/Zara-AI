# Layer 6 Critical Fixes

## Goal
Fix two audited defects in Layer 6.0. No new features. No architectural changes.

## Files Read
- `context/ContextResolver.kt`, `context/ConversationContextManager.kt`
- `context/ContextUpdater.kt`, `voice/VoiceSessionManager.kt`
- `core/ClarificationManager.kt`, `models/PendingClarification.kt`

## Files Modified
- `context/ContextResolver.kt`
- `voice/VoiceSessionManager.kt`

## Files Created
- `docs/change-log/layer6-critical-fixes.md`

## Lines Changed
~120

## Fix 1 — Pipeline Order

Before:
```
classifier.classify(text) → ContextResolver.resolve(text, classifiedIntent)
```

After:
```
ContextResolver.resolve(text) → TextResult
  ResolvedText → classifier.classify(resolvedText) → pipeline
  Prompt       → return to user (no classify)
  NoContext    → classifier.classify(original) → pipeline
```

`ContextResolver` signature changed from `(String, ZaraIntent) → ContextResult`
to `(String) → TextResult`.

Pronoun replaced in the text string directly. Single classification pass. No recursion. No duplicate intent.

## Fix 2 — MEDIUM Confirmation State

MEDIUM confidence now stores `pendingContextText` (resolved string) inside `ContextResolver`.

Next user turn:
- `yes/yeah/ok/correct` → returns `ResolvedText(pendingContextText)` → pipeline runs
- `no/cancel/stop` → clears pending, returns `NoContext` → normal pipeline
- other input → clears pending, continues as new command

No new clarification system. Reuses existing session turn mechanism.
Does NOT touch `ClarificationManager` (kept for Layer 5 entity clarification only).

## Test Cases

| Sequence | Expected |
|---|---|
| "call malvin" → "call him" | "call him" → resolved to "call Malvin" → classify once → CALL |
| MEDIUM ctx: "call him" → "yes" | store pending → user says yes → run "call Malvin" |
| MEDIUM ctx: "call him" → "no" | clear pending, treat next turn normally |
| No context: "call him" | prompt: "I no longer know who..." |
| Unsafe: "delete him" | no context applied, pass through unchanged |

## Backward Compatibility
100% — Layer 5 clarification (ClarificationManager) untouched.
All slots, entity resolution, execution contracts unchanged.

## Side Effects
`ContextResolver.pendingContextText` is session-only, in-memory. Cleared on yes/no/other.

## Confidence
99%
