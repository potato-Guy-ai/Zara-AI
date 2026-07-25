# Zara Task & Memory System — Source of Truth

Branch: `layer6-6-minilm-intent`
Last updated: Phase 1 implementation

---

## Non-negotiable design constraints

- **Zara owns scheduling.** No `AlarmClock` intents. No one-alarm-per-task. One `AlarmManager` alarm is active at any time — the next relevant event. After processing, the scheduler recalculates and arms the next one.
- **Widget voice-first.** "Add Task" in the widget calls `VoiceSessionManager.startManualListening()` directly. No second STT pipeline. Typing is a fallback only.
- **Task repo is the operational source of truth.** Obsidian sync is async, fire-and-forget, and must never block task creation, scheduling, or completion.
- **Recurring tasks.** The next recurrence is created only after the current instance is processed — never pre-created in bulk.
- **Exact-alarm resilience.** `SCHEDULE_EXACT_ALARM` is checked before use (same pattern as `hasBluetoothConnect` in `PermissionManager`). If unavailable, fall back to `setAndAllowWhileIdle`. Scheduler never crashes on missing permission.
- **BootReceiver is additive.** Existing `startForegroundService` call is preserved. `ReminderScheduler.scheduleNext` is added as an additional call, not a replacement.

---

## Time expression model

| Expression type | Example | Stored as |
|---|---|---|
| Exact | "at 6 PM" | `TaskSchedule.Exact(epochMs)` |
| Relative | "in 30 mins", "in 2 hrs" | Resolved at creation → `Exact` |
| Ambiguous | "at 6" | `Exact` after AM/PM resolution; if genuinely ambiguous, ask once |
| Flexible | "sometime tomorrow evening" | `Flexible(windowStart, windowEnd, label="tomorrow_evening")` |
| Unscheduled | no time given | `TaskSchedule.Unscheduled` |

- **Deadline** (`before 6`) → stored in `TaskModel.deadline: Long?`, separate from the reminder trigger. "Before 6" is never treated as a reminder at 6.
- **Split** ("remind me at 5 to do X before 6") → reminder trigger = 5 PM, deadline = 6 PM.
- **Deadline-only tasks** (no reminder time specified) → scheduler creates an auto-reminder at `deadline - 30 minutes` so the task is never invisible until it's already overdue.
- **Flexible trigger** → on scheduling, Zara picks a point within `[windowStart, windowEnd]`. The original window (`windowStart`, `windowEnd`, `label`) is preserved in the model for future user-configurable window preferences. Do not collapse flexible tasks to a single point before storage.
- **Recurring** → `RecurrenceRule(type, intervalMs)`. Next instance created only when current is processed.

---

## Phase plan

### Phase 1 — Data model + repository ✅
`tasks/TaskModel.kt`, `tasks/TaskRepository.kt`
Gson serialization (already in build.gradle). DataStore key: `"zara_tasks"`.
No existing files changed.

### Phase 2 — Reminder parser
`tasks/ReminderParser.kt`
Returns `ParsedReminderTime(schedule, deadline, recurrence, body, ambiguousAmPm)`.
Handles all time expression types above. Normalize unit variants (`min`, `mins`, `sec`, `hr`, `hrs`).

### Phase 3 — Intent wiring
Add `SET_REMINDER` to `IntentAction`. Add patterns to `LocalIntentClassifier`. Handle in `ActionExecutor` (parse → confirm if ambiguous → `TaskRepository.create` → `ReminderScheduler.scheduleNext`).

### Phase 4 — Scheduler + notifications
`tasks/ReminderScheduler.kt` (single `object`, one alarm at a time)
`tasks/ReminderReceiver.kt` (fires alarm, updates state, reschedules next)
`tasks/ZaraNotificationHelper.kt` (channel + notification, Done/Snooze actions)
`BootReceiver.kt` — additive: add `ReminderScheduler.scheduleNext(context)` after existing foreground service start.
Manifest: receiver + `SCHEDULE_EXACT_ALARM` + `POST_NOTIFICATIONS`.

### Phase 5 — Widget
`tasks/TaskReminderWidget.kt`, layout XML, widget info XML.
"Add Task" → `TaskQuickAddActivity` → `VoiceSessionManager.startManualListening()`.
Manifest: AppWidgetProvider + Activity.

### Phase 6 — Obsidian vault sync
`tasks/ObsidianVaultWriter.kt`.
Vault path from `MemoryManager.get("obsidian_vault_path")`. No-op if unset/unreachable.
Called from `TaskRepository` as fire-and-forget on `Dispatchers.IO`.
Archive: tasks older than 6 months → separate archive file, skip `important`-tagged or still-OVERDUE.

---

## Files that change per phase

| Phase | New files | Modified files |
|---|---|---|
| 1 | `tasks/TaskModel.kt`, `tasks/TaskRepository.kt` | none |
| 2 | `tasks/ReminderParser.kt` | none |
| 3 | — | `core/ZaraIntent.kt`, `core/LocalIntentClassifier.kt`, `actions/ActionExecutor.kt` |
| 4 | `tasks/ReminderScheduler.kt`, `tasks/ReminderReceiver.kt`, `tasks/ZaraNotificationHelper.kt` | `service/BootReceiver.kt`, `AndroidManifest.xml` |
| 5 | `tasks/TaskReminderWidget.kt`, `tasks/TaskQuickAddActivity.kt`, layout/xml | `AndroidManifest.xml` |
| 6 | `tasks/ObsidianVaultWriter.kt` | `tasks/TaskRepository.kt` (add vault write call) |

---

## Serialization

Gson 2.10.1 (already in `build.gradle.kts`). Task list stored as JSON array under DataStore key `"zara_tasks"`. `TaskSchedule` and `RecurrenceRule` use a `type` discriminator field for safe Gson deserialization without `RuntimeTypeAdapterFactory`.
