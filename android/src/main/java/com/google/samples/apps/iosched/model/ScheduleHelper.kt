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
package com.google.samples.apps.iosched.model

import android.content.Context
import android.database.Cursor
import android.os.AsyncTask
import android.text.TextUtils
import android.util.Log
import com.google.samples.apps.iosched.BuildConfig
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.myschedule.MyScheduleAdapter
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContract.Blocks
import com.google.samples.apps.iosched.provider.ScheduleContract.Sessions
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.google.samples.apps.iosched.util.UIUtils
import java.util.*

class ScheduleHelper(private val mContext: Context) {

    fun getScheduleData(start: Long, end: Long): ArrayList<ScheduleItem> {
        // get sessions in my schedule and blocks, starting anytime in the conference day
        val mutableItems = ArrayList<ScheduleItem>()
        val immutableItems = ArrayList<ScheduleItem>()
        addBlocks(start, end, mutableItems, immutableItems)
        addSessions(start, end, mutableItems, immutableItems)

        val result = ScheduleItemHelper.processItems(mutableItems, immutableItems)
        if (BuildConfig.DEBUG || Log.isLoggable(TAG, Log.DEBUG)) {
            var previous: ScheduleItem? = null
            for (item in result) {
                if (item.flags and ScheduleItem.FLAG_CONFLICTS_WITH_PREVIOUS != 0) {
                    Log.d(TAG, "Schedule Item conflicts with previous. item=$item previous=$previous")
                }
                previous = item
            }
        }

        setSessionCounters(result, start, end)
        return result
    }

    /**
     * Fill the number of sessions for FREE blocks:
     */
    protected fun setSessionCounters(items: ArrayList<ScheduleItem>, dayStart: Long, dayEnd: Long) {
        val free = ArrayList<ScheduleItem>()

        for (item in items) {
            if (item.type == ScheduleItem.FREE) {
                free.add(item)
            }
        }

        if (free.isEmpty()) {
            return
        }

        // Count number of start/end pairs for sessions that are between dayStart and dayEnd and
        // are not in my schedule:
        val liveStreamedOnlySelection = if (UIUtils.shouldShowLiveSessionsOnly(mContext))
            "AND IFNULL(" + ScheduleContract.Sessions.SESSION_LIVESTREAM_ID + ",'')!=''"
        else
            ""

        val cursor = mContext.contentResolver.query(
                ScheduleContract.Sessions.buildCounterByIntervalUri(),
                SessionsCounterQuery.PROJECTION,
                Sessions.SESSION_START + ">=? AND " + Sessions.SESSION_START + "<=? AND " +
                        Sessions.SESSION_IN_MY_SCHEDULE + " = 0 " + liveStreamedOnlySelection,
                arrayOf(dayStart.toString(), dayEnd.toString()),
                null)

        while (cursor!!.moveToNext()) {
            val start = cursor.getLong(SessionsCounterQuery.SESSION_INTERVAL_START)
            val counter = cursor.getInt(SessionsCounterQuery.SESSION_INTERVAL_COUNT)

            // Find blocks that this interval applies.
            for (item in free) {
                // If grouped sessions starts and ends inside the free block, it is considered in it:
                if (item.startTime <= start && start < item.endTime) {
                    item.numOfSessions += counter
                }
            }
        }
        cursor.close()

        // remove free blocks that have no available sessions or that are in the past
        val now = UIUtils.getCurrentTime(mContext)
        val it = items.iterator()
        while (it.hasNext()) {
            val i = it.next()
            if (i.type == ScheduleItem.FREE) {
                if (i.endTime < now) {
                    LOGD(TAG, "Removing empty block in the past.")
                    it.remove()
                } else if (i.numOfSessions == 0) {
                    LOGD(TAG, "Removing block with zero sessions: " + Date(i.startTime) + "-" + Date(i.endTime))
                    it.remove()
                } else {
                    i.subtitle = mContext.resources.getQuantityString(
                            R.plurals.schedule_block_subtitle, i.numOfSessions, i.numOfSessions)
                }

            }
        }
    }

