/*
 * Copyright 2015 Google Inc. All rights reserved.
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

package com.google.samples.apps.iosched.myschedule

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.res.TypedArray
import android.database.DataSetObserver
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.Button
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.TextView

import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.feedback.SessionFeedbackActivity
import com.google.samples.apps.iosched.model.ScheduleItem
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.util.ImageLoader
import com.google.samples.apps.iosched.util.LUtils
import com.google.samples.apps.iosched.util.TimeUtils
import com.google.samples.apps.iosched.util.UIUtils

import java.util.ArrayList
import java.util.Date

import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.LOGE
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

/**
 * Adapter that produces views to render (one day of) the "My Schedule" screen.
 */
class MyScheduleAdapter(private val mContext: Context, private val mLUtils: LUtils) : ListAdapter, AbsListView.RecyclerListener {

    // list of items served by this adapter
    internal var mItems = ArrayList<ScheduleItem>()

    // observers to notify about changes in the data
    internal var mObservers = ArrayList<DataSetObserver>()

    internal var mImageLoader: ImageLoader? = null

    private val mHourColorDefault: Int
    private val mHourColorPast: Int
    private val mTitleColorDefault: Int
    private val mTitleColorPast: Int
    private val mIconColorDefault: Int
    private val mIconColorPast: Int
    private val mColorConflict: Int
    private val mColorBackgroundDefault: Int
    private val mColorBackgroundPast: Int
    private val mListSpacing: Int
    private val mSelectableItemBackground: Int
    private val mIsRtl: Boolean

    init {
        val resources = mContext.resources
        mHourColorDefault = resources.getColor(R.color.my_schedule_hour_header_default)
        mHourColorPast = resources.getColor(R.color.my_schedule_hour_header_finished)
        mTitleColorDefault = resources.getColor(R.color.my_schedule_session_title_default)
        mTitleColorPast = resources.getColor(R.color.my_schedule_session_title_finished)
        mIconColorDefault = resources.getColor(R.color.my_schedule_icon_default)
        mIconColorPast = resources.getColor(R.color.my_schedule_icon_finished)
        mColorConflict = resources.getColor(R.color.my_schedule_conflict)
        mColorBackgroundDefault = resources.getColor(android.R.color.white)
        mColorBackgroundPast = resources.getColor(R.color.my_schedule_past_background)
        mListSpacing = resources.getDimensionPixelOffset(R.dimen.element_spacing_normal)
        val a = mContext.obtainStyledAttributes(intArrayOf(R.attr.selectableItemBackground))
        mSelectableItemBackground = a.getResourceId(0, 0)
        a.recycle()
        mIsRtl = UIUtils.isRtl(mContext)
    }

    override fun areAllItemsEnabled(): Boolean {
        return true
    }

    override fun isEnabled(position: Int): Boolean {
        return true
    }

    override fun registerDataSetObserver(observer: DataSetObserver) {
        if (!mObservers.contains(observer)) {
            mObservers.add(observer)
        }
    }

    override fun unregisterDataSetObserver(observer: DataSetObserver) {
        if (mObservers.contains(observer)) {
            mObservers.remove(observer)
        }
    }

    override fun getCount(): Int {
        return mItems.size
    }

