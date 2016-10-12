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

package com.google.samples.apps.iosched.map

import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.samples.apps.iosched.R
import com.sothree.slidinguppanel.SlidingUpPanelLayout

/**
 * Map info fragment that uses a [com.sothree.slidinguppanel.SlidingUpPanelLayout] to display
 * its contents.
 * It is designed to be displayed at the bottom of the screen and handles resizing, scrolling and
 * expanding of content itself.
 * Minimised panel heights need to be predefined (see
 * `@dimen/map_slideableinfo_height_titleonly`) and are automatically applied depending
 * on its state.
 */
class SlideableInfoFragment : MapInfoFragment() {

    private var mSlideableView: View? = null

    private var mLayout: SlidingUpPanelLayout? = null

    /**
     * View that is used by the SlidingUpPanel as its main content. It does not contain anything
     * and is only used as a transparent overlay to intercept touch events and to provide a dummy
     * container for the panel layout.
     */
    private var mPanelContent: View? = null


    private var mHeightTitleOnly: Int = 0
    private var mHeightMoscone: Int = 0
    private var mHeightSession: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load heights
        val resources = resources
        mHeightTitleOnly = resources.getDimensionPixelOffset(R.dimen.map_slideableinfo_height_titleonly)
        mHeightMoscone = resources.getDimensionPixelOffset(R.dimen.map_slideableinfo_height_moscone)
        mHeightSession = resources.getDimensionPixelOffset(R.dimen.map_slideableinfo_height_session)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        val root = super.onCreateView(inflater, container, savedInstanceState, R.layout.map_info_bottom)
        mLayout = root!!.findViewById(R.id.map_bottomsheet) as SlidingUpPanelLayout
        mSlideableView = mLayout!!.findViewById(R.id.map_bottomsheet_slideable)

        mLayout!!.setPanelSlideListener(mPanelSlideListener)
        mLayout!!.panelState = SlidingUpPanelLayout.PanelState.HIDDEN
        mLayout!!.setEnableDragViewTouchEvents(false)

        // Collapse the panel when the dummy content view is touched
        mPanelContent = root.findViewById(R.id.map_bottomsheet_dummycontent)
        mPanelContent!!.setOnClickListener { mLayout!!.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED }
        mPanelContent!!.isClickable = false

        return root
    }

    override fun showTitleOnly(roomType: Int, title: String) {
        super.showTitleOnly(roomType, title)
        setCollapsedOnly()
    }

    override fun onSessionListLoading(roomId: String, roomTitle: String) {

        // Update the title and hide the list if displayed.
        // We don't want to uneccessarily resize the panel.
        mTitle.text = roomTitle
        mList.visibility = if (mList.visibility == View.VISIBLE) View.INVISIBLE else View.GONE

    }


    private fun setCollapsedOnly() {
        // Set up panel: collapsed only with title height and icon
        mLayout!!.panelHeight = mHeightTitleOnly
        mLayout!!.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
        mLayout!!.isTouchEnabled = false
    }

    override fun showMoscone() {
        // Set up panel: collapsed with moscone height
        mLayout!!.panelHeight = mHeightMoscone
        mLayout!!.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
        mLayout!!.isTouchEnabled = false

        super.showMoscone()
    }

    override fun onSessionLoadingFailed(roomTitle: String, roomType: Int) {
        // Do not display the list but permanently hide it
        super.onSessionLoadingFailed(roomTitle, roomType)
        setCollapsedOnly()
    }


    override fun onSessionsLoaded(roomTitle: String, roomType: Int, cursor: Cursor) {
        super.onSessionsLoaded(roomTitle, roomType, cursor)
        // Set up panel: expandable with session height
        mLayout!!.panelHeight = mHeightSession
        mLayout!!.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
        mLayout!!.isTouchEnabled = true

    }

    override fun onRoomSubtitleLoaded(roomTitle: String, roomType: Int, subTitle: String) {
        super.onRoomSubtitleLoaded(roomTitle, roomType, subTitle)

        // Set up panel: Same height as Moscone, but collapsible
        mLayout!!.panelHeight = mHeightMoscone
        mLayout!!.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
        mLayout!!.isTouchEnabled = true
    }

    override fun hide() {
        mLayout!!.panelState = SlidingUpPanelLayout.PanelState.HIDDEN
        mLayout!!.panelHeight = 0
    }

    override fun isExpanded(): Boolean {
        return mLayout!!.panelState == SlidingUpPanelLayout.PanelState.EXPANDED
    }

    override fun minimize() {
        mLayout!!.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
    }

    private val mPanelSlideListener = object : SlidingUpPanelLayout.PanelSlideListener {
        override fun onPanelSlide(view: View, v: Float) {
            // Visible size of panel. The bottom position is therefore the height of the layout,
            // not the bottom of the expandable view.
            mCallback.onInfoSizeChanged(mSlideableView!!.left, mSlideableView!!.top,
                    mSlideableView!!.right, mLayout!!.height)
            mPanelContent!!.isClickable = false
        }

        override fun onPanelCollapsed(view: View) {
            mList.isScrollContainer = false
            mList.isEnabled = false
            mList.setSelection(0)
            mPanelContent!!.isClickable = false

        }

        override fun onPanelExpanded(view: View) {
            mList.isScrollContainer = true
            mList.isEnabled = true
            mPanelContent!!.isClickable = true
        }

        override fun onPanelAnchored(view: View) {
        }

        override fun onPanelHidden(view: View) {
            mCallback.onInfoSizeChanged(mSlideableView!!.left, mSlideableView!!.top,
                    mSlideableView!!.right, mLayout!!.height)
        }
    }

    companion object {

        /**
         * Progress of panel sliding after which the padding returned through the #Callback is fixed
         * at 0. Below this factor it returns the actual height of the bottom panel.
         */
        val MAX_PANEL_PADDING_FACTOR = 0.6f


        fun newInstance(): SlideableInfoFragment {
            return SlideableInfoFragment()
        }
    }

}
