package com.zara.assistant.tasks

import java.util.Calendar

/**
 * Phase 2 — ReminderParser.
 *
 * Parses free-text reminder utterances into a structured [ParsedReminderTime].
 * Pure function object — no context, no coroutines, no side effects.
 * All time computations are relative to [nowMs] (defaults to System.currentTimeMillis(),
 * injectable for testing).
 *
 * SUPPORTED EXPRESSIONS (all case-insensitive, leading/trailing whitespace stripped):
 *
 * Exact times:
 *   "at 6 PM", "at 18:00", "at 6:30 PM", "at 6:30", "at noon", "at midnight"
 *
 * Ambiguous hour (no AM/PM qualifier):
 *   "at 6" — resolved by context (see resolveAmPm). If genuinely ambiguous,
 *   [ParsedReminderTime.ambiguousAmPm] = true and the caller must prompt.
 *
 * Relative:
 *   "in 30 seconds / sec / secs"
 *   "in 6 minutes / minute / min / mins"
 *   "in 2 hours / hour / hr / hrs"
 *   Combinations: "in 1 hour and 30 minutes", "in 90 min"
 *
 * Flexible (named windows):
 *   "sometime tomorrow morning / afternoon / evening / night"
 *   "tomorrow morning / afternoon / evening / night" (without "sometime")
 *   "this morning / afternoon / evening / night"
 *   "this weekend"
 *   "tomorrow" (alone) → Flexible window covering all of next day
 *   Window boundaries are hardcoded defaults. The [TaskSchedule.Flexible.label]
 *   is preserved so user preferences can redefine boundaries later.
 *
 * Deadline constraint (separate from reminder trigger):
 *   "before 6", "before 6 PM", "before noon" → [ParsedReminderTime.deadline]
 *   "before X" never sets the reminder trigger; it only sets the deadline.
 *
 * Split reminder + deadline:
 *   "at 5 ... before 6" → reminder at 5, deadline at 6
 *   "remind me at 5 to do X before 6 PM" → parses both independently
 *
 * Recurring:
 *   "every day", "every morning", "every night", "every evening", "every afternoon"
 *   "every weekday", "every week"
 *   → [ParsedReminderTime.recurrence]
 *
 * Body extraction:
 *   Everything that is not a time/deadline/recurrence expression is considered
 *   the task body. The parser strips trigger phrases and returns the cleaned body.
 *
 * DESIGN NOTES:
 * - Flexible windows store [windowStart], [windowEnd], and [label] independently
 *   from [resolvedTriggerMs] — the window is never collapsed. See TaskModel notes.
 * - Deadline is stored in [ParsedReminderTime.deadlineMs], separate from [schedule].
 * - A "before X" with no other time expression produces Unscheduled + non-null deadline.
 *   The scheduler (Phase 4) will auto-create a reminder at deadline - DEADLINE_LEAD_MS.
 * - [ambiguousAmPm] = true only when a bare hour (1–6) could plausibly be AM or PM
 *   given the current time. The caller (Phase 3, ActionExecutor) must ask once.
 */
object ReminderParser {

    // ── Flexible window definitions (epoch-ms offsets from start of a day) ───
    // These defaults can be replaced by user preferences in a future phase.
    // Times are in local wall-clock hours; Calendar is used for DST safety.

    private val WINDOWS = mapOf(
        "morning"   to (5 to 9),     // 05:00–09:00
        "afternoon" to (12 to 17),   // 12:00–17:00
        "evening"   to (17 to 21),   // 17:00–21:00
        "night"     to (21 to 23)    // 21:00–23:00
    )

    // ── Regexes ───────────────────────────────────────────────────────────────

    // "at 6 PM", "at 18:00", "at 6:30", "at noon", "at midnight"
    private val reAt = Regex(
        """at\s+(?:(noon|midnight)|(\d{1,2})(?::(\d{2}))?\s*(am|pm)?)""",
        RegexOption.IGNORE_CASE
    )

    // "before 6", "before 6 PM", "before noon", "before midnight"
    private val reBefore = Regex(
        """before\s+(?:(noon|midnight)|(\d{1,2})(?::(\d{2}))?\s*(am|pm)?)""",
        RegexOption.IGNORE_CASE
    )

