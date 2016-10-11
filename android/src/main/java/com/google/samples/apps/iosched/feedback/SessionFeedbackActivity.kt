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

package com.google.samples.apps.iosched.feedback

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.NavUtils
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.myschedule.MyScheduleActivity
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.util.BeamUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGE
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

/**
 * Displays the questions and rating bars, as well as a comment box, for the user to provide
 * feedback on a session. The `mSessionUri` should be passed with the
 * [android.content.Intent] starting this Activity.
 */
class SessionFeedbackActivity : BaseActivity() {

    var sessionUri: Uri? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.session_feedback_act)

        if (savedInstanceState == null) {
            val sessionUri = intent.data
            BeamUtils.setBeamSessionUri(this, sessionUri)
        }

        sessionUri = intent.data

        if (sessionUri == null) {
            LOGE(TAG, "SessionFeedbackActivity started with null data URI!")
            finish()
        }

        addPresenterFragment(R.id.session_feedback_frag,
                SessionFeedbackModel(sessionUri!!, applicationContext,
                        FeedbackHelper(this)),
                SessionFeedbackModel.SessionFeedbackQueryEnum.values(),
                SessionFeedbackModel.SessionFeedbackUserActionEnum.values())


        val toolbar = actionBarToolbar
        toolbar.setNavigationIcon(R.drawable.ic_up)
        toolbar.setNavigationContentDescription(R.string.close_and_go_back)
        toolbar.setNavigationOnClickListener {
            NavUtils.navigateUpTo(this@SessionFeedbackActivity,
                    parentActivityIntent)
        }
    }

    override fun getParentActivityIntent(): Intent? {
        // Up to this session's track details, or Home if no track is available
        if (sessionUri != null) {
            return Intent(Intent.ACTION_VIEW, sessionUri)
        } else {
            return Intent(this, MyScheduleActivity::class.java)
        }
    }

    companion object {

        private val TAG = makeLogTag(SessionFeedbackActivity::class.java)
    }
}
