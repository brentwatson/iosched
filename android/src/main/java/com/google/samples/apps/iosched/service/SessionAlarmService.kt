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

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.support.v4.app.TaskStackBuilder
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.explore.ExploreIOActivity
import com.google.samples.apps.iosched.feedback.FeedbackHelper
import com.google.samples.apps.iosched.feedback.SessionFeedbackActivity
import com.google.samples.apps.iosched.map.MapActivity
import com.google.samples.apps.iosched.myschedule.MyScheduleActivity
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.util.LogUtils.*
import com.google.samples.apps.iosched.util.UIUtils
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Background service to handle scheduling of starred session notification via
 * [android.app.AlarmManager]. The service also handles invoking the system notifications to
 * provide feedback for the starred sessions.
 */
class SessionAlarmService : IntentService(SessionAlarmService.TAG), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private var mGoogleApiClient: GoogleApiClient? = null

    override fun onCreate() {
        super.onCreate()
        mGoogleApiClient = GoogleApiClient.Builder(this).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build()
    }

    override fun onHandleIntent(intent: Intent?) {
        mGoogleApiClient!!.blockingConnect(2000, TimeUnit.MILLISECONDS)
        val action = intent!!.action

        LOGD(TAG, "SessionAlarmService handling " + action)

        if (ACTION_SCHEDULE_ALL_STARRED_BLOCKS == action) {
            LOGD(TAG, "Scheduling all starred blocks.")
            scheduleAllStarredBlocks()
            scheduleAllStarredSessionFeedbacks()
            return
        } else if (ACTION_NOTIFY_SESSION_FEEDBACK == action) {
            LOGD(TAG, "Showing session feedback notification.")
            notifySessionFeedback(DEBUG_SESSION_ID == intent.getStringExtra(EXTRA_SESSION_ID))
            return
        }

        val sessionEnd = intent.getLongExtra(SessionAlarmService.EXTRA_SESSION_END,
                UNDEFINED_VALUE)
        if (sessionEnd == UNDEFINED_VALUE) {
            LOGD(TAG, "IGNORING ACTION -- missing sessionEnd parameter")
            return
        }

        val sessionAlarmOffset = intent.getLongExtra(SessionAlarmService.EXTRA_SESSION_ALARM_OFFSET,
                UNDEFINED_ALARM_OFFSET)
        LOGD(TAG, "Session alarm offset is: " + sessionAlarmOffset)

        // Feedback notifications have a slightly different set of extras.
        if (ACTION_SCHEDULE_FEEDBACK_NOTIFICATION == action) {
            val sessionId = intent.getStringExtra(SessionAlarmService.EXTRA_SESSION_ID)
            val sessionTitle = intent.getStringExtra(
                    SessionAlarmService.EXTRA_SESSION_TITLE)
            if (sessionTitle == null || sessionEnd == UNDEFINED_VALUE ||
                    sessionId == null) {
                LOGE(TAG, "Attempted to schedule for feedback without providing extras.")
                return
            }
            LOGD(TAG, "Scheduling feedback alarm for session: " + sessionTitle)
            scheduleFeedbackAlarm(sessionEnd, sessionAlarmOffset, sessionTitle)
            return
        }

        val sessionStart = intent.getLongExtra(SessionAlarmService.EXTRA_SESSION_START, UNDEFINED_VALUE)
        if (sessionStart == UNDEFINED_VALUE) {
            LOGD(TAG, "IGNORING ACTION -- no session start parameter.")
            return
        }

        if (ACTION_NOTIFY_SESSION == action) {
            LOGD(TAG, "Notifying about sessions starting at " +
                    sessionStart + " = " + Date(sessionStart).toString())
            LOGD(TAG, "-> Alarm offset: " + sessionAlarmOffset)
            notifySession(sessionStart, sessionAlarmOffset)
        } else if (ACTION_SCHEDULE_STARRED_BLOCK == action) {
            LOGD(TAG, "Scheduling session alarm.")
            LOGD(TAG, "-> Session start: " + sessionStart + " = " + Date(sessionStart).toString())
            LOGD(TAG, "-> Session end: " + sessionEnd + " = " + Date(sessionEnd).toString())
            LOGD(TAG, "-> Alarm offset: " + sessionAlarmOffset)
            scheduleAlarm(sessionStart, sessionEnd, sessionAlarmOffset)
        }
    }

    fun scheduleFeedbackAlarm(sessionEnd: Long,
                              alarmOffset: Long, sessionTitle: String) {
        // By default, feedback alarms fire 5 minutes before session end time. If alarm offset is
        // provided, alarm is set to go off that much time from now (useful for testing).
        val alarmTime: Long
        if (alarmOffset == UNDEFINED_ALARM_OFFSET) {
            alarmTime = sessionEnd - MILLI_FIVE_MINUTES
        } else {
            alarmTime = UIUtils.getCurrentTime(this) + alarmOffset
        }

        LOGD(TAG, "Scheduling session feedback alarm for session '$sessionTitle'")
        LOGD(TAG, "  -> end time: " + sessionEnd + " = " + Date(sessionEnd).toString())
        LOGD(TAG, "  -> alarm time: " + alarmTime + " = " + Date(alarmTime).toString())

        val feedbackIntent = Intent(
                ACTION_NOTIFY_SESSION_FEEDBACK,
                null,
                this,
                SessionAlarmService::class.java)
        val pi = PendingIntent.getService(
                this, 1, feedbackIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC_WAKEUP, alarmTime, pi)
    }

    private fun scheduleAlarm(sessionStart: Long,
                              sessionEnd: Long, alarmOffset: Long) {

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        val currentTime = UIUtils.getCurrentTime(this)
        // If the session is already started, do not schedule system notification.
        if (currentTime > sessionStart) {
            LOGD(TAG, "Not scheduling alarm because target time is in the past: " + sessionStart)
            return
        }

        // By default, sets alarm to go off at 10 minutes before session start time.  If alarm
        // offset is provided, alarm is set to go off by that much time from now.
        val alarmTime: Long
        if (alarmOffset == UNDEFINED_ALARM_OFFSET) {
            alarmTime = sessionStart - MILLI_TEN_MINUTES
        } else {
            alarmTime = currentTime + alarmOffset
        }

        LOGD(TAG, "Scheduling alarm for " + alarmTime + " = " + Date(alarmTime).toString())

        val notifIntent = Intent(
                ACTION_NOTIFY_SESSION,
                null,
                this,
                SessionAlarmService::class.java)
        // Setting data to ensure intent's uniqueness for different session start times.
        notifIntent.data = Uri.Builder().authority("com.google.samples.apps.iosched").path(sessionStart.toString()).build()
        notifIntent.putExtra(SessionAlarmService.EXTRA_SESSION_START, sessionStart)
        LOGD(TAG, "-> Intent extra: session start " + sessionStart)
        notifIntent.putExtra(SessionAlarmService.EXTRA_SESSION_END, sessionEnd)
        LOGD(TAG, "-> Intent extra: session end " + sessionEnd)
        notifIntent.putExtra(SessionAlarmService.EXTRA_SESSION_ALARM_OFFSET, alarmOffset)
        LOGD(TAG, "-> Intent extra: session alarm offset " + alarmOffset)
        val pi = PendingIntent.getService(this,
                0,
                notifIntent,
                PendingIntent.FLAG_CANCEL_CURRENT)
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Schedule an alarm to be fired to notify user of added sessions are about to begin.
        LOGD(TAG, "-> Scheduling RTC_WAKEUP alarm at " + alarmTime)
        am.set(AlarmManager.RTC_WAKEUP, alarmTime, pi)
    }

    /**
     * A starred session is about to end. Notify the user to provide session feedback.
     * Constructs and triggers a system notification. Does nothing if the session has already
     * concluded.
     */
    private fun notifySessionFeedback(debug: Boolean) {
        LOGD(TAG, "Considering firing notification for session feedback.")

        if (debug) {
            LOGW(TAG, "Note: this is a debug notification.")
        }

        // Don't fire notification if this feature is disabled in settings
        if (!SettingsUtils.shouldShowSessionFeedbackReminders(this)) {
            LOGD(TAG, "Skipping session feedback notification. Disabled in settings.")
            return
        }

        var c: Cursor? = null
        try {
            c = contentResolver.query(
                    ScheduleContract.Sessions.CONTENT_MY_SCHEDULE_URI,
                    SessionsNeedingFeedbackQuery.PROJECTION,
                    SessionsNeedingFeedbackQuery.WHERE_CLAUSE, null, null)
            if (c == null) {
                return
            }

            val feedbackHelper = FeedbackHelper(this)

            val needFeedbackIds = ArrayList<String>()
            val needFeedbackTitles = ArrayList<String>()
            while (c.moveToNext()) {
                val sessionId = c.getString(SessionsNeedingFeedbackQuery.SESSION_ID)
                val sessionTitle = c.getString(SessionsNeedingFeedbackQuery.SESSION_TITLE)

                // Avoid repeated notifications.
                if (feedbackHelper.isFeedbackNotificationFiredForSession(sessionId)) {
                    LOGD(TAG, "Skipping repeated session feedback notification for session '"
                            + sessionTitle + "'")
                    continue
                }

                needFeedbackIds.add(sessionId)
                needFeedbackTitles.add(sessionTitle)
            }

            if (needFeedbackIds.size == 0) {
                // the user has already been notified of all sessions needing feedback
                return
            }

            LOGD(TAG, "Going forward with session feedback notification for "
                    + needFeedbackIds.size + " session(s).")

            val res = resources

            // this is used to synchronize deletion of notifications on phone and wear
            val dismissalIntent = Intent(ACTION_NOTIFICATION_DISMISSAL)
            // TODO: fix Wear dismiss integration
            //dismissalIntent.putExtra(KEY_SESSION_ID, sessionId);
            val dismissalPendingIntent = PendingIntent.getService(this, Date().time.toInt(), dismissalIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT)

            val provideFeedbackTicker = res.getString(R.string.session_feedback_notification_ticker)
            val notifBuilder = NotificationCompat.Builder(this).setColor(resources.getColor(R.color.theme_primary)).setContentText(provideFeedbackTicker).setTicker(provideFeedbackTicker).setLights(
                    SessionAlarmService.NOTIFICATION_ARGB_COLOR,
                    SessionAlarmService.NOTIFICATION_LED_ON_MS,
                    SessionAlarmService.NOTIFICATION_LED_OFF_MS).setSmallIcon(R.drawable.ic_stat_notification).setPriority(Notification.PRIORITY_LOW).setLocalOnly(true) // make it local to the phone
                    .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE).setDeleteIntent(dismissalPendingIntent).setAutoCancel(true)

            if (needFeedbackIds.size == 1) {
                // Only 1 session needs feedback
                val sessionUri = ScheduleContract.Sessions.buildSessionUri(needFeedbackIds[0])
                val pi = TaskStackBuilder.create(this).addNextIntent(Intent(this, MyScheduleActivity::class.java)).addNextIntent(Intent(Intent.ACTION_VIEW, sessionUri, this,
                        SessionFeedbackActivity::class.java)).getPendingIntent(1, PendingIntent.FLAG_CANCEL_CURRENT)

                notifBuilder.setContentTitle(needFeedbackTitles[0]).setContentIntent(pi)
            } else {
                // Show information about several sessions that need feedback
                val pi = TaskStackBuilder.create(this).addNextIntent(Intent(this, MyScheduleActivity::class.java)).getPendingIntent(1, PendingIntent.FLAG_CANCEL_CURRENT)

                val inboxStyle = NotificationCompat.InboxStyle()
                inboxStyle.setBigContentTitle(provideFeedbackTicker)
                for (title in needFeedbackTitles) {
                    inboxStyle.addLine(title)
                }

                notifBuilder.setContentTitle(
                        resources.getQuantityString(R.plurals.session_plurals,
                                needFeedbackIds.size, needFeedbackIds.size)).setStyle(inboxStyle).setContentIntent(pi)
            }

            val nm = getSystemService(
                    Context.NOTIFICATION_SERVICE) as NotificationManager
            LOGD(TAG, "Now showing session feedback notification!")
            nm.notify(FEEDBACK_NOTIFICATION_ID, notifBuilder.build())

            for (i in needFeedbackIds.indices) {
                setupNotificationOnWear(needFeedbackIds[i], null, needFeedbackTitles[i], null)
                feedbackHelper.setFeedbackNotificationAsFiredForSession(needFeedbackIds[i])
            }
        } finally {
            if (c != null) {
                try {
                    c.close()
                } catch (ignored: Exception) {
                }

            }
        }
    }

    /**
     * Builds corresponding notification for the Wear device that is paired to this handset. This
     * is done by adding a Data Item to teh Data Store; the Wear device will be notified to build a
     * local notification.
     */
    private fun setupNotificationOnWear(sessionId: String, sessionRoom: String?, sessionName: String,
                                        speaker: String?) {
        if (!mGoogleApiClient!!.isConnected) {
            Log.e(TAG, "setupNotificationOnWear(): Failed to send data item since there was no " + "connectivity to Google API Client")
            return
        }
        val putDataMapRequest = PutDataMapRequest.create(FeedbackHelper.getFeedbackDataPathForWear(sessionId))
        putDataMapRequest.dataMap.putLong("time", Date().time)
        putDataMapRequest.dataMap.putString(KEY_SESSION_ID, sessionId)
        putDataMapRequest.dataMap.putString(KEY_SESSION_NAME, sessionName)
        putDataMapRequest.dataMap.putString(KEY_SPEAKER_NAME, speaker)
        putDataMapRequest.dataMap.putString(KEY_SESSION_ROOM, sessionRoom)

        val request = putDataMapRequest.asPutDataRequest()

        Wearable.DataApi.putDataItem(mGoogleApiClient, request).setResultCallback { dataItemResult -> LOGD(TAG, "setupNotificationOnWear(): Sending notification result success:" + dataItemResult.status.isSuccess) }
    }

    // Starred sessions are about to begin.  Constructs and triggers system notification.
    private fun notifySession(sessionStart: Long, alarmOffset: Long) {
        val currentTime = UIUtils.getCurrentTime(this)
        val intervalEnd = sessionStart + MILLI_TEN_MINUTES
        LOGD(TAG, "Considering notifying for time interval.")
        LOGD(TAG, "    Interval start: " + sessionStart + "=" + Date(sessionStart).toString())
        LOGD(TAG, "    Interval end: " + intervalEnd + "=" + Date(intervalEnd).toString())
        LOGD(TAG, "    Current time is: " + currentTime + "=" + Date(currentTime).toString())
        if (sessionStart < currentTime) {
            LOGD(TAG, "Skipping session notification (too late -- time interval already started)")
            return
        }

        if (!SettingsUtils.shouldShowSessionReminders(this)) {
            // skip if disabled in settings
            LOGD(TAG, "Skipping session notification for sessions. Disabled in settings.")
            return
        }

        // Avoid repeated notifications.
        if (alarmOffset == UNDEFINED_ALARM_OFFSET && UIUtils.isNotificationFiredForBlock(
                this, ScheduleContract.Blocks.generateBlockId(sessionStart, intervalEnd))) {
            LOGD(TAG, "Skipping session notification (already notified)")
            return
        }

        val cr = contentResolver

        LOGD(TAG, "Looking for sessions in interval $sessionStart - $intervalEnd")
        var c: Cursor? = null
        try {
            c = cr.query(
                    ScheduleContract.Sessions.CONTENT_MY_SCHEDULE_URI,
                    SessionDetailQuery.PROJECTION,
                    ScheduleContract.Sessions.STARTING_AT_TIME_INTERVAL_SELECTION,
                    ScheduleContract.Sessions.buildAtTimeIntervalArgs(sessionStart, intervalEnd),
                    null)
            val starredCount = c!!.count
            LOGD(TAG, "# starred sessions in that interval: " + c.count)
            var singleSessionId: String? = null
            var singleSessionRoomId: String? = null
            val starredSessionTitles = ArrayList<String>()
            while (c.moveToNext()) {
                singleSessionId = c.getString(SessionDetailQuery.SESSION_ID)
                singleSessionRoomId = c.getString(SessionDetailQuery.ROOM_ID)
                starredSessionTitles.add(c.getString(SessionDetailQuery.SESSION_TITLE))
                LOGD(TAG, "-> Title: " + c.getString(SessionDetailQuery.SESSION_TITLE))
            }
            if (starredCount < 1) {
                return
            }

            // Generates the pending intent which gets fired when the user taps on the notification.
            // NOTE: Use TaskStackBuilder to comply with Android's design guidelines
            // related to navigation from notifications.
            val baseIntent = Intent(this, MyScheduleActivity::class.java)
            baseIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            val taskBuilder = TaskStackBuilder.create(this).addNextIntent(baseIntent)

            // For a single session, tapping the notification should open the session details (b/15350787)
            if (starredCount == 1) {
                taskBuilder.addNextIntent(Intent(Intent.ACTION_VIEW,
                        ScheduleContract.Sessions.buildSessionUri(singleSessionId)))
            }

            val pi = taskBuilder.getPendingIntent(0, PendingIntent.FLAG_CANCEL_CURRENT)

            val res = resources
            val contentText: String
            var minutesLeft = (sessionStart - currentTime + 59000).toInt() / 60000
            if (minutesLeft < 1) {
                minutesLeft = 1
            }

            if (starredCount == 1) {
                contentText = res.getString(R.string.session_notification_text_1, minutesLeft)
            } else {
                contentText = res.getQuantityString(R.plurals.session_notification_text,
                        starredCount - 1,
                        minutesLeft,
                        starredCount - 1)
            }

            val notifBuilder = NotificationCompat.Builder(this).setContentTitle(starredSessionTitles[0]).setContentText(contentText).setColor(resources.getColor(R.color.theme_primary)).setTicker(res.getQuantityString(R.plurals.session_notification_ticker,
                    starredCount,
                    starredCount)).setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE).setLights(
                    SessionAlarmService.NOTIFICATION_ARGB_COLOR,
                    SessionAlarmService.NOTIFICATION_LED_ON_MS,
                    SessionAlarmService.NOTIFICATION_LED_OFF_MS).setSmallIcon(R.drawable.ic_stat_notification).setContentIntent(pi).setPriority(Notification.PRIORITY_MAX).setAutoCancel(true)
            if (minutesLeft > 5) {
                notifBuilder.addAction(R.drawable.ic_alarm_holo_dark,
                        String.format(res.getString(R.string.snooze_x_min), 5),
                        createSnoozeIntent(sessionStart, intervalEnd, 5))
            }
            if (starredCount == 1 && SettingsUtils.isAttendeeAtVenue(this)) {
                notifBuilder.addAction(R.drawable.ic_map_holo_dark,
                        res.getString(R.string.title_map),
                        createRoomMapIntent(singleSessionRoomId!!))
            }
            val bigContentTitle: String
            if (starredCount == 1 && starredSessionTitles.size > 0) {
                bigContentTitle = starredSessionTitles[0]
            } else {
                bigContentTitle = res.getQuantityString(R.plurals.session_notification_title,
                        starredCount,
                        minutesLeft,
                        starredCount)
            }
            val richNotification = NotificationCompat.InboxStyle(
                    notifBuilder).setBigContentTitle(bigContentTitle)

            // Adds starred sessions starting at this time block to the notification.
            for (i in 0..starredCount - 1) {
                richNotification.addLine(starredSessionTitles[i])
            }
            val nm = getSystemService(
                    Context.NOTIFICATION_SERVICE) as NotificationManager
            LOGD(TAG, "Now showing notification.")
            nm.notify(NOTIFICATION_ID, richNotification.build())
        } finally {
            if (c != null) {
                try {
                    c.close()
                } catch (ignored: Exception) {
                }

            }
        }
    }

    private fun createSnoozeIntent(sessionStart: Long, sessionEnd: Long,
                                   snoozeMinutes: Int): PendingIntent {
        val scheduleIntent = Intent(
                SessionAlarmService.ACTION_SCHEDULE_STARRED_BLOCK,
                null, this, SessionAlarmService::class.java)
        scheduleIntent.putExtra(SessionAlarmService.EXTRA_SESSION_START, sessionStart)
        scheduleIntent.putExtra(SessionAlarmService.EXTRA_SESSION_END, sessionEnd)
        scheduleIntent.putExtra(SessionAlarmService.EXTRA_SESSION_ALARM_OFFSET,
                snoozeMinutes * MILLI_ONE_MINUTE)
        return PendingIntent.getService(this, 0, scheduleIntent,
                PendingIntent.FLAG_CANCEL_CURRENT)
    }

    private fun createRoomMapIntent(roomId: String): PendingIntent {
        val mapIntent = Intent(applicationContext, MapActivity::class.java)
        mapIntent.putExtra(MapActivity.EXTRA_ROOM, roomId)
        mapIntent.putExtra(MapActivity.EXTRA_DETACHED_MODE, true)
        return TaskStackBuilder.create(applicationContext).addNextIntent(Intent(this, ExploreIOActivity::class.java)).addNextIntent(mapIntent).getPendingIntent(0, PendingIntent.FLAG_CANCEL_CURRENT)
    }

    private fun scheduleAllStarredBlocks() {
        val cr = contentResolver
        var c: Cursor? = null
        try {
            c = cr.query(ScheduleContract.Sessions.CONTENT_MY_SCHEDULE_URI,
                    arrayOf(ScheduleContractHelper.formatQueryDistinctParameter(
                            ScheduleContract.Sessions.SESSION_START), ScheduleContract.Sessions.SESSION_END, ScheduleContract.Sessions.SESSION_IN_MY_SCHEDULE),
                    null,
                    null,
                    null)
            if (c == null) {
                return
            }

            while (c.moveToNext()) {
                val sessionStart = c.getLong(0)
                val sessionEnd = c.getLong(1)
                scheduleAlarm(sessionStart, sessionEnd, UNDEFINED_ALARM_OFFSET)
            }
        } finally {
            if (c != null) {
                try {
                    c.close()
                } catch (ignored: Exception) {
                }

            }
        }
    }

    // Schedules feedback alarms for all starred sessions.
    private fun scheduleAllStarredSessionFeedbacks() {
        val cr = contentResolver
        var c: Cursor? = null
        try {
            c = cr.query(ScheduleContract.Sessions.CONTENT_MY_SCHEDULE_URI,
                    arrayOf(ScheduleContract.Sessions.SESSION_TITLE, ScheduleContract.Sessions.SESSION_END, ScheduleContract.Sessions.SESSION_IN_MY_SCHEDULE),
                    null,
                    null,
                    null)
            if (c == null) {
                return
            }
            while (c.moveToNext()) {
                val sessionTitle = c.getString(0)
                val sessionEnd = c.getLong(1)
                scheduleFeedbackAlarm(sessionEnd, UNDEFINED_ALARM_OFFSET, sessionTitle)
            }
        } finally {
            if (c != null) {
                try {
                    c.close()
                } catch (ignored: Exception) {
                }

            }
        }
    }

    interface SessionDetailQuery {
        companion object {

            val PROJECTION = arrayOf(ScheduleContract.Sessions.SESSION_ID, ScheduleContract.Sessions.SESSION_TITLE, ScheduleContract.Sessions.ROOM_ID, ScheduleContract.Sessions.SESSION_IN_MY_SCHEDULE)

            val SESSION_ID = 0
            val SESSION_TITLE = 1
            val ROOM_ID = 2
        }
    }

    interface SessionsNeedingFeedbackQuery {
        companion object {
            val PROJECTION = arrayOf(ScheduleContract.Sessions.SESSION_ID, ScheduleContract.Sessions.SESSION_TITLE, ScheduleContract.Sessions.SESSION_IN_MY_SCHEDULE, ScheduleContract.Sessions.HAS_GIVEN_FEEDBACK)

            val SESSION_ID = 0
            val SESSION_TITLE = 1

            val WHERE_CLAUSE = ScheduleContract.Sessions.HAS_GIVEN_FEEDBACK + "=0"
        }
    }

    override fun onConnected(connectionHint: Bundle) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Connected to Google Api Service")
        }
    }

    override fun onConnectionSuspended(cause: Int) {
        // Ignore
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Disconnected from Google Api Service")
        }
    }

    companion object {

        private val TAG = makeLogTag(SessionAlarmService::class.java)

        val ACTION_NOTIFY_SESSION = "com.google.samples.apps.iosched.action.NOTIFY_SESSION"
        val ACTION_NOTIFY_SESSION_FEEDBACK = "com.google.samples.apps.iosched.action.NOTIFY_SESSION_FEEDBACK"
        val ACTION_SCHEDULE_FEEDBACK_NOTIFICATION = "com.google.samples.apps.iosched.action.SCHEDULE_FEEDBACK_NOTIFICATION"
        val ACTION_SCHEDULE_STARRED_BLOCK = "com.google.samples.apps.iosched.action.SCHEDULE_STARRED_BLOCK"
        val ACTION_SCHEDULE_ALL_STARRED_BLOCKS = "com.google.samples.apps.iosched.action.SCHEDULE_ALL_STARRED_BLOCKS"
        val EXTRA_SESSION_START = "com.google.samples.apps.iosched.extra.SESSION_START"
        val EXTRA_SESSION_END = "com.google.samples.apps.iosched.extra.SESSION_END"
        val EXTRA_SESSION_ALARM_OFFSET = "com.google.samples.apps.iosched.extra.SESSION_ALARM_OFFSET"
        val EXTRA_SESSION_ID = "com.google.samples.apps.iosched.extra.SESSION_ID"
        val EXTRA_SESSION_TITLE = "com.google.samples.apps.iosched.extra.SESSION_TITLE"

        val NOTIFICATION_ID = 100
        val FEEDBACK_NOTIFICATION_ID = 101

        // pulsate every 1 second, indicating a relatively high degree of urgency
        private val NOTIFICATION_LED_ON_MS = 100
        private val NOTIFICATION_LED_OFF_MS = 1000
        private val NOTIFICATION_ARGB_COLOR = 0xff0088ff.toInt() // cyan

        private val MILLI_TEN_MINUTES: Long = 600000
        private val MILLI_FIVE_MINUTES: Long = 300000
        private val MILLI_ONE_MINUTE: Long = 60000

        private val UNDEFINED_ALARM_OFFSET: Long = -1
        private val UNDEFINED_VALUE: Long = -1
        val ACTION_NOTIFICATION_DISMISSAL = "com.google.sample.apps.iosched.ACTION_NOTIFICATION_DISMISSAL"
        val KEY_SESSION_ID = "session-id"
        private val KEY_SESSION_NAME = "session-name"
        private val KEY_SPEAKER_NAME = "speaker-name"
        private val KEY_SESSION_ROOM = "session-room"
        val PATH_FEEDBACK = "/iowear/feedback"

        // special session ID that identifies a debug notification
        val DEBUG_SESSION_ID = "debug-session-id"
    }

}
