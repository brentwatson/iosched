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

package com.google.samples.apps.iosched.appwidget

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.util.SparseBooleanArray
import android.util.SparseIntArray
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.model.ScheduleHelper
import com.google.samples.apps.iosched.model.ScheduleItem
import com.google.samples.apps.iosched.myschedule.MyScheduleActivity
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.ui.SimpleSectionedListAdapter
import com.google.samples.apps.iosched.ui.TaskStackBuilderProxyActivity
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGE
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.google.samples.apps.iosched.util.TimeUtils
import com.google.samples.apps.iosched.util.UIUtils
import java.util.*

/**
 * This is the service that provides the factory to be bound to the collection service.
 */
class ScheduleWidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory {
        return WidgetRemoteViewsFactory(this.applicationContext)
    }

    /**
     * This is the factory that will provide data to the collection widget.
     */
    private class WidgetRemoteViewsFactory(private val mContext: Context) : RemoteViewsService.RemoteViewsFactory {
        private var mPMap: SparseIntArray? = null
        private var mSections: MutableList<SimpleSectionedListAdapter.Section>? = null
        private var mHeaderPositionMap: SparseBooleanArray? = null

        internal var mBuffer = StringBuilder()
        internal var mFormatter = Formatter(mBuffer, Locale.getDefault())
        private var mScheduleItems: ArrayList<ScheduleItem>? = null
        private var mDefaultSessionColor: Int = 0
        private var mDefaultStartEndTimeColor: Int = 0

        override fun onCreate() {
            // Since we reload the cursor in onDataSetChanged() which gets called immediately after
            // onCreate(), we do nothing here.
        }

        override fun onDestroy() {

        }

        override fun getCount(): Int {
            if (mScheduleItems == null || !AccountUtils.hasActiveAccount(mContext)) {
                return 0
            }
            if (mScheduleItems!!.size < 10) {
                init()
            }
            return mScheduleItems!!.size
        }

        fun getItemViewType(position: Int): Int {
            if (position < 0 || position >= mScheduleItems!!.size) {
                LOGE(TAG, "Invalid view position passed to MyScheduleAdapter: " + position)
                return VIEW_TYPE_NORMAL
            }
            val item = mScheduleItems!![position]
            val now = UIUtils.getCurrentTime(mContext)
            if (item.startTime <= now && now <= item.endTime && item.type == ScheduleItem.SESSION) {
                return VIEW_TYPE_NOW
            } else {
                return VIEW_TYPE_NORMAL
            }
        }

        override fun getViewAt(position: Int): RemoteViews {
            val rv: RemoteViews

            val isSectionHeader = mHeaderPositionMap!!.get(position)
            val offset = mPMap!!.get(position)

            if (isSectionHeader) {
                rv = RemoteViews(mContext.packageName, R.layout.widget_schedule_header)
                val section = mSections!![offset - 1]
                rv.setTextViewText(R.id.widget_schedule_day, section.title)

            } else {
                val itemPosition = position - offset

                val homeIntent = Intent(mContext, MyScheduleActivity::class.java)

                val item = mScheduleItems!![itemPosition]
                val nextItem = if (itemPosition < mScheduleItems!!.size - 1) mScheduleItems!![itemPosition + 1] else null

                if (mDefaultSessionColor < 0) {
                    mDefaultSessionColor = mContext.resources.getColor(R.color.default_session_color)
                }

                val itemViewType = getItemViewType(itemPosition)
                var isNowPlaying = false
                val isPastDuringConference = false
                mDefaultStartEndTimeColor = R.color.body_text_2

                if (itemViewType == VIEW_TYPE_NOW) {
                    isNowPlaying = true
                    mDefaultStartEndTimeColor = R.color.body_text_1
                }

                rv = RemoteViews(mContext.packageName, R.layout.widget_schedule_item)


                if (itemPosition < 0 || itemPosition >= mScheduleItems!!.size) {
                    LOGE(TAG, "Invalid view position passed to MyScheduleAdapter: " + position)
                    return rv
                }

                val now = UIUtils.getCurrentTime(mContext)
                rv.setTextViewText(R.id.start_end_time, formatTime(now, item))

                rv.setViewVisibility(R.id.live_now_badge, View.GONE)

                // Set default colors to time indicators, in case they were overridden by conflict warning:
                if (!isNowPlaying) {
                    rv.setTextColor(R.id.start_end_time, mContext.resources.getColor(mDefaultStartEndTimeColor))
                }

                if (item.type == ScheduleItem.FREE) {
                    rv.setImageViewResource(R.id.icon, R.drawable.ic_browse)

                    rv.setTextViewText(R.id.slot_title, mContext.getText(R.string.browse_sessions))
                    rv.setTextColor(R.id.slot_title, mContext.resources.getColor(R.color.flat_button_text))

                    rv.setTextViewText(R.id.slot_room, item.subtitle)
                    rv.setTextColor(R.id.slot_room, mContext.resources.getColor(R.color.body_text_2))

                    val fillIntent = TaskStackBuilderProxyActivity.getFillIntent(
                            homeIntent,
                            Intent(Intent.ACTION_VIEW, ScheduleContract.Sessions.buildUnscheduledSessionsInInterval(
                                    item.startTime, item.endTime)))
                    rv.setOnClickFillInIntent(R.id.box, fillIntent)

                } else if (item.type == ScheduleItem.BREAK) {
                    rv.setImageViewResource(R.id.icon, UIUtils.getBreakIcon(item.title))

                    rv.setTextViewText(R.id.slot_title, item.title)
                    rv.setTextColor(R.id.slot_title, mContext.resources.getColor(R.color.body_text_1))

                    rv.setTextViewText(R.id.slot_room, item.room)
                    rv.setTextColor(R.id.slot_room, mContext.resources.getColor(R.color.body_text_2))

                } else if (item.type == ScheduleItem.SESSION) {
                    rv.setImageViewResource(R.id.icon, UIUtils.getSessionIcon(item.sessionType))

                    rv.setTextViewText(R.id.slot_title, item.title)
                    rv.setTextColor(R.id.slot_title, mContext.resources.getColor(R.color.body_text_1))

                    rv.setTextViewText(R.id.slot_room, item.room)
                    rv.setTextColor(R.id.slot_room, mContext.resources.getColor(R.color.body_text_2))

                    // show or hide the "LIVE NOW" badge
                    val showLiveBadge = 0 != item.flags and ScheduleItem.FLAG_HAS_LIVESTREAM
                            && now >= item.startTime && now <= item.endTime
                    rv.setViewVisibility(R.id.live_now_badge, if (showLiveBadge) View.VISIBLE else View.GONE)

                    // show or hide the "conflict" warning
                    if (!isPastDuringConference) {
                        val showConflict = 0 != item.flags and ScheduleItem.FLAG_CONFLICTS_WITH_PREVIOUS
                        if (showConflict && !isNowPlaying) {
                            val conflictColor = mContext.resources.getColor(R.color.my_schedule_conflict)
                            rv.setTextColor(R.id.start_end_time, conflictColor)
                        }
                    }

                    val fillIntent = TaskStackBuilderProxyActivity.getFillIntent(
                            homeIntent,
                            Intent(Intent.ACTION_VIEW, ScheduleContract.Sessions.buildSessionUri(item.sessionId)))
                    rv.setOnClickFillInIntent(R.id.box, fillIntent)

                } else {
                    LOGE(TAG, "Invalid item type in MyScheduleAdapter: " + item.type)
                }
            }

            return rv
        }

        override fun getLoadingView(): RemoteViews? {
            return null
        }

        override fun getViewTypeCount(): Int {
            return 4
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun hasStableIds(): Boolean {
            return true
        }

        override fun onDataSetChanged() {
            init()
        }

        private fun init() {
            val scheduleHelper = ScheduleHelper(mContext)

            //Fetch all sessions and blocks
            val allScheduleItems = scheduleHelper.getScheduleData(java.lang.Long.MIN_VALUE, java.lang.Long.MAX_VALUE)

            val displayTimeZone = SettingsUtils.getDisplayTimeZone(mContext).id

            mSections = ArrayList<SimpleSectionedListAdapter.Section>()
            var previousTime: Long = -1
            var time: Long
            mPMap = SparseIntArray()
            mHeaderPositionMap = SparseBooleanArray()
            var offset = 0
            var globalPosition = 0
            var position = 0
            mScheduleItems = ArrayList<ScheduleItem>()
            for (item in allScheduleItems) {
                if (item.endTime <= UIUtils.getCurrentTime(mContext)) {
                    continue
                }
                mScheduleItems!!.add(item)
                time = item.startTime
                if (!UIUtils.isSameDayDisplay(previousTime, time, mContext)) {
                    mBuffer.setLength(0)
                    mSections!!.add(SimpleSectionedListAdapter.Section(position,
                            DateUtils.formatDateRange(
                                    mContext, mFormatter,
                                    time, time,
                                    DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_DATE,
                                    displayTimeZone).toString()))
                    ++offset
                    mHeaderPositionMap!!.put(globalPosition, true)
                    mPMap!!.put(globalPosition, offset)
                    ++globalPosition
                }
                mHeaderPositionMap!!.put(globalPosition, false)
                mPMap!!.put(globalPosition, offset)
                ++globalPosition
                ++position
                previousTime = time
            }
        }

        private fun formatTime(now: Long, item: ScheduleItem): String {
            val time = StringBuilder()
            if (item.startTime <= now) {
                // session is happening now!
                if (0 != item.flags and ScheduleItem.FLAG_HAS_LIVESTREAM) {
                    // session has live stream
                    time.append(mContext.getString(R.string.watch_now))
                } else {
                    time.append(mContext.getString(R.string.session_now))
                }
            } else {
                // session in the future
                time.append(TimeUtils.formatShortTime(mContext, Date(item.startTime)))
            }
            time.append(" - ")
            time.append(TimeUtils.formatShortTime(mContext, Date(item.endTime)))
            return time.toString()
        }

        companion object {
            private val TAG = makeLogTag(WidgetRemoteViewsFactory::class.java)

            private val VIEW_TYPE_NORMAL = 0
            private val VIEW_TYPE_NOW = 1
        }
    }
}