    // Relative: "in 2 hours", "in 30 min", "in 90 seconds"
    // Handles hrs/hr/hours/hour, mins/min/minutes/minute, secs/sec/seconds/second
    private val reRelativeUnit = Regex(
        """(\d+(?:\.\d+)?)\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)""",
        RegexOption.IGNORE_CASE
    )
    // The "in" anchor may appear anywhere in the utterance (e.g. after
    // "remind me"), so it is NOT anchored to the start of the string.
    // Requiring a digit right after "in" avoids false positives like
    // "in the evening" or "in my car".
    private val reRelativePrefix = Regex("""\bin\s+(?=\d)""", RegexOption.IGNORE_CASE)

    // Flexible: "sometime tomorrow evening", "this evening", "tomorrow", "this weekend"
    private val reFlexible = Regex(
        """(?:sometime\s+)?(?:(this|tomorrow)\s+)?(morning|afternoon|evening|night|weekend)""",
        RegexOption.IGNORE_CASE
    )
    private val reTomorrow = Regex("""(?:sometime\s+)?tomorrow(?!\s+(?:morning|afternoon|evening|night))""", RegexOption.IGNORE_CASE)

    // Recurring: "every day", "every morning", etc.
    private val reRecurring = Regex(
        """every\s+(day|morning|afternoon|evening|night|weekday|week)""",
        RegexOption.IGNORE_CASE
    )

    // Verb phrases to strip when extracting body
    private val reReminderVerb = Regex(
        """^(?:remind me\s+(?:to\s+)?|remind me\s+|remember to\s+|don.?t forget to\s+)""",
        RegexOption.IGNORE_CASE
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Parse [rawText] and return a [ParsedReminderTime].
     * [nowMs] defaults to current time; override in tests.
     */
    fun parse(rawText: String, nowMs: Long = System.currentTimeMillis()): ParsedReminderTime {
        val t = rawText.trim()
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }

        // ── 1. Recurring ─────────────────────────────────────────────────────
        val recurrence = extractRecurrence(t)

        // ── 2. Deadline ("before X") — extracted first so it does not
        //    interfere with the trigger time regex ─────────────────────────
        val (deadlineMs, textAfterDeadline) = extractDeadline(t, cal, nowMs)

        // ── 3. Relative ("in X mins/hrs/secs") ───────────────────────────────
        val relativeMs = extractRelative(textAfterDeadline)
        if (relativeMs != null) {
            val triggerMs = nowMs + relativeMs
            val body = cleanBody(stripRelative(textAfterDeadline))
            return ParsedReminderTime(
                schedule      = TaskSchedule.Exact(triggerMs),
                deadlineMs    = deadlineMs,
                recurrence    = recurrence,
                body          = body,
                ambiguousAmPm = false
            )
        }

        // ── 4. Exact or ambiguous ("at 6 PM", "at 6") ───────────────────────
        val atMatch = reAt.find(textAfterDeadline)
        if (atMatch != null) {
            val (triggerMs, ambiguous) = resolveAtTime(atMatch, cal, nowMs)
            val body = cleanBody(textAfterDeadline.replace(atMatch.value, "").trim())
            return ParsedReminderTime(
                schedule      = TaskSchedule.Exact(triggerMs),
                deadlineMs    = deadlineMs,
                recurrence    = recurrence,
                body          = body,
                ambiguousAmPm = ambiguous
            )
        }

        // ── 5. Flexible ("tomorrow evening", "this morning", "this weekend") ─
        val reTomMatch = reTomorrow.find(textAfterDeadline)
        if (reTomMatch != null) {
            val window = tomorrowWindow(cal)
            val body = cleanBody(textAfterDeadline.replace(reTomMatch.value, "").trim())
            return ParsedReminderTime(
                schedule      = window,
                deadlineMs    = deadlineMs,
                recurrence    = recurrence,
                body          = body,
                ambiguousAmPm = false
            )
        }

        val flexMatch = reFlexible.find(textAfterDeadline)
        if (flexMatch != null) {
            val dayword = flexMatch.groupValues[1].lowercase()  // "this" or "tomorrow" or ""
            val period  = flexMatch.groupValues[2].lowercase()  // "morning", "evening", etc.
            val window = resolveFlexibleWindow(dayword, period, cal)
            val body = cleanBody(textAfterDeadline.replace(flexMatch.value, "").trim())
            return ParsedReminderTime(
                schedule      = window,
                deadlineMs    = deadlineMs,
                recurrence    = recurrence,
                body          = body,
                ambiguousAmPm = false
            )
        }

        // ── 6. Deadline-only (no trigger found) ──────────────────────────────
        if (deadlineMs != null) {
            val body = cleanBody(textAfterDeadline)
            return ParsedReminderTime(
                schedule      = TaskSchedule.Unscheduled,
                deadlineMs    = deadlineMs,
                recurrence    = recurrence,
                body          = body,
                ambiguousAmPm = false
            )
        }

        // ── 7. No time found — unscheduled ───────────────────────────────────
        return ParsedReminderTime(
            schedule      = TaskSchedule.Unscheduled,
            deadlineMs    = null,
            recurrence    = recurrence,
            body          = cleanBody(t),
            ambiguousAmPm = false
        )
    }

