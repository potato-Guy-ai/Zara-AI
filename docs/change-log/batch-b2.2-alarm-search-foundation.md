# Batch B2.2 — Alarm Expansion + Search Foundation

## Goal
1. Full alarm setting with time (hour/minute)
2. Clock utility commands (show alarms, show timers, open clock)
3. Fix SEARCH_QUERY groupValues bug so search actually fires
4. YouTube search confirmed working end-to-end

## Files Modified
- `core/ZaraIntent.kt` — Added `SHOW_ALARMS`, `SHOW_TIMERS`, `OPEN_CLOCK`, `ALARM_HOUR`, `ALARM_MINUTE`
- `core/LocalIntentClassifier.kt` — Fixed `reSearch` groupValues[2]→[1] bug; added `reAlarmWithTime`, `reShowAlarms`, `reShowTimers`, `reOpenClock` rules
- `core/SlotExtractor.kt` — Added `extractAlarmTime()` for SET_ALARM; parses hour/minute/am/pm
- `actions/AppActions.kt` — Added `setAlarm(hour, minute)`, `showTimers()`
- `actions/ActionExecutor.kt` — Wired SET_ALARM (hour/minute), SHOW_ALARMS, SHOW_TIMERS, OPEN_CLOCK in executeRaw

## Lines Changed
~90

## New Intents
- `SHOW_ALARMS` — opens clock alarm screen
- `SHOW_TIMERS` — opens clock timers screen
- `OPEN_CLOCK` — opens clock app
- `ALARM_HOUR` / `ALARM_MINUTE` — IntentExtra slots for alarm time

## Search Flow
```
"search cats on youtube"
→ LocalIntentClassifier: SEARCH_QUERY target="cats on youtube"  (groupValues[1] — BUG FIXED)
→ SlotExtractor.extractSearch(): QUERY=cats, APP=youtube
→ ActionExecutor.executeRaw: appActions.search("cats", "youtube")
→ AppActions.search → searchYouTube("cats")
→ YouTube search results opened
```

## Alarm Flow
```
"set alarm for 7:30 am"
→ LocalIntentClassifier: reAlarmWithTime matches → SET_ALARM
→ SlotExtractor.extractAlarmTime(): ALARM_HOUR=7, ALARM_MINUTE=30
→ ActionExecutor: appActions.setAlarm(7, 30)
→ AlarmClock.ACTION_SET_ALARM with EXTRA_HOUR=7, EXTRA_MINUTES=30, EXTRA_SKIP_UI=true
→ "Alarm set for 7:30 AM."
```

## Test Cases
| Input | Expected |
|---|---|
| `set alarm for 6 am` | Alarm set for 6:00 AM |
| `set alarm for 7:30 am` | Alarm set for 7:30 AM |
| `set alarm at 5` | Alarm set for 5:00 (AM/PM as spoken) |
| `create alarm for 8 pm` | Alarm set for 8:00 PM |
| `show alarms` | Opens clock alarm screen |
| `open clock` | Opens clock app |
| `show timers` | Opens timers screen |
| `search cats on youtube` | YouTube search results for cats |
| `search football highlights on youtube` | YouTube search results |
| `search dogs on google` | Google search for dogs |
| `set timer for 5 minutes` | Timer set (unchanged) |

## Side Effects
None. SET_TIMER unchanged. Spotify unchanged. Contact/context systems untouched.

## Confidence
98%
