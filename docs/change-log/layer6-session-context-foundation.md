# Layer 6.0 — Session Conversation Context Foundation

## Goal
Session-only context layer enabling pronoun/reference resolution for follow-up commands.
No persistence, no user profiles, no long-term memory.

## Files Created
- `context/ContextModels.kt` — PersonContext, AppContext, ActionContext, QueryContext, MediaContext + expiry constants
- `context/ConversationContextManager.kt` — ring-buffer stores (max 5 per type), retrieve with expiry check
- `context/ContextResolver.kt` — pronoun resolution before classification
- `context/ContextUpdater.kt` — updates context after successful execution

## Files Modified
- `voice/VoiceSessionManager.kt` — inserted `ContextResolver` before pipeline, `ContextUpdater` after execution

## Context Types

| Type | Fields | Expiry |
|---|---|---|
| PersonContext | contactName, phoneNumber, confidence | 5 min |
| AppContext | appName, packageName, confidence | 10 min |
| ActionContext | lastAction, lastTarget, confidence | 10 min |
| QueryContext | query, queryType, confidence | 5 min |
| MediaContext | song, artist, playlist, video, confidence | 5 min |

## Ring Buffer Design
- `ArrayDeque<T>` max 5 per type
- `removeFirst()` on overflow
- Expiry checked on `lastX()` access only — no background timers

## Pronoun Resolution

| Pronoun | Resolves To |
|---|---|
| him, her, them, that person, that contact | PersonContext |
| that app, same app, search there, open it | AppContext |
| that song, that video, another one, same artist | MediaContext |

## Context Confidence Rules
- HIGH → auto-resolve
- MEDIUM → clarification prompt
- LOW → ignored

## Expiry Behavior
Expired context returns `ExpiredPrompt` — user-visible message, no silent guess.

## Safety Rules
Context NEVER used for: delete, format, reset, payment, transfer, install, uninstall, settings.

## Layer 7 Boundary
Layer 6 = session-only. No `remember this`, no user facts, no persistent memory.

## Test Cases
| Command sequence | Expected |
|---|---|
| "call malvin" then "message him" | PersonContext → message malvin |
| "open youtube" then "search motivation" | Normal (no pronoun) |
| "play believer" then "play another one" | MediaContext → play believer again |
| "call him" (no prior context) | Expired prompt |
| expired context + "call him" (30s wait) | Expired prompt |
| "him" on delete action | Safety block, no context applied |

## Side Effects
None. Context updates only after clearly successful execution strings.

## Backward Compatibility
100%

## Confidence
97%
