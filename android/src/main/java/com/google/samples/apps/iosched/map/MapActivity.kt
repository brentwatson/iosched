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

package com.google.samples.apps.iosched.map

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.session.SessionDetailConstants
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

/**
 * Activity that displays a [com.google.samples.apps.iosched.map.MapFragment] and a [ ].
 * Supports 'detached' mode, where the toolbar contains an up navigation option that finishes this
 * Activity. (see [.EXTRA_DETACHED_MODE]
 * Optionally a room can be specified via [.EXTRA_ROOM] that pans the map to its indicated
 * marker.

 * @see com.google.samples.apps.iosched.map.MapInfoFragment.newInstace
 */
class MapActivity : BaseActivity(), MapInfoFragment.Callback, MapFragment.Callbacks {

    private var mDetachedMode: Boolean = false

    private var mMapFragment: MapFragment? = null

    private var mInfoFragment: MapInfoFragment? = null

    private var mInfoContainer: View? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fm = fragmentManager
        mMapFragment = fm.findFragmentByTag("map") as MapFragment

        mDetachedMode = intent.getBooleanExtra(EXTRA_DETACHED_MODE, false)

        if (isFinishing) {
            return
        }

        setContentView(R.layout.map_act)
        mInfoContainer = findViewById(R.id.map_detail_popup)

        // ANALYTICS SCREEN: View the Map screen on a phone
        // Contains: Nothing (Page name is a constant)
        AnalyticsHelper.sendScreenView(SCREEN_LABEL)

