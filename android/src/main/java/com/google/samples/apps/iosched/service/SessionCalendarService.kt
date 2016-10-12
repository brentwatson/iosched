/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.iosched.service

import android.app.IntentService
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import android.provider.CalendarContract
import android.text.TextUtils
import android.util.Log
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.LogUtils.*
import java.util.*

/**
 * Background [android.app.Service] that adds or removes session Calendar events through
 * the [CalendarContract] API available in Android 4.0 or above.
 */
class SessionCalendarService : IntentService(SessionCalendarService.TAG) {

    override fun onHandleIntent(intent: Intent?) {
        val action = intent!!.action
        Log.d(TAG, "Received intent: " + action)

        val resolver = contentResolver

        var isAddEvent = false

        if (ACTION_ADD_SESSION_CALENDAR == action) {
            isAddEvent = true

        } else if (ACTION_REMOVE_SESSION_CALENDAR == action) {
            isAddEvent = false

        } else if (ACTION_UPDATE_ALL_SESSIONS_CALENDAR == action && SettingsUtils.shouldSyncCalendar(this)) {
            try {
                contentResolver.applyBatch(CalendarContract.AUTHORITY,
                        processAllSessionsCalendar(resolver, getCalendarId(intent)))
                sendBroadcast(Intent(
                        SessionCalendarService.ACTION_UPDATE_ALL_SESSIONS_CALENDAR_COMPLETED))
            } catch (e: RemoteException) {
                LOGE(TAG, "Error adding all sessions to Google Calendar", e)
            } catch (e: OperationApplicationException) {
                LOGE(TAG, "Error adding all sessions to Google Calendar", e)
            }

        } else if (ACTION_CLEAR_ALL_SESSIONS_CALENDAR == action) {
            try {
                contentResolver.applyBatch(CalendarContract.AUTHORITY,
                        processClearAllSessions(resolver, getCalendarId(intent)))
            } catch (e: RemoteException) {
                LOGE(TAG, "Error clearing all sessions from Google Calendar", e)
            } catch (e: OperationApplicationException) {
                LOGE(TAG, "Error clearing all sessions from Google Calendar", e)
            }

        } else {
            return
        }

        val uri = intent.data
        val extras = intent.extras
        if (uri == null || extras == null || !SettingsUtils.shouldSyncCalendar(this)) {
            return
        }

        try {
            resolver.applyBatch(CalendarContract.AUTHORITY,
                    processSessionCalendar(resolver, getCalendarId(intent), isAddEvent, uri,
                            extras.getLong(EXTRA_SESSION_START),
                            extras.getLong(EXTRA_SESSION_END),
                            extras.getString(EXTRA_SESSION_TITLE),
                            extras.getString(EXTRA_SESSION_ROOM)))
        } catch (e: RemoteException) {
            LOGE(TAG, "Error adding session to Google Calendar", e)
        } catch (e: OperationApplicationException) {
            LOGE(TAG, "Error adding session to Google Calendar", e)
        }

    }

    /**
     * Gets the currently-logged in user's Google Calendar, or the Google Calendar for the user
     * specified in the given intent's [.EXTRA_ACCOUNT_NAME].
     */
    private fun getCalendarId(intent: Intent?): Long {
        val accountName: String
        if (intent != null && intent.hasExtra(EXTRA_ACCOUNT_NAME)) {
            accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME)
        } else {
            accountName = AccountUtils.getActiveAccountName(this)!!
        }

        if (TextUtils.isEmpty(accountName)) {
            return INVALID_CALENDAR_ID
        }

