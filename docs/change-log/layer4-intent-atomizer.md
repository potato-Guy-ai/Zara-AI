# Layer 4 — Intent Atomization + Safety Gateway Engine

## Files Created
- `core/IntentGraph.kt` — IntentGraph model, SlotType enum, TypedSlot, AtomicIntent
- `core/IntentAtomizer.kt` — Engine implementation
- `docs/change-log/layer4-intent-atomizer.md`

## Files Modified
None. Atomizer is a standalone pre-pipeline gate; existing pipeline unchanged.

## Architecture
```
Raw Input → IntentAtomizer.atomize() → IntentGraph
                                          (VALID / NEEDS_CLARIFICATION / REJECTED)
```
IntentGraph is consumed by callers before entering SlotExtractor/EntityResolver pipeline.
Atomizer does NOT perform entity resolution, app resolution, or execution.

## Safety Rules Implemented
1. Null/empty/noise rejection
2. Unicode normalization (NFC), whitespace collapse
3. Max input length 500, min 2
4. Pure noise detection (< 2 alphanumeric chars)

## Slot Type System
- `CONTACT_SLOT`, `APP_SLOT`, `ACTION_SLOT`, `MESSAGE_SLOT`, `QUERY_SLOT`, `CALL_TARGET_SLOT`, `CONTENT_SLOT`
- Each token assigned exactly one SlotType
- TypedSlot enforces non-blank value and [0.0, 1.0] confidence at construction

## Validation Gate
| Intent Type | Required Slots |
|---|---|
| CALL | CONTACT_SLOT |
| MESSAGE | CONTACT_SLOT |
| OPEN_APP | APP_SLOT |
| SEARCH | QUERY_SLOT |

Missing required slot → REJECTED (single intent) or NEEDS_CLARIFICATION (compound)

## Confidence Scoring
- High ≥ 0.9: action keyword matched + required slot found
- Medium 0.65: partial match
- Low 0.3: UNKNOWN type
- Intent confidence = average slot confidence

## Performance
- O(n) string scan
- No regex engine
- No NLP models
- No Android calls
- Deterministic

## Confidence
99%
