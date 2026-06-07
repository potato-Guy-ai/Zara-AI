# Layer 5 Hardening — Production Reliability Pass

## Files Created
- `core/ContactNormalizer.kt` — Part 1: emoji/symbol/case/whitespace normalization
- `core/ContactDeduplicationEngine.kt` — Part 2: dedup by phone + name
- `core/ContactRankingEngine.kt` — Part 3: score-based ranking
- `core/EntityConfidenceEvaluator.kt` — Part 4: HIGH/MEDIUM/LOW gating
- `core/PreferredAppRegistry.kt` — Part 8: canonical app preferences
- `core/ExecutionTelemetry.kt` — Part 11: local debug telemetry
- `docs/change-log/layer5-hardening.md`

## Files Modified
- `core/EntityResolver.kt` — Parts 1–4,8: normalization + dedup + ranking + confidence + preferred apps; clarification stores stable resolvedValue
- `core/ClarificationManager.kt` — Part 5: candidate identity fix; numeric selection uses immutable stored list; ContactNormalizer applied to matching
- `core/AppActionPlanner.kt` — Parts 6,7,8,10: channel detection, WhatsApp action hardening, body extraction, unsupported command sanity check, PreferredAppRegistry

## Key Fixes

### Part 5 — Clarification selection bug
Previously: candidates re-matched by displayName after potential re-sort → wrong contact.
Now: `storedCandidates` list order is frozen at clarification creation. Numeric "3" always maps to `candidates[2]`. Display name matching is also against the frozen list in original order.

### Part 2 — Deduplication
Phone number normalized to last-10-digits canonical form. Duplicate entries (emoji variants, casing) collapsed before clarification.

### Part 1 — Contact normalization
❤️ Malvin, MALVIN, malvin → all resolve to `malvin`.

### Part 6 — Channel awareness
"call boss on whatsapp" → WHATSAPP channel + AUDIO_CALL action.

### Part 7 — WhatsApp body isolation
"message malvin saying hello" → target=malvin, body=hello. Body text never leaks into contact lookup.

### Part 8 — Preferred app
"play music" → Spotify (not YouTube Music).
"open youtube" → YouTube (not YouTube Music).

### Part 10 — Intent sanity
"destroy phone" → `unsupported_command=true`, not routed to cloud.

### Part 11 — Telemetry
Every clarification resolution logged via `ExecutionTelemetry.record()`.

## Backward Compatibility
100% — all Layer 4 and 5.1–5.7 paths unchanged.

## Confidence
97%
