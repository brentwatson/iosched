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

package com.google.samples.apps.iosched.explore

import android.app.LoaderManager
import android.content.Context
import android.content.Intent
import android.content.Loader
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.view.*
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.model.TagMetadata
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.ui.SearchActivity
import com.google.samples.apps.iosched.ui.widget.CollectionView
import com.google.samples.apps.iosched.ui.widget.CollectionView.Inventory
import com.google.samples.apps.iosched.ui.widget.CollectionView.InventoryGroup
import com.google.samples.apps.iosched.ui.widget.CollectionViewCallbacks
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.google.samples.apps.iosched.util.UIUtils

/**
 * This activity displays all sessions based on the selected filters.
 *
 *
 * It can either be invoked with specific filters or the user can choose the filters
 * to use from the alt_nav_bar.
 */
class ExploreSessionsActivity : BaseActivity(), Toolbar.OnMenuItemClickListener, LoaderManager.LoaderCallbacks<Cursor> {

    private var mDrawerCollectionView: CollectionView? = null
    private var mDrawerLayout: DrawerLayout? = null

    private var mTagMetadata: TagMetadata? = null
    private var mTagFilterHolder: TagFilterHolder? = null
    // Keep track of the current URI. This can diverge from Intent.getData() if the user
    // dismisses a particular timeslot. At that point, the Activity switches the mode
    // as well as the Uri used.
    private var mCurrentUri: Uri? = null