    // ── Time resolution helpers ───────────────────────────────────────────────

    /**
     * Resolve an "at HH:MM am/pm" match to an epoch ms and an ambiguity flag.
     * Ambiguous = bare hour 1–6 with no am/pm where both interpretations are
     * in the future and plausible (within 12 hours of each other in the future).
     * If only one interpretation is in the future, pick it without asking.
     */
    private fun resolveAtTime(
        match: MatchResult,
        cal: Calendar,
        nowMs: Long
    ): Pair<Long, Boolean> {
        val keyword = match.groupValues[1].lowercase()
        if (keyword == "noon")     return resolveNamedTime(12, 0, cal, nowMs) to false
        if (keyword == "midnight") return resolveNamedTime(0,  0, cal, nowMs) to false

        val hour   = match.groupValues[2].toIntOrNull() ?: return nowMs to false
        val minute = match.groupValues[3].toIntOrNull() ?: 0
        val ampm   = match.groupValues[4].lowercase()

        return when {
            ampm == "am" -> resolveNamedTime(hour24 = if (hour == 12) 0 else hour, minute, cal, nowMs) to false
            ampm == "pm" -> resolveNamedTime(hour24 = if (hour == 12) 12 else hour + 12, minute, cal, nowMs) to false
            hour >= 13   -> resolveNamedTime(hour, minute, cal, nowMs) to false  // 24h unambiguous
            else         -> resolveAmPm(hour, minute, cal, nowMs)
        }
    }

    /**
     * Intelligent AM/PM resolution for bare hours (1–12, no qualifier).
     * Logic:
     *   - If current time is 12:00–17:59 → prefer PM (most natural: "remind me at 6" = 6 PM)
     *   - If current hour >= 18 → both AM and PM next-day; prefer AM (morning is more useful)
     *   - If current hour < 6  → prefer AM same-day or next morning
     *   - If current hour is 6–11 → prefer PM (next afternoon)
     *   - If only one of AM/PM is in the future today → pick it
     *   - If both or neither is useful → ambiguous = true
     */
    private fun resolveAmPm(hour: Int, minute: Int, cal: Calendar, nowMs: Long): Pair<Long, Boolean> {
        val amMs = resolveNamedTime(if (hour == 12) 0 else hour, minute, cal, nowMs)
        val pmMs = resolveNamedTime(if (hour == 12) 12 else hour + 12, minute, cal, nowMs)
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)

