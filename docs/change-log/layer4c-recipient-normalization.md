# Layer 4C — Recipient & Message Normalization

## Goal

Add recipient normalization, BODY preservation confirmation, and expanded channel support
to `SlotExtractor`. Additive only — no existing behavior changed.

---

## Files Read

- `docs/project/zara-architecture.md`
- `docs/project/zara-current-state.md`
- `docs/change-log/layer4a-slot-infrastructure.md`
- `docs/change-log/layer4b-core-slot-extraction.md`
- `docs/change-log/stt-correction-boundary-fix.md`
- `core/SlotExtractor.kt`
- `core/ZaraIntent.kt`

---

## Files Modified

| File | Change |
|---|---|
| `core/ZaraIntent.kt` | Added `RECIPIENT` to `IntentExtra`; added `TELEGRAM`, `SIGNAL`, `MESSENGER`, `DISCORD` to `ChannelType`; updated `CHANNEL` comment |
| `core/SlotExtractor.kt` | Added `extractRecipientAndChannel()` for `CALL`, `SEND_SMS`, `SEND_WHATSAPP` |

## Files Created

- `docs/change-log/layer4c-recipient-normalization.md`

---

## Lines Changed

- `ZaraIntent.kt`: +6 lines
- `SlotExtractor.kt`: +20 lines net

---

## Recipient Rules

- Applies to: `CALL`, `SEND_SMS`, `SEND_WHATSAPP`
- `extra[RECIPIENT]` is set to `intent.target` (unchanged)
- Only set if `RECIPIENT` not already present
- `target` is never modified

---

## Message Rules

- `BODY` is the canonical message slot — no change.
- `BODY` is already placed in `extra` by `LocalIntentClassifier`.
- Layer 4C does not touch, duplicate, or rename it.
- No `MESSAGE` or `TEXT` key introduced.

---

## Channel Rules

### Existing (unchanged)
- `sms` — `ChannelType.SMS`
- `whatsapp` — `ChannelType.WHATSAPP`

### New (Layer 4C)
- `telegram` — `ChannelType.TELEGRAM`
- `signal` — `ChannelType.SIGNAL`
- `messenger` — `ChannelType.MESSENGER`
- `discord` — `ChannelType.DISCORD`

Channel is detected from `rawText` only if `CHANNEL` not already set by the classifier.

---

## Backward Compatibility

- `target` — never modified
- `rawText` — never modified
- `BODY` — never touched
- `CHANNEL` — only written if not already present
- `OPEN_APP`, `CALL`, `SEND_SMS`, `SEND_WHATSAPP` routing — unchanged
- `ActionExecutor` — not modified

---

## Test Cases

| Input | RECIPIENT | CHANNEL | BODY |
|---|---|---|---|
| `call ahmed` | `ahmed` | — | — |
| `message dad saying hi` | `dad` | `sms` (from classifier) | `hi` |
| `send telegram to ahmed saying hello` | `ahmed` | `telegram` | `hello` |
| `message john on signal` | `john` | `signal` | — |
| `whatsapp sara` | `sara` | `whatsapp` (from classifier) | — |
| `message ahmed on discord` | `ahmed` | `discord` | — |

---

## Side Effects

None. Channel detection reads `rawText` only. No I/O, no threads, no caching.

---

## Confidence

99% — additive-only changes. Existing classifier and executor paths unaffected.
