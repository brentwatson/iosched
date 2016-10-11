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

package com.google.samples.apps.iosched.myschedule

import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.model.ScheduleHelper
import com.google.samples.apps.iosched.model.ScheduleItem
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.session.SessionDetailActivity
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.util.ThrottledContentObserver
import com.google.samples.apps.iosched.util.TimeUtils
import com.google.samples.apps.iosched.util.UIUtils

import android.app.AlertDialog
import android.app.Fragment
import android.app.FragmentManager
import android.app.ListFragment
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.support.design.widget.TabLayout
import android.support.v13.app.FragmentPagerAdapter
import android.support.v4.view.ViewCompat
import android.support.v4.view.ViewPager
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.widget.ScrollView
import android.widget.TextView

import java.lang.ref.WeakReference
import java.util.Arrays
import java.util.Date
import java.util.HashSet

import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.LOGE
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

class MyScheduleActivity : BaseActivity(), MyScheduleFragment.Listener {

    // If true, we are in the wide (tablet) mode where we show conference days side by side;
    // if false, we are in narrow (handset) mode where we use a ViewPager and show only
    // one conference day at a time.
    private var mWideMode = false

    // If in wide mode, we have MyScheduleView widgets showing each day
    private val mMyScheduleViewWide = arrayOfNulls<MyScheduleView>(2)

    // The adapters that serves as the source of data for the UI, indicating the available
    // items. We have one adapter per day of the conference. When we push new data into these
    // adapters, the corresponding UIs update automatically.
    private val mScheduleAdapters = arrayOfNulls<MyScheduleAdapter>(Config.CONFERENCE_DAYS.size)

    // If non-null, the Activity will show day-0 tab (or column).
    private var mDayZeroAdapter: MyScheduleAdapter? = null

    // The ScheduleHelper is responsible for feeding data in a format suitable to the Adapter.
    private val mDataHelper: ScheduleHelper

    // View pager and adapter (for narrow mode)
    internal var mViewPager: ViewPager? = null
    internal var mViewPagerAdapter: OurViewPagerAdapter? = null
    internal var mTabLayout: TabLayout? = null
    internal var mScrollViewWide: ScrollView? = null

    // Login failed butter bar
    internal var mButterBar: View? = null

    internal var mDestroyed = false

    private val mMyScheduleFragments = HashSet<MyScheduleFragment>()

    private var mShowedAnnouncementDialog = false

    private val baseTabViewId = 12345

    private val mViewPagerScrollState = ViewPager.SCROLL_STATE_IDLE

    init {
        mDataHelper = ScheduleHelper(this)
    }

    override fun getSelfNavDrawerItem(): Int {
        return BaseActivity.NAVDRAWER_ITEM_MY_SCHEDULE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_schedule)

        // Pre-process the intent received to open this activity to determine if it was a deep
        // link to a SessionDetail. Typically you wouldn't use this type of logic, but we need to
        // because of the path of session details page on the website is only /schedule and session
        // ids are part of the query parameters (sid).
        val intent = intent
        if (intent != null && !TextUtils.isEmpty(intent.dataString)) {
            // Check format against website format.
            val intentDataString = intent.dataString
            try {
                val dataUri = Uri.parse(intentDataString)
                val sessionId = dataUri.getQueryParameter("sid")
                if (!TextUtils.isEmpty(sessionId)) {
                    val data = ScheduleContract.Sessions.buildSessionUri(sessionId)
                    val sessionDetailIntent = Intent(this@MyScheduleActivity,
                            SessionDetailActivity::class.java)
                    sessionDetailIntent.data = data
                    startActivity(sessionDetailIntent)
                    finish()
                }
                LOGD(TAG, "SessionId: " + sessionId)
            } catch (exception: Exception) {
                LOGE(TAG, "Data uri existing but wasn't parsable for a session detail deep link")
            }

        }

        // ANALYTICS SCREEN: View the My Schedule screen
        // Contains: Nothing (Page name is a constant)
        AnalyticsHelper.sendScreenView(SCREEN_LABEL)

        mViewPager = findViewById(R.id.view_pager) as ViewPager
        mScrollViewWide = findViewById(R.id.main_content_wide) as ScrollView
        mWideMode = findViewById(R.id.my_schedule_first_day) != null

        if (SettingsUtils.isAttendeeAtVenue(this)) {
            mDayZeroAdapter = MyScheduleAdapter(this, lUtils)
            prepareDayZeroAdapter()
        }

        for (i in Config.CONFERENCE_DAYS.indices) {
            mScheduleAdapters[i] = MyScheduleAdapter(this, lUtils)
        }

        mViewPagerAdapter = OurViewPagerAdapter(fragmentManager)
        mViewPager!!.adapter = mViewPagerAdapter

