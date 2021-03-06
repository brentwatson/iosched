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
 * The code of conduct fragment in the welcome screen.
 */
class ConductFragment : WelcomeFragment(), WelcomeActivity.WelcomeActivityContent {

    override fun shouldDisplay(context: Context): Boolean {
        return !SettingsUtils.isConductAccepted(context)
    }

    override fun getPositiveListener(): View.OnClickListener {
        return object : WelcomeFragment.WelcomeFragmentOnClickListener(mActivity) {
            override fun onClick(v: View) {
                // Ensure we don't run this fragment again
                LOGD(TAG, "Marking code of conduct flag.")
                SettingsUtils.markConductAccepted(mActivity, true)
                doNext()
            }
        }
    }

    override fun getNegativeListener(): View.OnClickListener {
        return object : WelcomeFragment.WelcomeFragmentOnClickListener(mActivity) {
            override fun onClick(v: View) {
                // Nothing to do here
                LOGD(TAG, "Need to accept Code of Conduct.")
                doFinish()
            }
        }
    }

    override fun getPositiveText(): String {
        return getResourceString(R.string.accept)
    }

    override fun getNegativeText(): String {
        return getResourceString(R.string.decline)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.welcome_conduct_fragment, container, false)
    }

    companion object {
        private val TAG = makeLogTag(ConductFragment::class.java)
    }
}