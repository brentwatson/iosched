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
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.ui.widget.CollectionView
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.videolibrary.VideoLibraryModel.VideoLibraryQueryEnum
import com.google.samples.apps.iosched.videolibrary.VideoLibraryModel.VideoLibraryUserActionEnum

/**
 * This Activity displays all the videos of past Google I/O sessions in the form of a card for each
 * topics and a card for new videos of the current year.
 */
class VideoLibraryActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.video_library_act)

        addPresenterFragment(R.id.video_library_frag,
                VideoLibraryModel(applicationContext, this),
                arrayOf(VideoLibraryQueryEnum.VIDEOS, VideoLibraryQueryEnum.MY_VIEWED_VIDEOS),
                arrayOf(VideoLibraryUserActionEnum.RELOAD, VideoLibraryUserActionEnum.VIDEO_PLAYED))

        // ANALYTICS SCREEN: View the video library screen
        // Contains: Nothing (Page name is a constant)
        AnalyticsHelper.sendScreenView(SCREEN_LABEL)

        registerHideableHeaderView(findViewById(R.id.headerbar))
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        enableActionBarAutoHide(findViewById(R.id.videos_collection_view) as CollectionView)
    }

    override fun getSelfNavDrawerItem(): Int {
        return BaseActivity.NAVDRAWER_ITEM_VIDEO_LIBRARY
    }

    override fun onActionBarAutoShowOrHide(shown: Boolean) {
        super.onActionBarAutoShowOrHide(shown)
        val frame = findViewById(R.id.main_content) as DrawShadowFrameLayout
        frame.setShadowVisible(shown, shown)
    }

    companion object {

        private val SCREEN_LABEL = "Video Library"
    }
}
