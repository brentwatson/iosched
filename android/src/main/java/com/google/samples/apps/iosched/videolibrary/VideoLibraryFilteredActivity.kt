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

package com.google.samples.apps.iosched.videolibrary

import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.ui.widget.CollectionView
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.google.samples.apps.iosched.videolibrary.VideoLibraryModel.VideoLibraryQueryEnum
import com.google.samples.apps.iosched.videolibrary.VideoLibraryModel.VideoLibraryUserActionEnum

/**
 * This Activity displays all the videos of past Google I/O sessions. You can also filter them per
 * year and/or topics.

 * You can set the initial filter when launching this activity by adding The Topic and/or year to
 * the extras. For this use the `KEY_FILTER_TOPIC` and `KEY_FILTER_YEAR` keys.
 */
class VideoLibraryFilteredActivity : BaseActivity(), Toolbar.OnMenuItemClickListener {
    private var mDrawerLayout: DrawerLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the initial filter values from the intent call.
        val extras = intent.extras
        setTitle(R.string.title_video_library)
        var topicFilter = VideoLibraryModel.ALL_TOPICS
        var yearFilter = VideoLibraryModel.ALL_YEARS
        if (extras != null) {
            topicFilter = extras.getString(KEY_FILTER_TOPIC, VideoLibraryModel.ALL_TOPICS)
            yearFilter = extras.getInt(KEY_FILTER_YEAR, VideoLibraryModel.ALL_YEARS)
        }

        // Instantiate a new model with initial filter values from the intent call.
        val model = VideoLibraryModel(applicationContext, this)
        model.selectedTopic = topicFilter
        model.selectedYear = yearFilter

        setContentView(R.layout.video_library_filtered_act)

        addPresenterFragment(R.id.video_library_frag, model, VideoLibraryQueryEnum.values(),
                VideoLibraryUserActionEnum.values())

        // ANALYTICS EVENT: View the Filtered Video Library screen
        // Contains: Nothing (Page name is a constant)
        AnalyticsHelper.sendScreenView(SCREEN_LABEL)
        LOGD("Tracker", SCREEN_LABEL)

        registerHideableHeaderView(findViewById(R.id.headerbar))

        // Add the back button to the toolbar.
        val toolbar = actionBarToolbar
        toolbar.setNavigationIcon(R.drawable.ic_up)
        toolbar.setNavigationContentDescription(R.string.close_and_go_back)
        toolbar.setNavigationOnClickListener { BaseActivity.navigateUpOrBack(this@VideoLibraryFilteredActivity, null) }
        mDrawerLayout = findViewById(R.id.drawer_layout) as DrawerLayout
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        // Add the filter button to the toolbar.
        val toolbar = actionBarToolbar
        toolbar.inflateMenu(R.menu.video_library_filtered)
        toolbar.setOnMenuItemClickListener(this)
        return true
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.menu_filter -> {
                LOGD(TAG, "Clicking Filter menu button on FilteredVideoLib.")
                mDrawerLayout!!.openDrawer(GravityCompat.END)

                return true
            }
        }

        return false
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        enableActionBarAutoHide(findViewById(R.id.videos_collection_view) as CollectionView)
    }

    override fun onActionBarAutoShowOrHide(shown: Boolean) {
        super.onActionBarAutoShowOrHide(shown)
        val frame = findViewById(R.id.main_content) as DrawShadowFrameLayout
        frame.setShadowVisible(shown, shown)
    }

    override fun onBackPressed() {
        if (mDrawerLayout != null && mDrawerLayout!!.isDrawerOpen(GravityCompat.END)) {
            mDrawerLayout!!.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }

    companion object {

        private val TAG = makeLogTag(VideoLibraryFilteredActivity::class.java)

        private val SCREEN_LABEL = "Filtered Video Library"

        val KEY_FILTER_TOPIC = "com.google.samples.apps.iosched.KEY_FILTER_TOPIC"

        val KEY_FILTER_YEAR = "com.google.samples.apps.iosched.KEY_FILTER_YEAR"
    }
}
