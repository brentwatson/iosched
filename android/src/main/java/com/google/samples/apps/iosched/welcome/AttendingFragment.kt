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
package com.google.samples.apps.iosched.welcome

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.settings.SettingsUtils

import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

/**
 * The attending in person fragment in the welcome screen.
 */
class AttendingFragment : WelcomeFragment(), WelcomeActivity.WelcomeActivityContent {

    override fun shouldDisplay(context: Context): Boolean {
        return !SettingsUtils.hasAnsweredLocalOrRemote(context)
    }


    override fun getPositiveListener(): View.OnClickListener {
        return object : WelcomeFragment.WelcomeFragmentOnClickListener(mActivity) {
            override fun onClick(v: View) {
                // Ensure we don't run this fragment again
                LOGD(TAG, "Marking attending flag.")
                SettingsUtils.setAttendeeAtVenue(mActivity, true)
                SettingsUtils.markAnsweredLocalOrRemote(mActivity, true)
                doNext()
            }
        }
    }

    override fun getNegativeListener(): View.OnClickListener {
        return object : WelcomeFragment.WelcomeFragmentOnClickListener(mActivity) {
            override fun onClick(v: View) {
                LOGD(TAG, "Marking not attending flag.")
                SettingsUtils.setAttendeeAtVenue(mActivity, false)
                SettingsUtils.markAnsweredLocalOrRemote(mActivity, true)
                doNext()
            }
        }
    }

    override fun getPositiveText(): String {
        return getResourceString(R.string.attending_in_person)
    }

    override fun getNegativeText(): String {
        return getResourceString(R.string.attending_remotely)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.welcome_attending_fragment, container, false)
    }

    companion object {
        private val TAG = makeLogTag(AttendingFragment::class.java)
    }

}
