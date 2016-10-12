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

package com.google.samples.apps.iosched.explore

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.explore.ExploreModel.ExploreQueryEnum
import com.google.samples.apps.iosched.explore.ExploreModel.ExploreUserActionEnum
import com.google.samples.apps.iosched.explore.data.ItemGroup
import com.google.samples.apps.iosched.explore.data.LiveStreamData
import com.google.samples.apps.iosched.explore.data.SessionData
import com.google.samples.apps.iosched.framework.QueryEnum
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.session.SessionDetailActivity
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.ui.SearchActivity
import com.google.samples.apps.iosched.ui.widget.CollectionView
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.util.LogUtils.*
import com.google.samples.apps.iosched.util.UIUtils

/**
 * Display a summary of what is happening at Google I/O this year. Theme and topic cards are
 * displayed based on the session data. Conference messages are also displayed as cards..
 */
class ExploreIOActivity : BaseActivity(), Toolbar.OnMenuItemClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.explore_io_act)
        addPresenterFragment(
                R.id.explore_library_frag,
                ExploreModel(
                        applicationContext),
                arrayOf<QueryEnum>(ExploreQueryEnum.SESSIONS, ExploreQueryEnum.TAGS),
                arrayOf(ExploreUserActionEnum.RELOAD))

        // ANALYTICS SCREEN: View the Explore I/O screen
        // Contains: Nothing (Page name is a constant)
        AnalyticsHelper.sendScreenView(SCREEN_LABEL)

        registerHideableHeaderView(findViewById(R.id.headerbar))
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        enableActionBarAutoHide(findViewById(R.id.explore_collection_view) as CollectionView)
    }

    override fun getSelfNavDrawerItem(): Int {
        return BaseActivity.NAVDRAWER_ITEM_EXPLORE
    }

    override fun onActionBarAutoShowOrHide(shown: Boolean) {
        super.onActionBarAutoShowOrHide(shown)
        val frame = findViewById(R.id.main_content) as DrawShadowFrameLayout
        frame.setShadowVisible(shown, shown)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        // Add the search button to the toolbar.
        val toolbar = actionBarToolbar
        toolbar.inflateMenu(R.menu.explore_io_menu)
        toolbar.setOnMenuItemClickListener(this)
        return true
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.menu_search -> {
                startActivity(Intent(this, SearchActivity::class.java))
                return true
            }
        }
        return false
    }

    fun sessionDetailItemClicked(viewClicked: View?) {
        LOGD(TAG, "clicked: " + viewClicked + " " +
                if (viewClicked != null) viewClicked.tag else "")
        var tag: Any? = null
        if (viewClicked != null) {
            tag = viewClicked.tag
        }
        if (tag is SessionData) {
            val sessionData = viewClicked!!.tag as SessionData
            if (!TextUtils.isEmpty(sessionData.sessionId)) {
                val intent = Intent(applicationContext, SessionDetailActivity::class.java)
                val sessionUri = ScheduleContract.Sessions.buildSessionUri(sessionData.sessionId)
                intent.data = sessionUri
                startActivity(intent)
            } else {
                LOGE(TAG, "Theme item clicked but session data was null:" + sessionData)
                Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cardHeaderClicked(viewClicked: View?) {
        LOGD(TAG, "clicked: " + viewClicked + " " +
                if (viewClicked != null) viewClicked.tag else "")
        val moreButton = viewClicked!!.findViewById(android.R.id.button1)
        val tag = moreButton?.tag
        val intent = Intent(applicationContext, ExploreSessionsActivity::class.java)
        if (tag is LiveStreamData) {
            intent.data = ScheduleContract.Sessions.buildSessionsAfterUri(UIUtils.getCurrentTime(this))
            intent.putExtra(ExploreSessionsActivity.EXTRA_SHOW_LIVE_STREAM_SESSIONS, true)
        } else if (tag is ItemGroup) {
            intent.putExtra(ExploreSessionsActivity.EXTRA_FILTER_TAG, tag.id)
        }
        startActivity(intent)
    }

    companion object {

        private val TAG = makeLogTag(ExploreIOActivity::class.java)

        private val SCREEN_LABEL = "Explore I/O"
    }
}