        // TODO: The calendar ID should be stored in shared settings_prefs upon choosing an account.
        val calendarsCursor = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf("_id"),
                // TODO: What if the calendar is not displayed or not sync'd?
                "account_name = ownerAccount and account_name = ?",
                arrayOf(accountName),
                null)

        var calendarId = INVALID_CALENDAR_ID
        if (calendarsCursor != null && calendarsCursor.moveToFirst()) {
            calendarId = calendarsCursor.getLong(0)
            calendarsCursor.close()
        }

        return calendarId
    }

    private fun makeCalendarEventTitle(sessionTitle: String): String {
        return sessionTitle + resources.getString(R.string.session_calendar_suffix)
    }

    /**
     * Processes all sessions in the
     * [com.google.samples.apps.iosched.provider.ScheduleProvider], adding or removing
     * calendar events to/from the specified Google Calendar depending on whether a session is
     * in the user's schedule or not.
     */
    private fun processAllSessionsCalendar(resolver: ContentResolver,
                                           calendarId: Long): ArrayList<ContentProviderOperation> {

        val batch = ArrayList<ContentProviderOperation>()

        // Unable to find the Calendar associated with the user. Stop here.
        if (calendarId == INVALID_CALENDAR_ID) {
            return batch
        }

        // Retrieves all sessions. For each session, add to Calendar if starred and attempt to
        // remove from Calendar if unstarred.
        val cursor = resolver.query(
                ScheduleContract.Sessions.CONTENT_URI,
                SessionsQuery.PROJECTION,
                null, null, null)

        if (cursor != null) {
            while (cursor.moveToNext()) {
                val uri = ScheduleContract.Sessions.buildSessionUri(
                        java.lang.Long.valueOf(cursor.getLong(0))!!.toString())
                val isAddEvent = cursor.getInt(SessionsQuery.SESSION_IN_MY_SCHEDULE) == 1
                if (isAddEvent) {
                    batch.addAll(processSessionCalendar(resolver,
                            calendarId, isAddEvent, uri,
                            cursor.getLong(SessionsQuery.SESSION_START),
                            cursor.getLong(SessionsQuery.SESSION_END),
                            cursor.getString(SessionsQuery.SESSION_TITLE),
                            cursor.getString(SessionsQuery.ROOM_NAME)))
                }
            }
            cursor.close()
        }

        return batch
    }

    /**
     * Adds or removes a single session to/from the specified Google Calendar.
     */
    private fun processSessionCalendar(
            resolver: ContentResolver,
            calendarId: Long, isAddEvent: Boolean,
            sessionUri: Uri, sessionBlockStart: Long, sessionBlockEnd: Long,
            sessionTitle: String?, sessionRoom: String): ArrayList<ContentProviderOperation> {
        val batch = ArrayList<ContentProviderOperation>()

        // Unable to find the Calendar associated with the user. Stop here.
        if (calendarId == INVALID_CALENDAR_ID) {
            return batch
        }

        val calendarEventTitle = makeCalendarEventTitle(sessionTitle!!)

        val cursor: Cursor?
        val values = ContentValues()

        // Add Calendar event.
        if (isAddEvent) {
            if (sessionBlockStart == 0L || sessionBlockEnd == 0L || sessionTitle == null) {
                LOGW(TAG, "Unable to add a Calendar event due to insufficient input parameters.")
                return batch
            }

            // Check if the calendar event exists first.  If it does, we don't want to add a
            // duplicate one.
            cursor = resolver.query(
                    CalendarContract.Events.CONTENT_URI, // URI
                    arrayOf(CalendarContract.Events._ID), // Projection
                    CalendarContract.Events.CALENDAR_ID + "=? and "            // Selection

                            + CalendarContract.Events.TITLE + "=? and "
                            + CalendarContract.Events.DTSTART + ">=? and "
                            + CalendarContract.Events.DTEND + "<=?",
                    arrayOf(// Selection args
                            java.lang.Long.valueOf(calendarId)!!.toString(), calendarEventTitle, java.lang.Long.toString(Config.CONFERENCE_START_MILLIS), java.lang.Long.toString(Config.CONFERENCE_END_MILLIS)),
                    null)

            var newEventId: Long = -1

            if (cursor != null && cursor.moveToFirst()) {
                // Calendar event already exists for this session.
                newEventId = cursor.getLong(0)
                cursor.close()

                // Data fix (workaround):
                batch.add(
                        ContentProviderOperation.newUpdate(CalendarContract.Events.CONTENT_URI).withValue(CalendarContract.Events.EVENT_TIMEZONE,
                                Config.CONFERENCE_TIMEZONE.id).withSelection(CalendarContract.Events._ID + "=?",
                                arrayOf(java.lang.Long.valueOf(newEventId)!!.toString())).build())
                // End data fix.

            } else {
                // Calendar event doesn't exist, create it.

                // NOTE: we can't use batch processing here because we need the result of
                // the insert.
                values.clear()
                values.put(CalendarContract.Events.DTSTART, sessionBlockStart)
                values.put(CalendarContract.Events.DTEND, sessionBlockEnd)
                values.put(CalendarContract.Events.EVENT_LOCATION, sessionRoom)
                values.put(CalendarContract.Events.TITLE, calendarEventTitle)
                values.put(CalendarContract.Events.CALENDAR_ID, calendarId)
                values.put(CalendarContract.Events.EVENT_TIMEZONE,
                        Config.CONFERENCE_TIMEZONE.id)
                val eventUri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                val eventId = eventUri!!.lastPathSegment ?: return batch // Should be empty at this point

                newEventId = java.lang.Long.valueOf(eventId)!!
                // Since we're adding session reminder to system notification, we're not creating
                // Calendar event reminders.  If we were to create Calendar event reminders, this
                // is how we would do it.
                //values.put(CalendarContract.Reminders.EVENT_ID, Integer.valueOf(eventId));
                //values.put(CalendarContract.Reminders.MINUTES, 10);
                //values.put(CalendarContract.Reminders.METHOD,
                //        CalendarContract.Reminders.METHOD_ALERT); // Or default?
                //cr.insert(CalendarContract.Reminders.CONTENT_URI, values);
                //values.clear();
            }

            // Update the session in our own provider with the newly created calendar event ID.
            values.clear()
            values.put(ScheduleContract.Sessions.SESSION_CAL_EVENT_ID, newEventId)
            resolver.update(sessionUri, values, null, null)

        } else {
            // Remove Calendar event, if exists.

            // Get the event calendar id.
            cursor = resolver.query(sessionUri,
                    arrayOf(ScheduleContract.Sessions.SESSION_CAL_EVENT_ID),
                    null, null, null)
            var calendarEventId: Long = -1
            if (cursor != null && cursor.moveToFirst()) {
                calendarEventId = cursor.getLong(0)
                cursor.close()
            }

            // Try to remove the Calendar Event based on key.  If successful, move on;
            // otherwise, remove the event based on Event title.
            var affectedRows = 0
            if (calendarEventId != -1L) {
                affectedRows = resolver.delete(
                        CalendarContract.Events.CONTENT_URI,
                        CalendarContract.Events._ID + "=?",
                        arrayOf(java.lang.Long.valueOf(calendarEventId)!!.toString()))
            }

            if (affectedRows == 0) {
                resolver.delete(CalendarContract.Events.CONTENT_URI,
                        String.format("%s=? and %s=? and %s=? and %s=?",
                                CalendarContract.Events.CALENDAR_ID,
                                CalendarContract.Events.TITLE,
                                CalendarContract.Events.DTSTART,
                                CalendarContract.Events.DTEND),
                        arrayOf(java.lang.Long.valueOf(calendarId)!!.toString(), calendarEventTitle, java.lang.Long.valueOf(sessionBlockStart)!!.toString(), java.lang.Long.valueOf(sessionBlockEnd)!!.toString()))
            }

            // Remove the session and calendar event association.
            values.clear()
            values.remove(ScheduleContract.Sessions.SESSION_CAL_EVENT_ID)
            resolver.update(sessionUri, values, null, null)
        }

        return batch
    }

    /**
     * Removes all calendar entries associated with Google I/O 2013.
     */
    private fun processClearAllSessions(
            resolver: ContentResolver, calendarId: Long): ArrayList<ContentProviderOperation> {

        val batch = ArrayList<ContentProviderOperation>()

        // Unable to find the Calendar associated with the user. Stop here.
        if (calendarId == INVALID_CALENDAR_ID) {
            Log.e(TAG, "Unable to find Calendar for user")
            return batch
        }

        // Delete all calendar entries matching the given title within the given time period
        batch.add(ContentProviderOperation.newDelete(CalendarContract.Events.CONTENT_URI).withSelection(
                CalendarContract.Events.CALENDAR_ID + " = ? and "
                        + CalendarContract.Events.TITLE + " LIKE ? and "
                        + CalendarContract.Events.DTSTART + ">= ? and "
                        + CalendarContract.Events.DTEND + "<= ?",
                arrayOf(java.lang.Long.toString(calendarId), CALENDAR_CLEAR_SEARCH_LIKE_EXPRESSION, java.lang.Long.toString(Config.CONFERENCE_START_MILLIS), java.lang.Long.toString(Config.CONFERENCE_END_MILLIS))).build())

        return batch
    }

    private interface SessionsQuery {
        companion object {
            val PROJECTION = arrayOf(ScheduleContract.Sessions._ID, ScheduleContract.Sessions.SESSION_START, ScheduleContract.Sessions.SESSION_END, ScheduleContract.Sessions.SESSION_TITLE, ScheduleContract.Sessions.ROOM_NAME, ScheduleContract.Sessions.SESSION_IN_MY_SCHEDULE)

            val _ID = 0
            val SESSION_START = 1
            val SESSION_END = 2
            val SESSION_TITLE = 3
            val ROOM_NAME = 4
            val SESSION_IN_MY_SCHEDULE = 5
        }
    }

    companion object {
        private val TAG = makeLogTag(SessionCalendarService::class.java)

        val ACTION_ADD_SESSION_CALENDAR = "com.google.samples.apps.iosched.action.ADD_SESSION_CALENDAR"
        val ACTION_REMOVE_SESSION_CALENDAR = "com.google.samples.apps.iosched.action.REMOVE_SESSION_CALENDAR"
        val ACTION_UPDATE_ALL_SESSIONS_CALENDAR = "com.google.samples.apps.iosched.action.UPDATE_ALL_SESSIONS_CALENDAR"
        val ACTION_UPDATE_ALL_SESSIONS_CALENDAR_COMPLETED = "com.google.samples.apps.iosched.action.UPDATE_CALENDAR_COMPLETED"
        val ACTION_CLEAR_ALL_SESSIONS_CALENDAR = "com.google.samples.apps.iosched.action.CLEAR_ALL_SESSIONS_CALENDAR"
        val EXTRA_ACCOUNT_NAME = "com.google.samples.apps.iosched.extra.ACCOUNT_NAME"
        val EXTRA_SESSION_START = "com.google.samples.apps.iosched.extra.SESSION_BLOCK_START"
        val EXTRA_SESSION_END = "com.google.samples.apps.iosched.extra.SESSION_BLOCK_END"
        val EXTRA_SESSION_TITLE = "com.google.samples.apps.iosched.extra.SESSION_TITLE"
        val EXTRA_SESSION_ROOM = "com.google.samples.apps.iosched.extra.SESSION_ROOM"

        private val INVALID_CALENDAR_ID: Long = -1

        // TODO: localize
        private val CALENDAR_CLEAR_SEARCH_LIKE_EXPRESSION = "%added by Google I/O Android app%"
    }
}