    fun getScheduleDataAsync(adapter: MyScheduleAdapter,
                             start: Long, end: Long) {
        val task = object : AsyncTask<Long, Void, ArrayList<ScheduleItem>>() {
            override fun doInBackground(vararg params: Long?): ArrayList<ScheduleItem> {
                val start = params[0]
                val end = params[1]
                return getScheduleData(start!!, end!!)
            }

            override fun onPostExecute(scheduleItems: ArrayList<ScheduleItem>) {
                adapter.updateItems(scheduleItems)
            }
        }
        // On honeycomb and above, AsyncTasks are by default executed one by one. We are using a
        // thread pool instead here, because we want this to be executed independently from other
        // AsyncTasks. See the URL below for detail.
        // http://developer.android.com/reference/android/os/AsyncTask.html#execute(Params...)
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, start, end)
    }

    protected fun addSessions(start: Long, end: Long,
                              mutableItems: ArrayList<ScheduleItem>, immutableItems: ArrayList<ScheduleItem>) {

        var cursor: Cursor? = null
        try {
            cursor = mContext.contentResolver.query(
                    ScheduleContractHelper.addOverrideAccountName(Sessions.CONTENT_MY_SCHEDULE_URI,
                            AccountUtils.getActiveAccountName(mContext)!!),
                    SessionsQuery.PROJECTION,
                    // filter sessions to the specified day
                    Sessions.STARTING_AT_TIME_INTERVAL_SELECTION,
                    arrayOf(start.toString(), end.toString()),
                    // order by session start
                    Sessions.SESSION_START)

            if (cursor!!.moveToFirst()) {
                do {
                    val item = ScheduleItem()
                    item.type = ScheduleItem.SESSION
                    item.sessionId = cursor.getString(SessionsQuery.SESSION_ID)
                    item.title = cursor.getString(SessionsQuery.SESSION_TITLE)
                    item.startTime = cursor.getLong(SessionsQuery.SESSION_START)
                    item.endTime = cursor.getLong(SessionsQuery.SESSION_END)
                    if (!TextUtils.isEmpty(cursor.getString(SessionsQuery.SESSION_LIVESTREAM_URL))) {
                        item.flags = item.flags or ScheduleItem.FLAG_HAS_LIVESTREAM
                    }
                    item.subtitle = UIUtils.formatSessionSubtitle(
                            cursor.getString(SessionsQuery.ROOM_ROOM_NAME),
                            cursor.getString(SessionsQuery.SESSION_SPEAKER_NAMES), mContext)
                    item.room = cursor.getString(SessionsQuery.ROOM_ROOM_NAME)
                    item.backgroundImageUrl = cursor.getString(SessionsQuery.SESSION_PHOTO_URL)
                    item.backgroundColor = cursor.getInt(SessionsQuery.SESSION_COLOR)
                    item.hasGivenFeedback = cursor.getInt(SessionsQuery.HAS_GIVEN_FEEDBACK) > 0
                    item.sessionType = detectSessionType(cursor.getString(SessionsQuery.SESSION_TAGS))
                    item.mainTag = cursor.getString(SessionsQuery.SESSION_MAIN_TAG)
                    immutableItems.add(item)
                } while (cursor.moveToNext())
            }
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
    }

    protected fun addBlocks(start: Long, end: Long,
                            mutableItems: ArrayList<ScheduleItem>, immutableItems: ArrayList<ScheduleItem>) {
        var cursor: Cursor? = null
        try {
            cursor = mContext.contentResolver.query(
                    Blocks.CONTENT_URI,
                    BlocksQuery.PROJECTION,

                    // filter sessions on the specified day
                    Blocks.BLOCK_START + " >= ? and " + Blocks.BLOCK_START + " <= ?",
                    arrayOf(start.toString(), end.toString()),

                    // order by session start
                    Blocks.BLOCK_START)

            if (cursor!!.moveToFirst()) {
                do {
                    val item = ScheduleItem()
                    item.setTypeFromBlockType(cursor.getString(BlocksQuery.BLOCK_TYPE))
                    item.title = cursor.getString(BlocksQuery.BLOCK_TITLE)
                    item.subtitle = cursor.getString(BlocksQuery.BLOCK_SUBTITLE)
                    item.room = item.subtitle
                    item.startTime = cursor.getLong(BlocksQuery.BLOCK_START)
                    item.endTime = cursor.getLong(BlocksQuery.BLOCK_END)

                    // Hide BREAK blocks to remote attendees (b/14666391):
                    if (item.type == ScheduleItem.BREAK && !SettingsUtils.isAttendeeAtVenue(mContext)) {
                        continue
                    }
                    // Currently, only type=FREE is mutable
                    if (item.type == ScheduleItem.FREE) {
                        mutableItems.add(item)
                    } else {
                        immutableItems.add(item)
                        item.flags = item.flags or ScheduleItem.FLAG_NOT_REMOVABLE
                    }
                } while (cursor.moveToNext())
            }
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
    }

    private interface SessionsQuery {
        companion object {
            val PROJECTION = arrayOf(Sessions.SESSION_ID, Sessions.SESSION_TITLE, Sessions.SESSION_START, Sessions.SESSION_END, ScheduleContract.Rooms.ROOM_NAME, Sessions.SESSION_IN_MY_SCHEDULE, Sessions.SESSION_LIVESTREAM_ID, Sessions.SESSION_SPEAKER_NAMES, Sessions.SESSION_PHOTO_URL, Sessions.SESSION_COLOR, Sessions.HAS_GIVEN_FEEDBACK, Sessions.SESSION_TAGS, Sessions.SESSION_MAIN_TAG)

            val SESSION_ID = 0
            val SESSION_TITLE = 1
            val SESSION_START = 2
            val SESSION_END = 3
            val ROOM_ROOM_NAME = 4
            val SESSION_LIVESTREAM_URL = 6
            val SESSION_SPEAKER_NAMES = 7
            val SESSION_PHOTO_URL = 8
            val SESSION_COLOR = 9
            val HAS_GIVEN_FEEDBACK = 10
            val SESSION_TAGS = 11
            val SESSION_MAIN_TAG = 12
        }
    }

    private interface BlocksQuery {
        companion object {
            val PROJECTION = arrayOf(Blocks.BLOCK_TITLE, Blocks.BLOCK_TYPE, Blocks.BLOCK_START, Blocks.BLOCK_END, Blocks.BLOCK_SUBTITLE)

            val BLOCK_TITLE = 0
            val BLOCK_TYPE = 1
            val BLOCK_START = 2
            val BLOCK_END = 3
            val BLOCK_SUBTITLE = 4
        }
    }


    private interface SessionsCounterQuery {
        companion object {
            val PROJECTION = arrayOf(Sessions.SESSION_START, Sessions.SESSION_END, Sessions.SESSION_INTERVAL_COUNT, Sessions.SESSION_IN_MY_SCHEDULE)

            val SESSION_INTERVAL_START = 0
            val SESSION_INTERVAL_END = 1
            val SESSION_INTERVAL_COUNT = 2
        }
    }

    companion object {

        private val TAG = makeLogTag(ScheduleHelper::class.java)

        fun detectSessionType(tagsText: String): Int {
            if (TextUtils.isEmpty(tagsText)) {
                return ScheduleItem.SESSION_TYPE_MISC
            }
            val tags = tagsText.toUpperCase(Locale.US)
            if (tags.contains("TYPE_SESSIONS") || tags.contains("KEYNOTE")) {
                return ScheduleItem.SESSION_TYPE_SESSION
            } else if (tags.contains("TYPE_CODELAB")) {
                return ScheduleItem.SESSION_TYPE_CODELAB
            } else if (tags.contains("TYPE_SANDBOXTALKS")) {
                return ScheduleItem.SESSION_TYPE_BOXTALK
            } else if (tags.contains("TYPE_APPREVIEWS") || tags.contains("TYPE_OFFICEHOURS") ||
                    tags.contains("TYPE_WORKSHOPS")) {
                return ScheduleItem.SESSION_TYPE_MISC
            }
            return ScheduleItem.SESSION_TYPE_MISC // default
        }
    }

}
