package com.zara.assistant.service.automation

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.zara.assistant.utils.ZaraLogger
import java.util.*

class CalendarAutomation(private val context: Context) {

    fun openCalendar() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_CALENDAR)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getTodaysEvents(): List<String> {
        val events = mutableListOf<String>()
        val now = Calendar.getInstance()
        val startOfDay = now.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        val endOfDay = now.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
            "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
            arrayOf(startOfDay.toString(), endOfDay.toString()),
            "${CalendarContract.Events.DTSTART} ASC"
        )
        cursor?.use {
            while (it.moveToNext()) {
                val title = it.getString(0) ?: "Untitled"
                val time = java.text.SimpleDateFormat("h:mm a", Locale.US)
                    .format(Date(it.getLong(1)))
                events.add("$title at $time")
            }
        }
        return events
    }

    fun createEvent(title: String, startMs: Long, endMs: Long) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