    // The OnClickListener for the Switch widgets on the navigation filter.
    private val mDrawerItemCheckBoxClickListener = View.OnClickListener { v ->
        val isChecked = (v as CheckBox).isChecked
        val theTag = v.getTag() as TagMetadata.Tag
        LOGD(TAG, "Checkbox with tag: " + theTag.name + " isChecked => " + isChecked)
        if (isChecked) {
            mTagFilterHolder!!.add(theTag.id, theTag.category)
        } else {
            mTagFilterHolder!!.remove(theTag.id, theTag.category)
        }
        reloadFragment()
    }
    private var mFragment: ExploreSessionsFragment? = null
    private var mMode: Int = 0
    private var mTimeSlotLayout: View? = null
    private var mTimeSlotDivider: View? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.explore_sessions_act)

        mDrawerLayout = findViewById(R.id.drawer_layout) as DrawerLayout
        mDrawerCollectionView = findViewById(R.id.drawer_collection_view) as CollectionView
        mTimeSlotLayout = findViewById(R.id.timeslot_view)
        mTimeSlotDivider = findViewById(R.id.timeslot_divider)
        val timeSlotTextView = findViewById(R.id.timeslot) as TextView
        val dismissTimeSlotButton = findViewById(R.id.close_timeslot) as ImageButton
        registerHideableHeaderView(findViewById(R.id.headerbar))

        mDrawerLayout!!.setDrawerShadow(R.drawable.drawer_shadow_flipped, GravityCompat.END)

        mFragment = fragmentManager.findFragmentById(R.id.explore_sessions_frag) as ExploreSessionsFragment

        if (savedInstanceState != null) {

            mTagFilterHolder = savedInstanceState.getParcelable<TagFilterHolder>(STATE_FILTER_TAGS)
            mCurrentUri = savedInstanceState.getParcelable<Uri>(STATE_CURRENT_URI)

        } else if (intent != null) {
            mCurrentUri = intent.data
        }

        // Build the tag URI
        val interval = ScheduleContract.Sessions.getInterval(mCurrentUri)
        if (interval != null) {
            mMode = MODE_TIME_FIT

            val title = getString(R.string.explore_sessions_time_slot_title,
                    getString(R.string.explore_sessions_show_day_n,
                            UIUtils.startTimeToDayIndex(interval[0])),
                    UIUtils.formatTime(interval[0], this))
            setTitle(title)

            mTimeSlotLayout!!.visibility = View.VISIBLE
            mTimeSlotDivider!!.visibility = View.VISIBLE
            timeSlotTextView.text = title
            dismissTimeSlotButton.setOnClickListener {
                mTimeSlotLayout!!.visibility = View.GONE
                mTimeSlotDivider!!.visibility = View.GONE
                mMode = MODE_EXPLORE
                mCurrentUri = null
                reloadFragment()
            }
        } else {
            mMode = MODE_EXPLORE
        }

        // Add the back button to the toolbar.
        val toolbar = actionBarToolbar
        toolbar.setNavigationIcon(R.drawable.ic_up)
        toolbar.setNavigationContentDescription(R.string.close_and_go_back)
        toolbar.setNavigationOnClickListener { BaseActivity.navigateUpOrBack(this@ExploreSessionsActivity, null) }

        // Start loading the tag metadata. This will in turn call the fragment with the
        // correct arguments.
        loaderManager.initLoader(TAG_METADATA_TOKEN, null, this)

        // ANALYTICS SCREEN: View the Explore Sessions screen
        // Contains: Nothing (Page name is a constant)
        AnalyticsHelper.sendScreenView(SCREEN_LABEL)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        // Add the filter & search buttons to the toolbar.
        val toolbar = actionBarToolbar
        toolbar.inflateMenu(R.menu.explore_sessions_filtered)
        toolbar.setOnMenuItemClickListener(this)
        return true
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_FILTER_TAGS, mTagFilterHolder)
        outState.putParcelable(STATE_CURRENT_URI, mCurrentUri)
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.menu_filter -> {
                mDrawerLayout!!.openDrawer(GravityCompat.END)
                return true
            }
            R.id.menu_search -> {
                // ANALYTICS EVENT: Click the search button on the ExploreSessions screen
                // Contains: No data (Just that a search occurred, no search term)
                AnalyticsHelper.sendEvent(SCREEN_LABEL, "launchsearch", "")
                startActivity(Intent(this, SearchActivity::class.java))
                return true
            }
        }
        return false
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        enableActionBarAutoHide(findViewById(R.id.collection_view) as CollectionView)
    }

    override fun onActionBarAutoShowOrHide(shown: Boolean) {
        super.onActionBarAutoShowOrHide(shown)
        val frame = findViewById(R.id.main_content) as DrawShadowFrameLayout
        frame.setShadowVisible(shown, shown)
    }

    override fun onCreateLoader(id: Int, args: Bundle): Loader<Cursor>? {
        if (id == TAG_METADATA_TOKEN) {
            return TagMetadata.createCursorLoader(this)
        }
        return null
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor) {
        when (loader.id) {
            TAG_METADATA_TOKEN -> {
                mTagMetadata = TagMetadata(cursor)
                onTagMetadataLoaded()
            }
            else -> cursor.close()
        }
    }

    override fun onBackPressed() {
        if (mDrawerLayout != null && mDrawerLayout!!.isDrawerOpen(GravityCompat.END)) {
            mDrawerLayout!!.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }

    private fun onTagMetadataLoaded() {
        if (mTagFilterHolder == null) {
            // Use the Intent Extras to set up the TagFilterHolder
            mTagFilterHolder = TagFilterHolder()

            val tag = intent.getStringExtra(EXTRA_FILTER_TAG)
            val userTag = mTagMetadata!!.getTag(tag)
            val userTagCategory = userTag?.category
            if (tag != null && userTagCategory != null) {
                mTagFilterHolder!!.add(tag, userTagCategory)
            }

            mTagFilterHolder!!.isShowLiveStreamedSessions = intent.getBooleanExtra(EXTRA_SHOW_LIVE_STREAM_SESSIONS, false)

            // update the selected filters using the following logic:
            // a) For onsite attendees, we should default to showing all 'types'
            // (i.e. Sessions, code labs, sandbox, misc).
            if (SettingsUtils.isAttendeeAtVenue(this)) {
                val tags = mTagMetadata!!.getTagsInCategory(Config.Tags.CATEGORY_TYPE)
                // Here we only add all 'types' if the user has not explicitly selected
                // one of the category_type tags.
                if (tags != null && !TextUtils.equals(userTagCategory, Config.Tags.CATEGORY_TYPE)) {
                    for (theTag in tags) {
                        mTagFilterHolder!!.add(theTag.id, theTag.category)
                    }
                }
            } else {
                // b) For remote users, default to only showing Sessions that are Live streamed.
                val theTag = mTagMetadata!!.getTag(Config.Tags.SESSIONS)
                if (!TextUtils.equals(theTag.category, userTagCategory)) {
                    mTagFilterHolder!!.add(theTag.id, theTag.category)
                }
                mTagFilterHolder!!.isShowLiveStreamedSessions = true
            }
        }
        reloadFragment()
        val tagAdapter = TagAdapter()
        mDrawerCollectionView!!.setCollectionAdapter(tagAdapter)
        mDrawerCollectionView!!.updateInventory(tagAdapter.inventory)
    }

    /**
     * Set the activity title to be that of the selected tag name.
     * If the user chosen tag's category is present in the filter and there is a single tag
     * with that category then set the title to the specific tag name else
     * set the title to R.string.explore.
     */
    private fun setActivityTitle() {
        if (mMode == MODE_EXPLORE && mTagMetadata != null) {
            val tag = intent.getStringExtra(EXTRA_FILTER_TAG)
            val titleTag = if (tag == null) null else mTagMetadata!!.getTag(tag)
            var title: String? = null
            if (titleTag != null && mTagFilterHolder!!.getCountByCategory(titleTag.category) == 1) {
                for (tagId in mTagFilterHolder!!.selectedFilters) {
                    val theTag = mTagMetadata!!.getTag(tagId)
                    if (TextUtils.equals(titleTag.category, theTag.category)) {
                        title = theTag.name
                    }
                }
            }
            setTitle(if (title == null) getString(R.string.title_explore) else title)
        }
    }

    private fun reloadFragment() {
        // Build the tag URI
        var uri = mCurrentUri

        if (uri == null) {
            uri = ScheduleContract.Sessions.buildCategoryTagFilterUri(
                    ScheduleContract.Sessions.CONTENT_URI,
                    mTagFilterHolder!!.toStringArray(),
                    mTagFilterHolder!!.categoryCount)
        } else { // build a uri with the specific filters
            uri = ScheduleContract.Sessions.buildCategoryTagFilterUri(uri,
                    mTagFilterHolder!!.toStringArray(),
                    mTagFilterHolder!!.categoryCount)
        }
        setActivityTitle()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.putExtra(ExploreSessionsFragment.EXTRA_SHOW_LIVESTREAMED_SESSIONS,
                mTagFilterHolder!!.isShowLiveStreamedSessions)

        LOGD(TAG, "Reloading fragment with categories " + mTagFilterHolder!!.categoryCount +
                " uri: " + uri +
                " showLiveStreamedEvents: " + mTagFilterHolder!!.isShowLiveStreamedSessions)

        mFragment!!.reloadFromArguments(BaseActivity.intentToFragmentArguments(intent))
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {

    }

    /**
     * Adapter responsible for showing the alt_nav with the tags from
     * [com.google.samples.apps.iosched.model.TagMetadata]
     */
    private inner class TagAdapter : CollectionViewCallbacks {

        /**
         * Returns a new instance of [Inventory]. It always contains three
         * [InventoryGroup] groups.
         *
         *  * Themes group containing themes such as Develop, Distribute etc.
         *  * Types group containing tags for all types of sessions, codelabs etc.
         *  * Topics group containing tags for specific topics such as Android, Cloud etc.
         *

         * @return A new instance of [Inventory].
         */
        // We need to add the Live streamed section after the Type category
        val inventory: Inventory
            get() {
                val themes = mTagMetadata!!.getTagsInCategory(Config.Tags.CATEGORY_THEME)
                val inventory = Inventory()

                val themeGroup = InventoryGroup(GROUP_TOPIC_TYPE_OR_THEME).setDisplayCols(1).setDataIndexStart(0).setShowHeader(false)

                if (themes != null && themes.size > 0) {
                    for (type in themes) {
                        themeGroup.addItemWithTag(type)
                    }
                    inventory.addGroup(themeGroup)
                }

                val typesGroup = InventoryGroup(GROUP_TOPIC_TYPE_OR_THEME).setDataIndexStart(0).setShowHeader(true)
                val data = mTagMetadata!!.getTagsInCategory(Config.Tags.CATEGORY_TYPE)

                if (data != null && data.size > 0) {
                    for (tag in data) {
                        typesGroup.addItemWithTag(tag)
                    }
                    inventory.addGroup(typesGroup)
                }
                val liveStreamGroup = InventoryGroup(GROUP_LIVE_STREAM).setDataIndexStart(0).setShowHeader(true).addItemWithTag("Livestreamed")
                inventory.addGroup(liveStreamGroup)

                val topicsGroup = InventoryGroup(GROUP_TOPIC_TYPE_OR_THEME).setDataIndexStart(0).setShowHeader(true)

                val topics = mTagMetadata!!.getTagsInCategory(Config.Tags.CATEGORY_TOPIC)
                if (topics != null && topics.size > 0) {
                    for (topic in topics) {
                        topicsGroup.addItemWithTag(topic)
                    }
                    inventory.addGroup(topicsGroup)
                }

                return inventory
            }

        override fun newCollectionHeaderView(context: Context, groupId: Int, parent: ViewGroup): View {
            val view = LayoutInflater.from(context).inflate(R.layout.explore_sessions_list_item_alt_header, parent, false)
            // We do not want the divider/header to be read out by TalkBack, so
            // inform the view that this is not important for accessibility.
            UIUtils.setAccessibilityIgnore(view)
            return view
        }

        override fun bindCollectionHeaderView(context: Context, view: View, groupId: Int,
                                              headerLabel: String, headerTag: Any) {
        }

        override fun newCollectionItemView(context: Context, groupId: Int, parent: ViewGroup): View {
            return LayoutInflater.from(context).inflate(if (groupId == GROUP_LIVE_STREAM)
                R.layout.explore_sessions_list_item_livestream_alt_drawer
            else
                R.layout.explore_sessions_list_item_alt_drawer, parent, false)
        }

        override fun bindCollectionItemView(context: Context, view: View, groupId: Int,
                                            indexInGroup: Int, dataIndex: Int, tag: Any) {
            val checkBox = view.findViewById(R.id.filter_checkbox) as CheckBox
            if (groupId == GROUP_LIVE_STREAM) {
                checkBox.isChecked = mTagFilterHolder!!.isShowLiveStreamedSessions
                checkBox.setOnClickListener {
                    mTagFilterHolder!!.isShowLiveStreamedSessions = checkBox.isChecked
                    // update the fragment to reflect the changes.
                    reloadFragment()
                }

            } else {
                val theTag = tag as TagMetadata.Tag
                if (theTag != null) {
                    (view.findViewById(R.id.text_view) as TextView).text = theTag.name
                    // set the original checked state by looking up our tags.
                    checkBox.isChecked = mTagFilterHolder!!.contains(theTag.id)
                    checkBox.tag = theTag
                    checkBox.setOnClickListener(mDrawerItemCheckBoxClickListener)
                }
            }
            view.setOnClickListener { checkBox.performClick() }
        }
    }

    companion object {

        val EXTRA_FILTER_TAG = "com.google.samples.apps.iosched.explore.EXTRA_FILTER_TAG"
        val EXTRA_SHOW_LIVE_STREAM_SESSIONS = "com.google.samples.apps.iosched.explore.EXTRA_SHOW_LIVE_STREAM_SESSIONS"

        // The saved instance state filters
        private val STATE_FILTER_TAGS = "com.google.samples.apps.iosched.explore.STATE_FILTER_TAGS"
        private val STATE_CURRENT_URI = "com.google.samples.apps.iosched.explore.STATE_CURRENT_URI"

        private val SCREEN_LABEL = "ExploreSessions"

        private val TAG = makeLogTag(ExploreSessionsActivity::class.java)
        private val TAG_METADATA_TOKEN = 0x8

        private val GROUP_TOPIC_TYPE_OR_THEME = 0
        private val GROUP_LIVE_STREAM = 1

        private val MODE_TIME_FIT = 1
        private val MODE_EXPLORE = 2
    }
}