        // Both in the future — decide by current time band
        return when {
            amMs <= nowMs && pmMs > nowMs -> pmMs to false  // only PM is still future
            pmMs <= nowMs && amMs > nowMs -> amMs to false  // only AM is still future (next day)
            currentHour in 12..17         -> pmMs to false  // mid-afternoon → prefer PM
            currentHour >= 18             -> amMs to false  // evening → next morning
            currentHour < 6               -> amMs to false  // early morning → same AM
            else                          -> pmMs to true   // genuinely ambiguous, prefer PM but flag it
        }
    }

    /**
     * Returns the next occurrence of [hour24]:[minute] as epoch ms.
     * If that time has already passed today, advances to tomorrow.
     */
    private fun resolveNamedTime(hour24: Int, minute: Int, cal: Calendar, nowMs: Long): Long {
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= nowMs) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    // ── Relative extraction ───────────────────────────────────────────────────

    /** Returns total offset milliseconds if the text contains "in X unit[s]", else null. */
    private fun extractRelative(text: String): Long? {
        val anchor = reRelativePrefix.find(text) ?: return null
        var totalMs = 0L
        var found = false
        // Only units at or after the "in" anchor count as the relative offset.
        reRelativeUnit.findAll(text, anchor.range.first).forEach { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return@forEach
            val unit  = m.groupValues[2].lowercase()
            totalMs += when {
                unit.startsWith("hour") || unit.startsWith("hr") -> (value * 3600_000).toLong()
                unit.startsWith("min")                           -> (value * 60_000).toLong()
                unit.startsWith("sec")                           -> (value * 1_000).toLong()
                else -> 0L
            }
            found = true
        }
        return if (found) totalMs else null
    }

    private fun stripRelative(text: String): String =
        reRelativePrefix.replace(text, "").let { reRelativeUnit.replace(it, "") }
            .replace(Regex("""\band\b\s*""", RegexOption.IGNORE_CASE), "").trim()

    // ── Deadline extraction ───────────────────────────────────────────────────

    /**
     * Finds and removes "before HH[:MM] [am/pm]" from [text].
     * Returns (deadlineMs or null, text with the deadline phrase removed).
     */
    private fun extractDeadline(text: String, cal: Calendar, nowMs: Long): Pair<Long?, String> {
        val m = reBefore.find(text) ?: return null to text
        val keyword = m.groupValues[1].lowercase()
        val deadlineMs = when {
            keyword == "noon"     -> resolveNamedTime(12, 0, cal, nowMs)
            keyword == "midnight" -> resolveNamedTime(0, 0, cal, nowMs)
            else -> {
                val hour   = m.groupValues[2].toIntOrNull() ?: return null to text
                val minute = m.groupValues[3].toIntOrNull() ?: 0
                val ampm   = m.groupValues[4].lowercase()
                val h24 = when {
                    ampm == "am" -> if (hour == 12) 0 else hour
                    ampm == "pm" -> if (hour == 12) 12 else hour + 12
                    hour >= 13   -> hour
                    // Ambiguous bare deadline hour — prefer PM like resolveAmPm does
                    else         -> if (hour + 12 > cal.get(Calendar.HOUR_OF_DAY)) hour + 12 else hour
                }
                resolveNamedTime(h24, minute, cal, nowMs)
            }
        }
        val stripped = text.replace(m.value, "").replace(Regex("""\s{2,}"""), " ").trim()
        return deadlineMs to stripped
    }

    // ── Flexible window resolution ────────────────────────────────────────────

    /**
     * Builds a [TaskSchedule.Flexible] for named day+period combinations.
     * [dayword] is "this", "tomorrow", or "" (omitted).
     * [period] is "morning", "afternoon", "evening", "night", or "weekend".
     *
     * The window boundaries and [resolvedTriggerMs] are kept strictly separate.
     * [resolvedTriggerMs] is the centre of the window — not a hard-coded
     * "default time". This separation means a future setting can change the
     * window without touching the concept of "resolved trigger".
     */
    private fun resolveFlexibleWindow(dayword: String, period: String, cal: Calendar): TaskSchedule.Flexible {
        val base = Calendar.getInstance().apply { timeInMillis = cal.timeInMillis }
        when (dayword) {
            "tomorrow" -> base.add(Calendar.DAY_OF_YEAR, 1)
            "this", "" -> {
                // "this morning" in the afternoon → still today (window may be in the past;
                // Phase 4 scheduler will treat it as overdue and apply overdue policy)
            }
        }
        if (period == "weekend") return weekendWindow(base, dayword)

        val (startHour, endHour) = WINDOWS[period] ?: (8 to 20)  // safe default
        val label = buildLabel(dayword, period)

        val windowStart = dayBoundary(base, startHour)
        val windowEnd   = dayBoundary(base, endHour)
        val midpoint    = windowStart + (windowEnd - windowStart) / 2

        return TaskSchedule.Flexible(
            windowStart       = windowStart,
            windowEnd         = windowEnd,
            label             = label,
            resolvedTriggerMs = midpoint  // centre of window; separate from boundaries
        )
    }

    private fun tomorrowWindow(cal: Calendar): TaskSchedule.Flexible {
        val base = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val windowStart = dayBoundary(base, 0)   // start of tomorrow
        val windowEnd   = dayBoundary(base, 23)  // end of tomorrow
        return TaskSchedule.Flexible(
            windowStart       = windowStart,
            windowEnd         = windowEnd,
            label             = "tomorrow",
            resolvedTriggerMs = dayBoundary(base, 9) // default to 9 AM within the window
        )
    }

    private fun weekendWindow(base: Calendar, dayword: String): TaskSchedule.Flexible {
        val dow = base.get(Calendar.DAY_OF_WEEK)
        val daysToSat = ((Calendar.SATURDAY - dow + 7) % 7).let { if (it == 0 && dayword != "this") 7 else it }
        val sat = Calendar.getInstance().apply {
            timeInMillis = base.timeInMillis
            add(Calendar.DAY_OF_YEAR, daysToSat)
        }
        val sun = Calendar.getInstance().apply {
            timeInMillis = sat.timeInMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val windowStart = dayBoundary(sat, 8)
        val windowEnd   = dayBoundary(sun, 22)
        return TaskSchedule.Flexible(
            windowStart       = windowStart,
            windowEnd         = windowEnd,
            label             = "weekend",
            resolvedTriggerMs = dayBoundary(sat, 10) // Saturday morning
        )
    }

    /** Epoch ms for [hour]:00:00 on the day represented by [cal]. */
    private fun dayBoundary(cal: Calendar, hour: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun buildLabel(dayword: String, period: String): String {
        val parts = listOfNotNull(dayword.takeIf { it.isNotBlank() }, period)
        return parts.joinToString("_")
    }

    // ── Recurrence ────────────────────────────────────────────────────────────

    private fun extractRecurrence(text: String): RecurrenceRule? {
        val m = reRecurring.find(text) ?: return null
        return when (m.groupValues[1].lowercase()) {
            "day"             -> RecurrenceRule(RecurrenceType.DAILY)
            "weekday"         -> RecurrenceRule(RecurrenceType.WEEKDAYS)
            "week"            -> RecurrenceRule(RecurrenceType.WEEKLY)
            "morning", "evening", "afternoon", "night"
                              -> RecurrenceRule(RecurrenceType.DAILY)  // same period next day
            else              -> RecurrenceRule(RecurrenceType.DAILY)
        }
    }

    // ── Body cleaning ─────────────────────────────────────────────────────────

    /**
     * Strips reminder verb prefix and normalizes whitespace.
     * Does NOT strip the task description itself.
     */
    private fun cleanBody(text: String): String {
        var result = reReminderVerb.replace(text, "")
        // Strip dangling connector words that are left after time phrase removal
        result = result
            .replace(Regex("""^(to|that|about)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        return result
    }
}

// ── Result type ───────────────────────────────────────────────────────────────

/**
 * Output of [ReminderParser.parse].
 *
 * [schedule] — when to trigger the reminder. May be Unscheduled if only a
 *   deadline was found, or if no time expression was present at all.
 * [deadlineMs] — optional deadline epoch ms, separate from the trigger.
 *   "before 6" sets this; it never sets [schedule].
 * [recurrence] — optional recurrence rule. Applied by scheduler after each fire.
 * [body] — cleaned task description (reminder verb and time phrases stripped).
 * [ambiguousAmPm] — true when a bare hour (e.g. "at 6") could plausibly mean
 *   AM or PM and the caller must ask. Only set for [TaskSchedule.Exact] results.
 */
data class ParsedReminderTime(
    val schedule: TaskSchedule,
    val deadlineMs: Long?,
    val recurrence: RecurrenceRule?,
    val body: String,
    val ambiguousAmPm: Boolean
)
