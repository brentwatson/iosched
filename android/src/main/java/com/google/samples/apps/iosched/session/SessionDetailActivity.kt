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

package com.google.samples.apps.iosched.session

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.myschedule.MyScheduleActivity
import com.google.samples.apps.iosched.session.SessionDetailModel.SessionDetailQueryEnum
import com.google.samples.apps.iosched.session.SessionDetailModel.SessionDetailUserActionEnum
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.util.BeamUtils
import com.google.samples.apps.iosched.util.LogUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGE
import com.google.samples.apps.iosched.util.SessionsHelper
import com.google.samples.apps.iosched.util.UIUtils

/**
 * Displays the details about a session. This Activity is launched via an `Intent` with
 * [Intent.ACTION_VIEW] and a [Uri] built with
 * [com.google.samples.apps.iosched.provider.ScheduleContract.Sessions.buildSessionUri].
 */
class SessionDetailActivity : BaseActivity() {

    private val mHandler = Handler()

    var sessionUri: Uri? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        UIUtils.tryTranslateHttpIntent(this)
        BeamUtils.tryUpdateIntentFromBeam(this)
        val shouldBeFloatingWindow = shouldBeFloatingWindow()
        if (shouldBeFloatingWindow) {
            setupFloatingWindow(R.dimen.session_details_floating_width,
                    R.dimen.session_details_floating_height, 1, 0.4f)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.session_detail_act)

        val toolbar = actionBarToolbar
        toolbar.setNavigationIcon(if (shouldBeFloatingWindow)
            R.drawable.ic_ab_close
        else
            R.drawable.ic_up)
        toolbar.setNavigationContentDescription(R.string.close_and_go_back)
        toolbar.setNavigationOnClickListener { finish() }
        mHandler.post {
            // Do not display the Activity name in the toolbar
            toolbar.title = ""
        }

        if (savedInstanceState == null) {
            val sessionUri = intent.data
            BeamUtils.setBeamSessionUri(this, sessionUri)
        }

        sessionUri = intent.data

        if (sessionUri == null) {
            LOGE(TAG, "SessionDetailActivity started with null session Uri!")
            finish()
            return
        }

        addPresenterFragment(R.id.session_detail_frag,
                SessionDetailModel(sessionUri, applicationContext,
                        SessionsHelper(this)), SessionDetailQueryEnum.values(),
                SessionDetailUserActionEnum.values())
    }

    override fun getParentActivityIntent(): Intent? {
        return Intent(this, MyScheduleActivity::class.java)
    }

    companion object {

        private val TAG = LogUtils.makeLogTag(SessionDetailActivity::class.java)
    }
}