        overridePendingTransition(0, 0)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        // Save the mapview state in a separate bundle parameter
        val mapviewState = Bundle()
        mMapFragment!!.onSaveInstanceState(mapviewState)
        outState.putBundle(BUNDLE_STATE_MAPVIEW, mapviewState)
    }


    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (mDetachedMode) {
            val toolbar = actionBarToolbar
            toolbar.setNavigationIcon(R.drawable.ic_up)
            toolbar.setNavigationOnClickListener { finish() }
        }

        if (mMapFragment == null) {
            // Either restore the state of the map or read it from the Intent extras.
            if (savedInstanceState != null) {
                // Restore state from existing bundle
                val previousState = savedInstanceState.getBundle(BUNDLE_STATE_MAPVIEW)
                mMapFragment = MapFragment.newInstance(previousState)
            } else {
                // Get highlight room id (if specified in intent extras)
                val highlightRoomId = if (intent.hasExtra(EXTRA_ROOM))
                    intent.extras.getString(EXTRA_ROOM)
                else
                    null
                mMapFragment = MapFragment.newInstance(highlightRoomId!!)
            }
            fragmentManager.beginTransaction().add(R.id.fragment_container_map, mMapFragment, "map").commit()
        }
        if (mInfoFragment == null) {
            mInfoFragment = MapInfoFragment.newInstace(this)
            fragmentManager.beginTransaction().add(R.id.fragment_container_map_info, mInfoFragment, "mapsheet").commit()
        }

        mDetachedMode = intent.getBooleanExtra(EXTRA_DETACHED_MODE, false)
    }

    override fun onBackPressed() {
        if (mInfoFragment != null && mInfoFragment!!.isExpanded) {
            mInfoFragment!!.minimize()
        } else {
            super.onBackPressed()
        }
    }

    override fun getSelfNavDrawerItem(): Int {
        return if (mDetachedMode) BaseActivity.NAVDRAWER_ITEM_INVALID else BaseActivity.NAVDRAWER_ITEM_MAP
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (intent.getBooleanExtra(EXTRA_DETACHED_MODE, false) && item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Adjust the padding in the map fragment when the info fragment has been resized.
     * The size is self reported from the fragment and has to be adjusted before it can be
     * applied to the map.

     * For [com.google.samples.apps.iosched.map.InlineInfoFragment] (that is only displayed
     * on the left of the screen), the full extend of its container layout (including padding) is
     * passed to the map fragment.
     * For [com.google.samples.apps.iosched.map.SlideableInfoFragment] (that is only
     * displayed
     * at the bottom of the screen), its actual height is passed through to the map.
     */
    override fun onInfoSizeChanged(left: Int, top: Int, right: Int, bottom: Int) {
        if (mMapFragment != null) {
            if (mInfoFragment is InlineInfoFragment) {
                // InlineInfoFragment is shown on the left on tablet layouts.
                // Use the right edge of its containers for padding of the map.
                //TODO: RTL support - compare left and right positioning
                if (right > 0) {
                    mMapFragment!!.setMapInsets(mInfoContainer!!.right, 0, 0, 0)
                } else {
                    mMapFragment!!.setMapInsets(0, 0, 0, 0)
                }
            } else if (mInfoFragment is SlideableInfoFragment) {
                // SlideableInfoFragment is only shown on phone layouts at the bottom of the screen,
                // but only up to 50% of the total height of the screen
                if (top.toFloat() / bottom.toFloat() > SlideableInfoFragment.MAX_PANEL_PADDING_FACTOR) {

                    mMapFragment!!.setMapInsets(0, 0, 0, bottom - top)
                }
                val bottomPadding = Math.min(bottom - top,
                        Math.round(bottom * SlideableInfoFragment.MAX_PANEL_PADDING_FACTOR))
                mMapFragment!!.setMapInsets(0, 0, 0, bottomPadding)
            }
            // F
        }
    }

    override fun onSessionClicked(sessionId: String) {
        // ANALYTICS EVENT: Click on a session in the Maps screen.
        // Contains: The session ID.
        AnalyticsHelper.sendEvent(SCREEN_LABEL, "selectsession", sessionId)

        lUtils.startActivityWithTransition(
                Intent(Intent.ACTION_VIEW,
                        ScheduleContract.Sessions.buildSessionUri(sessionId)),
                null,
                SessionDetailConstants.TRANSITION_NAME_PHOTO)
    }

    override fun onInfoShowMoscone() {
        if (mInfoFragment != null) {
            mInfoFragment!!.showMoscone()
        }
        setTabletInfoVisibility(View.VISIBLE)

    }

    override fun onInfoShowTitle(label: String, icon: Int) {
        if (mInfoFragment != null) {
            mInfoFragment!!.showTitleOnly(icon, label)
        }
        setTabletInfoVisibility(View.VISIBLE)

    }

    override fun onInfoShowSessionlist(roomId: String, roomTitle: String, roomType: Int) {
        if (mInfoFragment != null) {
            mInfoFragment!!.showSessionList(roomId, roomTitle, roomType)
        }
        setTabletInfoVisibility(View.VISIBLE)

    }

    override fun onInfoShowFirstSessionTitle(roomId: String, roomTitle: String, roomType: Int) {
        if (mInfoFragment != null) {
            mInfoFragment!!.showFirstSessionTitle(roomId, roomTitle, roomType)
        }
        setTabletInfoVisibility(View.VISIBLE)
    }

    override fun onInfoHide() {
        if (mInfoFragment != null) {
            mInfoFragment!!.hide()
        }
        setTabletInfoVisibility(View.GONE)
    }

    private fun setTabletInfoVisibility(visibility: Int) {
        val view = findViewById(R.id.map_detail_popup)
        if (view != null) {
            view.visibility = visibility

        }
    }

    companion object {

        private val TAG = makeLogTag(MapActivity::class.java)

        private val SCREEN_LABEL = "Map"

        /**
         * When specified, will automatically point the map to the requested room.
         */
        val EXTRA_ROOM = "com.google.android.iosched.extra.ROOM"

        val EXTRA_DETACHED_MODE = "com.google.samples.apps.iosched.EXTRA_DETACHED_MODE"

        val BUNDLE_STATE_MAPVIEW = "mapview"
    }
}
