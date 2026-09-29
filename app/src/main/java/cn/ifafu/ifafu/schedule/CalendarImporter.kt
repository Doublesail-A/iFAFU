package cn.ifafu.ifafu.schedule

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import java.util.TimeZone

object CalendarImporter {
    /** Separate local calendars retain course colors in Google Calendar for Android. */
    fun import(context: Context, events: List<ScheduleEvent>, account: String): Int {
        require(events.isNotEmpty()) { "当前学期没有可导入的课程" }
        val resolver = context.contentResolver
        val accountName = "iFAFU-" + ScheduleEvents.key(account).take(12)
        val calendarUri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        // Idempotently replace only events owned by this app for the selected term.
        val scopes = events.map { it.scope }.distinct()
        val operations = arrayListOf<ContentProviderOperation>()
        scopes.forEach { scope ->
            operations += ContentProviderOperation.newDelete(CalendarContract.Events.CONTENT_URI)
                .withSelection(CalendarContract.Events.CUSTOM_APP_PACKAGE + "=? AND " +
                    CalendarContract.Events.CUSTOM_APP_URI + "=?", arrayOf(context.packageName, "ifafu://term/" + scope)).build()
        }
        events.groupBy { it.subject }.forEach { (subject, lessons) ->
            val internalName = "ifafu-" + ScheduleEvents.key(subject)
            val id = resolver.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID),
                Calendars.ACCOUNT_NAME + "=? AND " + Calendars.ACCOUNT_TYPE + "=? AND " + Calendars.NAME + "=?",
                arrayOf(accountName, CalendarContract.ACCOUNT_TYPE_LOCAL, internalName), null)?.use {
                    if (it.moveToFirst()) it.getLong(0) else null
                }
            val values = ContentValues().apply {
                put(Calendars.ACCOUNT_NAME, accountName); put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(Calendars.NAME, internalName); put(Calendars.CALENDAR_DISPLAY_NAME, "iFAFU · " + lessons.first().title.replace(Regex("^\\[调课\\]"), ""))
                put(Calendars.CALENDAR_COLOR, lessons.first().color)
                put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                put(Calendars.OWNER_ACCOUNT, accountName); put(Calendars.VISIBLE, 1); put(Calendars.SYNC_EVENTS, 1)
                put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            }
            val calendarId = if (id != null) {
                resolver.update(ContentUris.withAppendedId(calendarUri, id), values, null, null); id
            } else ContentUris.parseId(requireNotNull(resolver.insert(calendarUri, values)))
            lessons.forEach { event ->
                operations += ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                    .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                    .withValue(CalendarContract.Events.TITLE, event.title)
                    .withValue(CalendarContract.Events.EVENT_LOCATION, event.location)
                    .withValue(CalendarContract.Events.DESCRIPTION, event.description)
                    .withValue(CalendarContract.Events.DTSTART, event.start)
                    .withValue(CalendarContract.Events.DTEND, event.end)
                    .withValue(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    .withValue(CalendarContract.Events.EVENT_COLOR, event.color)
                    .withValue(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
                    .withValue(CalendarContract.Events.CUSTOM_APP_URI, "ifafu://term/" + event.scope)
                    .build()
            }
        }
        resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        return events.size
    }
}
