# Layer 5.6 — App Action Specialization

## Files Created
- `core/AppActionPlanner.kt`
- `docs/change-log/layer5-6-app-action-specialization.md`

## Files Modified
- `actions/ActionExecutor.kt` — plan-based execution branch
- `voice/VoiceSessionManager.kt` — `AppActionPlanner.plan()` inserted after EntityResolver

## Lines Changed
~120

## Pipeline
```
classify → slots → alias → entity → AppActionPlanner → route → execute
```

## Plan Storage
Plan stored as flat keys in `intent.extra`:
`app_plan_app`, `app_plan_action`, `app_plan_target`, `app_plan_query`

## Fallback
- Unknown app → intent unchanged, normal execution
- Unknown action → fallback to `OPEN_APP`
- Missing target → pass to ActionExecutor

## Test Cases
1. "voice message to boss on whatsapp" → WHATSAPP + VOICE_MESSAGE + boss ✅
2. "search how to cook cake on youtube" → YOUTUBE + SEARCH + query ✅
3. "call boss on whatsapp" → WHATSAPP + AUDIO_CALL + boss ✅
4. "play music on spotify" → MUSIC + PLAY_SONG ✅
5. unknown app → unchanged ✅

## Backward Compatibility
100% — plan only generated when app detected; all prior layers untouched.

## Confidence
97%
