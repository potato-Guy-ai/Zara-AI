# Batch B2.2 — Alarm Expansion + Search Foundation

## Goal
- Alarm time support (set alarm for 6 am, 7:30 am, 8 pm)
- Clock utility commands (show alarms, show timers, open clock)
- Search intent fix (search cats on youtube was silently broken)
- YouTube search execution

## Files Modified
- `core/ZaraIntent.kt`
- `core/LocalIntentClassifier.kt`
- `core/SlotExtractor.kt`
- `actions/AppActions.kt`
- `actions/ActionExecutor.kt`

## Files Created
- `docs/change-log/batch-b2.1-youtube-open-timer-fix.md`
- `docs/change-log/batch-b2.2-alarm-search-foundation.md`

## Lines Changed
~90

## New Intents
- `SHOW_ALARMS` — show alarm list
- `SHOW_TIMERS` — show timer screen
- `OPEN_CLOCK`  — open clock app
- `ALARM_HOUR` / `ALARM_MINUTE` extra slots added to `IntentExtra`

(SEARCH_QUERY already existed from Batch B — fixed broken group index)

## Search Flow
```
"search cats on youtube"
  → LocalIntentClassifier: SEARCH_QUERY target="cats on youtube"  [FIXED: groupValues[1]]
  → SlotExtractor.extractSearch: QUERY=cats, APP=youtube
  → ActionExecutor.executeRaw: appActions.search("cats", "youtube")
  → AppActions.search: searchYouTube("cats")
  → YouTube app or browser opens search results
```

**Bug fixed:** `reSearch` in `LocalIntentClassifier` was reading `groupValues[2]` but the regex only has one capture group. Result was always empty string → SEARCH_QUERY never classified correctly. Changed to `groupValues[1]`.

## Alarm Flow
```
"set alarm for 7:30 am"
  → LocalIntentClassifier: SET_ALARM
  → SlotExtractor.extractAlarmTime: ALARM_HOUR=7, ALARM_MINUTE=30
  → ActionExecutor: appActions.setAlarm(7, 30)
  → AlarmClock.ACTION_SET_ALARM with EXTRA_HOUR=7, EXTRA_MINUTES=30
  → "Alarm set for 7:30 AM."

"set alarm for 8 pm"
  → ALARM_HOUR=20, ALARM_MINUTE=0 → "Alarm set for 8:00 PM."

"set alarm at 5" (no am/pm)
  → ALARM_HOUR=5, ALARM_MINUTE=0 → "Alarm set for 5:00 AM."
```

## Test Cases
| Input | Expected |
|---|---|
| `search cats on youtube` | YouTube search opens ✓ |
| `search football highlights on youtube` | YouTube search opens ✓ |
| `search dogs on google` | Google search opens ✓ |
| `set alarm for 6 am` | Alarm set for 6:00 AM ✓ |
| `set alarm for 7:30 am` | Alarm set for 7:30 AM ✓ |
| `create alarm for 8 pm` | Alarm set for 8:00 PM ✓ |
| `set alarm at 5` | Alarm set for 5:00 AM ✓ |
| `show alarms` | Alarm list opens ✓ |
| `show timers` | Timer screen opens ✓ |
| `open clock` | Clock app opens ✓ |
| `set timer for 5 minutes` | Timer unchanged ✓ |

## Side Effects
None. All existing intents unaffected. Alarm time slots are new keys.

## Confidence
97%
