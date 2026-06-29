# Layer 6.5F — Priority Fix: Media Control vs Recovery Continuation

## Goal
Prevent Recovery continuation from consuming media-control phrases when a recoverable
failure is pending.

## Root Cause
`ContinuationResolver.resolve()` checked `RecoveryManager.hasRecoverable()` before
inspecting whether the text was a media-specific phrase. "resume music", "pause music",
"next song", "previous song", "stop music" were all classified as RESUME/PAUSE/NEXT
continuation signals and consumed by Recovery, never reaching the NLP classifier.

## Fix
One guard added in `ContinuationResolver.resolve()`, inside the Recovery block:
```kotlin
if (signal in RECOVERY_SIGNALS && MediaControlAction.fromText(text) != null) {
    return null  // pass to NLP → MEDIA_CONTROL
}
```
`RECOVERY_SIGNALS = {RETRY, RESUME, CONTINUE}` — the signals Recovery handles.
If the classified signal is one of these AND `MediaControlAction.fromText()` confirms
the text is a media phrase, return null immediately — NLP handles it as MEDIA_CONTROL.

Bare "resume", "retry", "continue", "try again" do NOT match `MediaControlAction.fromText()`
(not in its exact set) so they still go to Recovery correctly.

## Behavior After Fix
| Input | Recovery active | Result |
|---|---|---|
| `resume` | yes | Recovery |
| `retry` | yes | Recovery |
| `resume music` | yes | MEDIA_CONTROL |
| `pause music` | yes | MEDIA_CONTROL |
| `next song` | yes | MEDIA_CONTROL |
| `previous song` | yes | MEDIA_CONTROL |
| `stop music` | yes | MEDIA_CONTROL |
| `stop` | yes | Cancellation block (unchanged) |
| `resume` | no | "Nothing to retry or resume." |

## Files Modified
- `continuation/ContinuationResolver.kt`

## Lines Changed
~8

## Changelog Created
`docs/change-log/layer6_5f-priority-fix.md`

## Confidence
99%
