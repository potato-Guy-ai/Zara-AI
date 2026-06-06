# Layer 5.5 — Compound Intent Orchestration

## Files Created
- `core/CompoundIntentSplitter.kt`
- `docs/change-log/layer5-5-compound-intent.md`

## Files Modified
- `voice/VoiceSessionManager.kt` (already wired in prior commit)

## Split Delimiters
`" and "`, `" then "`, `" after that "`, `"&"`, `" & "`

## Pipeline
```
Raw Input → CompoundIntentSplitter → [per segment] runPipeline()
```

## Execution
- Sequential, stop on first failure
- Single segment = zero overhead (no list join)

## Constraints
- Stateless, deterministic, O(n)
- No splits inside quoted phrases
- No regex

## Confidence
100%
