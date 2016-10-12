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

/**
 * Map info fragment that displays its content within in.

 * It resizes based on the available space of its container. The list of sessions is automatically
 * marked as scrollable if required.
 * It is designed to be displayed at the left of the screen with a fixed width that is the only
 * value that is returned to
 * [com.google.samples.apps.iosched.map.MapInfoFragment.Callback.onInfoSizeChanged].
 */
class InlineInfoFragment : MapInfoFragment() {

    private var mLayout: View? = null

    private var mWidth = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        mLayout = super.onCreateView(inflater, container, savedInstanceState, R.layout.map_info_inline)

        mLayout!!.addOnLayoutChangeListener(mLayoutChangeListener)

        return mLayout
    }

    private val mLayoutChangeListener = object : View.OnLayoutChangeListener {
        override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int,
                                    oldTop: Int, oldRight: Int, oldBottom: Int) {
            mWidth = right
            mLayout!!.removeOnLayoutChangeListener(this)
        }
    }

    override fun onSessionListLoading(roomId: String, roomTitle: String) {
        // Do not update the UI while the list is loading to prevent flickering when list is set.
    }

    override fun onSessionsLoaded(roomTitle: String, roomType: Int, cursor: Cursor) {
        super.onSessionsLoaded(roomTitle, roomType, cursor)
        show()
    }

    override fun onRoomSubtitleLoaded(roomTitle: String, roomType: Int, subTitle: String) {
        super.onRoomSubtitleLoaded(roomTitle, roomType, subTitle)
        show()
    }

    override fun onSessionLoadingFailed(roomTitle: String, roomType: Int) {
        super.onSessionLoadingFailed(roomTitle, roomType)
        show()
    }

    override fun showMoscone() {
        super.showMoscone()
        show()
    }

    override fun showTitleOnly(icon: Int, roomTitle: String) {
        super.showTitleOnly(icon, roomTitle)
        show()
    }

    override fun isExpanded(): Boolean {
        return mLayout!!.visibility == View.VISIBLE
    }

    override fun minimize() {
        hide()
    }

    private fun show() {
        mLayout!!.visibility = View.VISIBLE
        mCallback.onInfoSizeChanged(0, 0, mWidth, 0)
    }

    override fun hide() {
        mLayout!!.visibility = View.GONE
        mCallback.onInfoSizeChanged(0, 0, 0, 0)
    }

    companion object {

        fun newInstance(): MapInfoFragment {
            return InlineInfoFragment()
        }
    }
}