    override fun getItem(position: Int): Any? {
        return if (position >= 0 && position < mItems.size) mItems[position] else null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    private fun formatDescription(item: ScheduleItem): String {
        val description = StringBuilder()
        description.append(TimeUtils.formatShortTime(mContext, Date(item.startTime)))
        if (Config.Tags.SPECIAL_KEYNOTE != item.mainTag) {
            description.append(" - ")
            description.append(TimeUtils.formatShortTime(mContext, Date(item.endTime)))
        }
        if (!TextUtils.isEmpty(item.room)) {
            description.append(" / ")
            description.append(item.room)
        }
        return description.toString()
    }

    private val mUriOnClickListener = View.OnClickListener { v ->
        val tag = v.getTag(R.id.myschedule_uri_tagkey)
        if (tag != null && tag is Uri) {
// ANALYTICS EVENT: Select a slot on My Agenda
            // Contains: URI indicating session ID or time interval of slot
            AnalyticsHelper.sendEvent("My Schedule", "selectslot", tag.toString())
            mContext.startActivity(Intent(Intent.ACTION_VIEW, tag))
        }
    }

    private fun setUriClickable(view: View, uri: Uri) {
        view.setTag(R.id.myschedule_uri_tagkey, uri)
        view.setOnClickListener(mUriOnClickListener)
        view.setBackgroundResource(mSelectableItemBackground)
    }

    /**
     * Enforces right-alignment to all the TextViews in the `holder`. This is not necessary if
     * all the data is localized in the targeted RTL language, but as we will not be able to
     * localize the conference data, we hack it.

     * @param holder The [ViewHolder] of the list item.
     */
    @SuppressLint("RtlHardcoded")
    private fun adjustForRtl(holder: ViewHolder) {
        if (mIsRtl) {
            holder.startTime!!.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            holder.title!!.gravity = Gravity.RIGHT
            holder.description!!.gravity = Gravity.RIGHT
            holder.browse!!.gravity = Gravity.RIGHT
            android.util.Log.d(TAG, "Gravity right")
        }
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        var view = view
        if (mImageLoader == null) {
            mImageLoader = ImageLoader(mContext)
        }

        val holder: ViewHolder
        // Create a new view if it is not ready yet.
        if (view == null) {
            view = LayoutInflater.from(mContext).inflate(R.layout.my_schedule_item, parent, false)
            holder = ViewHolder()
            holder.startTime = view!!.findViewById(R.id.start_time) as TextView
            holder.icon = view.findViewById(R.id.icon) as ImageView
            holder.title = view.findViewById(R.id.slot_title) as TextView
            holder.description = view.findViewById(R.id.slot_description) as TextView
            holder.browse = view.findViewById(R.id.browse_sessions) as TextView
            holder.feedback = view.findViewById(R.id.give_feedback_button) as Button
            holder.separator = view.findViewById(R.id.separator)
            holder.touchArea = view.findViewById(R.id.touch_area)
            holder.live = view.findViewById(R.id.live_now_badge)
            view.tag = holder
            // Typeface
            mLUtils.setMediumTypeface(holder.startTime!!)
            mLUtils.setMediumTypeface(holder.browse!!)
            mLUtils.setMediumTypeface(holder.title!!)
            adjustForRtl(holder)
        } else {
            holder = view.tag as ViewHolder
            // Clear event listeners
            clearClickable(view)
            clearClickable(holder.startTime!!)
            clearClickable(holder.touchArea!!)
            //Make sure it doesn't retain conflict coloring
            holder.description!!.setTextColor(mHourColorDefault)
        }

        if (position < 0 || position >= mItems.size) {
            LOGE(TAG, "Invalid view position passed to MyScheduleAdapter: " + position)
            return view
        }
        val item = mItems[position]
        val nextItem = if (position < mItems.size - 1) mItems[position + 1] else null

        val now = UIUtils.getCurrentTime(view.context)
        val isNowPlaying = item.startTime <= now && now <= item.endTime && item.type == ScheduleItem.SESSION
        val isPastDuringConference = item.endTime <= now && now < Config.CONFERENCE_END_MILLIS

        if (isPastDuringConference) {
            view.setBackgroundColor(mColorBackgroundPast)
            holder.startTime!!.setTextColor(mHourColorPast)
            holder.title!!.setTextColor(mTitleColorPast)
            holder.description!!.visibility = View.GONE
            holder.icon!!.setColorFilter(mIconColorPast)
        } else {
            view.setBackgroundColor(mColorBackgroundDefault)
            holder.startTime!!.setTextColor(mHourColorDefault)
            holder.title!!.setTextColor(mTitleColorDefault)
            holder.description!!.visibility = View.VISIBLE
            holder.icon!!.setColorFilter(mIconColorDefault)
        }

        holder.startTime!!.text = TimeUtils.formatShortTime(mContext, Date(item.startTime))

        // show or hide the "LIVE NOW" badge
        holder.live!!.visibility = if (0 != item.flags and ScheduleItem.FLAG_HAS_LIVESTREAM && isNowPlaying)
            View.VISIBLE
        else
            View.GONE

        holder.touchArea!!.setTag(R.id.myschedule_uri_tagkey, null)
        if (item.type == ScheduleItem.FREE) {
            holder.startTime!!.visibility = View.VISIBLE
            holder.icon!!.setImageResource(R.drawable.ic_browse)
            holder.feedback!!.visibility = View.GONE
            holder.title!!.visibility = View.GONE
            holder.browse!!.visibility = View.VISIBLE
            setUriClickable(view, ScheduleContract.Sessions.buildUnscheduledSessionsInInterval(
                    item.startTime, item.endTime))
            holder.description!!.visibility = View.GONE
        } else if (item.type == ScheduleItem.BREAK) {
            holder.startTime!!.visibility = View.VISIBLE
            holder.feedback!!.visibility = View.GONE
            holder.title!!.visibility = View.VISIBLE
            holder.title!!.text = item.title
            holder.icon!!.setImageResource(UIUtils.getBreakIcon(item.title))
            holder.browse!!.visibility = View.GONE
            holder.description!!.text = formatDescription(item)
        } else if (item.type == ScheduleItem.SESSION) {
            if (holder.feedback != null) {
                var showFeedbackButton = !item.hasGivenFeedback
                // Can't use isPastDuringConference because we want to show feedback after the
                // conference too.
                if (showFeedbackButton) {
                    if (item.endTime > now) {
                        // Session hasn't finished yet, don't show button.
                        showFeedbackButton = false
                    }
                }
                holder.feedback!!.visibility = if (showFeedbackButton) View.VISIBLE else View.GONE
                holder.feedback!!.setOnClickListener {
                    // ANALYTICS EVENT: Click on the "Send Feedback" action from Schedule page.
                    // Contains: The session title.
                    AnalyticsHelper.sendEvent("My Schedule", "Feedback", item.title)
                    val feedbackIntent = Intent(Intent.ACTION_VIEW,
                            ScheduleContract.Sessions.buildSessionUri(item.sessionId),
                            mContext, SessionFeedbackActivity::class.java)
                    mContext.startActivity(feedbackIntent)
                }
            }
            holder.title!!.visibility = View.VISIBLE
            holder.title!!.text = item.title
            holder.browse!!.visibility = View.GONE
            holder.icon!!.setImageResource(UIUtils.getSessionIcon(item.sessionType))

            val sessionUri = ScheduleContract.Sessions.buildSessionUri(item.sessionId)
            if (0 != item.flags and ScheduleItem.FLAG_CONFLICTS_WITH_PREVIOUS) {
                holder.startTime!!.visibility = View.GONE
                holder.description!!.setTextColor(mColorConflict)
                setUriClickable(holder.touchArea!!, sessionUri)
            } else {
                holder.startTime!!.visibility = View.VISIBLE
                setUriClickable(holder.startTime!!,
                        ScheduleContract.Sessions.buildUnscheduledSessionsInInterval(
                                item.startTime, item.endTime))
                // Padding fix needed for KitKat 4.4. (padding gets removed by setting the background)
                holder.startTime!!.setPadding(
                        mContext.resources.getDimension(R.dimen.keyline_2).toInt(), 0,
                        mContext.resources.getDimension(R.dimen.keyline_2).toInt(), 0)
                setUriClickable(holder.touchArea!!, sessionUri)
                if (0 != item.flags and ScheduleItem.FLAG_CONFLICTS_WITH_NEXT) {
                    holder.description!!.setTextColor(mColorConflict)
                }
            }
            holder.description!!.text = formatDescription(item)
        } else {
            LOGE(TAG, "Invalid item type in MyScheduleAdapter: " + item.type)
        }

        holder.separator!!.visibility = if (nextItem == null || 0 != item.flags and ScheduleItem.FLAG_CONFLICTS_WITH_NEXT)
            View.GONE
        else
            View.VISIBLE

        if (position == 0) { // First item
            view.setPadding(0, mListSpacing, 0, 0)
        } else if (nextItem == null) { // Last item
            view.setPadding(0, 0, 0, mListSpacing)
        } else {
            view.setPadding(0, 0, 0, 0)
        }

        return view
    }

    override fun getItemViewType(position: Int): Int {
        return 0
    }


    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun isEmpty(): Boolean {
        return mItems.isEmpty()
    }

    fun clear() {
        updateItems(null)
    }

    private fun notifyObservers() {
        for (observer in mObservers) {
            observer.onChanged()
        }
    }

    fun forceUpdate() {
        notifyObservers()
    }

    fun updateItems(items: List<ScheduleItem>?) {
        mItems.clear()
        if (items != null) {
            for (item in items) {
                LOGD(TAG, "Adding schedule item: " + item + " start=" + Date(item.startTime))
                mItems.add(item.clone() as ScheduleItem)
            }
        }
        notifyObservers()
    }

    override fun onMovedToScrapHeap(view: View) {
    }

    private class ViewHolder {
        var startTime: TextView? = null
        var icon: ImageView? = null
        var title: TextView? = null
        var description: TextView? = null
        var feedback: Button? = null
        var browse: TextView? = null
        var live: View? = null
        var separator: View? = null
        var touchArea: View? = null
    }

    companion object {
        private val TAG = makeLogTag("MyScheduleAdapter")

        private fun clearClickable(view: View) {
            view.setOnClickListener(null)
            view.setBackgroundResource(0)
            view.isClickable = false
        }
    }

}