        if (mWideMode) {
            mMyScheduleViewWide[0] = findViewById(R.id.my_schedule_first_day) as MyScheduleView
            mMyScheduleViewWide[0]!!.setAdapter(mScheduleAdapters[0])
            mMyScheduleViewWide[1] = findViewById(R.id.my_schedule_second_day) as MyScheduleView
            mMyScheduleViewWide[1]!!.setAdapter(mScheduleAdapters[1])

            val firstDayHeaderView = findViewById(R.id.day_label_first_day) as TextView
            val secondDayHeaderView = findViewById(R.id.day_label_second_day) as TextView
            if (firstDayHeaderView != null) {
                firstDayHeaderView.text = getDayName(0)
            }
            if (secondDayHeaderView != null) {
                secondDayHeaderView.text = getDayName(1)
            }

            val zerothDayHeaderView = findViewById(R.id.day_label_zeroth_day) as TextView
            val dayZeroView = findViewById(R.id.my_schedule_zeroth_day) as MyScheduleView
            if (mDayZeroAdapter != null) {
                dayZeroView.setAdapter(mDayZeroAdapter)
                dayZeroView.visibility = View.VISIBLE
                zerothDayHeaderView.text = getDayName(-1)
                zerothDayHeaderView.visibility = View.VISIBLE
            } else {
                dayZeroView.visibility = View.GONE
                zerothDayHeaderView.visibility = View.GONE
            }
        } else {
            // it's PagerAdapter set.
            mTabLayout = findViewById(R.id.sliding_tabs) as TabLayout

            mTabLayout!!.setTabsFromPagerAdapter(mViewPagerAdapter!!)

            mTabLayout!!.setOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    mViewPager!!.setCurrentItem(tab.position, true)
                    val view = findViewById(baseTabViewId + tab.position) as TextView
                    view.contentDescription = getString(R.string.talkback_selected,
                            getString(R.string.a11y_button, tab.text))
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {
                    val view = findViewById(baseTabViewId + tab.position) as TextView
                    view.contentDescription = getString(R.string.a11y_button, tab.text)
                }

                override fun onTabReselected(tab: TabLayout.Tab) {
                    // Do nothing
                }
            })
            mViewPager!!.pageMargin = resources.getDimensionPixelSize(R.dimen.my_schedule_page_margin)
            mViewPager!!.setPageMarginDrawable(R.drawable.page_margin)

            setTabLayoutContentDescriptions()
        }

        mButterBar = findViewById(R.id.butter_bar)
        removeLoginFailed()

        overridePendingTransition(0, 0)
        addDataObservers()
    }

    // This method is an ad-hoc implementation of Day 0.
    private fun prepareDayZeroAdapter() {
        val item = ScheduleItem()
        item.title = "Badge Pick-Up"
        item.startTime = 1432742400000L // 2015/05/27 9:00 AM PST
        item.endTime = 1432782000000L // 2015/05/27 8:00 PM PST
        item.type = ScheduleItem.BREAK
        item.subtitle = "Registration Desk"
        item.room = "Registration Desk"
        item.sessionType = ScheduleItem.SESSION_TYPE_MISC
        mDayZeroAdapter!!.updateItems(Arrays.asList(item))
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (mViewPager != null) {
            val now = UIUtils.getCurrentTime(this)
            selectDay(0)
            for (i in Config.CONFERENCE_DAYS.indices) {
                if (now >= Config.CONFERENCE_DAYS[i][0] && now <= Config.CONFERENCE_DAYS[i][1]) {
                    selectDay(i)
                    break
                }
            }
        }
        setProgressBarTopWhenActionBarShown(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f,
                resources.displayMetrics).toInt())
    }

    private fun selectDay(day: Int) {
        val gap = if (mDayZeroAdapter != null) 1 else 0
        mViewPager!!.currentItem = day + gap
        setTimerToUpdateUI(day + gap)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LOGD(TAG, "onNewIntent, extras " + intent.extras)
        if (intent.hasExtra(EXTRA_DIALOG_MESSAGE)) {
            mShowedAnnouncementDialog = false
            showAnnouncementDialogIfNeeded(intent)
        }
    }

    private fun getDayName(position: Int): String {
        val day1Start = Config.CONFERENCE_DAYS[0][0]
        val day = 1000 * 60 * 60 * 24.toLong()
        return TimeUtils.formatShortDate(this, Date(day1Start + day * position))
    }

    private fun setTabLayoutContentDescriptions() {
        val inflater = layoutInflater
        val gap = if (mDayZeroAdapter == null) 0 else 1
        var i = 0
        val count = mTabLayout!!.tabCount
        while (i < count) {
            val tab = mTabLayout!!.getTabAt(i)
            val view = inflater.inflate(R.layout.tab_my_schedule, mTabLayout, false) as TextView
            view.id = baseTabViewId + i
            view.text = tab!!.text
            if (i == 0) {
                view.contentDescription = getString(R.string.talkback_selected,
                        getString(R.string.a11y_button, tab.text))
            } else {
                view.contentDescription = getString(R.string.a11y_button, tab.text)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                view.announceForAccessibility(
                        getString(R.string.my_schedule_tab_desc_a11y, getDayName(i - gap)))
            }
            tab.customView = view
            i++
        }
    }

    private fun removeLoginFailed() {
        mButterBar?.visibility = View.GONE
        deregisterHideableHeaderView(mButterBar)
    }

    override fun onAuthFailure(accountName: String) {
        super.onAuthFailure(accountName)
        UIUtils.setUpButterBar(mButterBar, getString(R.string.login_failed_text),
                getString(R.string.login_failed_text_retry), View.OnClickListener {
            removeLoginFailed()
            retryAuth()
        })
        registerHideableHeaderView(findViewById(R.id.butter_bar))
    }

    override fun onAccountChangeRequested() {
        super.onAccountChangeRequested()
        removeLoginFailed()
    }

    override fun canSwipeRefreshChildScrollUp(): Boolean {
        if (mWideMode) {
            return ViewCompat.canScrollVertically(mScrollViewWide, -1)
        }

        // Prevent the swipe refresh by returning true here
        if (mViewPagerScrollState == ViewPager.SCROLL_STATE_DRAGGING) {
            return true
        }

        for (fragment in mMyScheduleFragments) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                if (!fragment.userVisibleHint) {
                    continue
                }
            }

            return ViewCompat.canScrollVertically(fragment.listView, -1)
        }

        return false
    }

    public override fun onResume() {
        super.onResume()
        updateData()
        showAnnouncementDialogIfNeeded(intent)
    }

    private fun showAnnouncementDialogIfNeeded(intent: Intent) {
        val title = intent.getStringExtra(EXTRA_DIALOG_TITLE)
        val message = intent.getStringExtra(EXTRA_DIALOG_MESSAGE)

        if (!mShowedAnnouncementDialog && !TextUtils.isEmpty(title) && !TextUtils.isEmpty(message)) {
            LOGD(TAG, "showAnnouncementDialogIfNeeded, title: " + title)
            LOGD(TAG, "showAnnouncementDialogIfNeeded, message: " + message!!)
            val yes = intent.getStringExtra(EXTRA_DIALOG_YES)
            LOGD(TAG, "showAnnouncementDialogIfNeeded, yes: " + yes)
            val no = intent.getStringExtra(EXTRA_DIALOG_NO)
            LOGD(TAG, "showAnnouncementDialogIfNeeded, no: " + no)
            val url = intent.getStringExtra(EXTRA_DIALOG_URL)
            LOGD(TAG, "showAnnouncementDialogIfNeeded, url: " + url)
            val spannable = SpannableString(message ?: "")
            Linkify.addLinks(spannable, Linkify.WEB_URLS)

            val builder = AlertDialog.Builder(this)
            if (!TextUtils.isEmpty(title)) {
                builder.setTitle(title)
            }
            builder.setMessage(spannable)
            if (!TextUtils.isEmpty(no)) {
                builder.setNegativeButton(no) { dialog, which -> dialog.cancel() }
            }
            if (!TextUtils.isEmpty(yes)) {
                builder.setPositiveButton(yes) { dialog, which ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                }
            }
            val dialog = builder.create()
            dialog.show()
            val messageView = dialog.findViewById(android.R.id.message) as TextView
            if (messageView != null) {
                // makes the embedded links in the text clickable, if there are any
                messageView.movementMethod = LinkMovementMethod.getInstance()
            }
            mShowedAnnouncementDialog = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mDestroyed = true
        removeDataObservers()
    }

    protected fun updateData() {
        for (i in Config.CONFERENCE_DAYS.indices) {
            mDataHelper.getScheduleDataAsync(mScheduleAdapters[i],
                    Config.CONFERENCE_DAYS[i][0], Config.CONFERENCE_DAYS[i][1])
        }
    }

    override fun onFragmentViewCreated(fragment: ListFragment) {
        fragment.listView.addHeaderView(
                layoutInflater.inflate(R.layout.reserve_action_bar_space_header_view, null))
        val dayIndex = fragment.arguments.getInt(ARG_CONFERENCE_DAY_INDEX, 0)
        if (dayIndex < 0) {
            fragment.listAdapter = mDayZeroAdapter
            fragment.listView.setRecyclerListener(mDayZeroAdapter)
        } else {
            fragment.listAdapter = mScheduleAdapters[dayIndex]
            fragment.listView.setRecyclerListener(mScheduleAdapters[dayIndex])
        }
    }

    override fun onFragmentAttached(fragment: MyScheduleFragment) {
        mMyScheduleFragments.add(fragment)
    }

    override fun onFragmentDetached(fragment: MyScheduleFragment) {
        mMyScheduleFragments.remove(fragment)
    }

    inner class OurViewPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        override fun getItem(position: Int): Fragment {
            var position = position
            LOGD(TAG, "Creating fragment #" + position)
            if (mDayZeroAdapter != null) {
                position--
            }
            val frag = MyScheduleFragment()
            val args = Bundle()
            args.putInt(ARG_CONFERENCE_DAY_INDEX, position)
            frag.arguments = args
            return frag
        }

        override fun getCount(): Int {
            return Config.CONFERENCE_DAYS.size + if (mDayZeroAdapter == null) 0 else 1
        }

        override fun getPageTitle(position: Int): CharSequence {
            return getDayName(position - if (mDayZeroAdapter == null) 0 else 1)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.my_schedule, menu)
        return true
    }

    protected fun addDataObservers() {
        contentResolver.registerContentObserver(
                ScheduleContract.BASE_CONTENT_URI, true, mObserver)

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sp.registerOnSharedPreferenceChangeListener(mPrefChangeListener)
    }

    fun removeDataObservers() {
        contentResolver.unregisterContentObserver(mObserver)

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sp.unregisterOnSharedPreferenceChangeListener(mPrefChangeListener)
    }

    private val mPrefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        LOGD(TAG, "sharedpreferences key $key changed, maybe reloading data.")
        for (adapter in mScheduleAdapters) {
            if (SettingsUtils.PREF_LOCAL_TIMES == key) {
                adapter?.forceUpdate()
            } else if (SettingsUtils.PREF_ATTENDEE_AT_VENUE == key) {
                updateData()
            }
        }
    }

    private val mObserver = ThrottledContentObserver(
            object : ThrottledContentObserver.Callbacks {
                override fun onThrottledContentObserverFired() {
                    LOGD(TAG, "content may be changed, reloading data")
                    updateData()
                }
            })

    /**
     * If in conference day, redraw the day's UI every @{link #INTERVAL_TO_REDRAW_UI} ms, so
     * that time sensitive widgets, like "now", "ended" and appropriate styles are updated.

     * @param today the index in the conference days array that corresponds to the current day.
     */
    private fun setTimerToUpdateUI(today: Int) {
        UpdateUIRunnable(this, today, Handler()).scheduleNextRun()
    }

    internal fun hasBeenDestroyed(): Boolean {
        return mDestroyed
    }

    internal class UpdateUIRunnable(activity: MyScheduleActivity, val today: Int, val handler: Handler) : Runnable {

        val weakRefToParent: WeakReference<MyScheduleActivity>

        init {
            weakRefToParent = WeakReference(activity)
        }

        fun scheduleNextRun() {
            handler.postDelayed(this, INTERVAL_TO_REDRAW_UI)
        }

        override fun run() {
            val activity = weakRefToParent.get()
            if (activity == null || activity.hasBeenDestroyed()) {
                LOGD(TAG, "Ativity is not valid anymore. Stopping UI Updater")
                return
            }
            LOGD(TAG, "Running MySchedule UI updater (now=" +
                    Date(UIUtils.getCurrentTime(activity)) + ")")
            if (activity.mScheduleAdapters != null
                    && activity.mScheduleAdapters.size > today
                    && activity.mScheduleAdapters[today] != null) {
                try {
                    activity.mScheduleAdapters[today]?.forceUpdate()
                } finally {
                    // schedule again
                    this.scheduleNextRun()
                }
            }
        }
    }

    companion object {

        // Interval that a timer will redraw the UI when in conference day, so that time sensitive
        // widgets, like the "Now" and "Ended" indicators can be properly updated.
        private val INTERVAL_TO_REDRAW_UI = 60 * 1000L

        private val SCREEN_LABEL = "My Schedule"
        private val TAG = makeLogTag(MyScheduleActivity::class.java)

        private val ARG_CONFERENCE_DAY_INDEX = "com.google.samples.apps.iosched.ARG_CONFERENCE_DAY_INDEX"

        val EXTRA_DIALOG_TITLE = "com.google.samples.apps.iosched.EXTRA_DIALOG_TITLE"
        val EXTRA_DIALOG_MESSAGE = "com.google.samples.apps.iosched.EXTRA_DIALOG_MESSAGE"
        val EXTRA_DIALOG_YES = "com.google.samples.apps.iosched.EXTRA_DIALOG_YES"
        val EXTRA_DIALOG_NO = "com.google.samples.apps.iosched.EXTRA_DIALOG_NO"
        val EXTRA_DIALOG_URL = "com.google.samples.apps.iosched.EXTRA_DIALOG_URL"
    }

}
