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

package com.google.samples.apps.iosched.ui

import com.google.samples.apps.iosched.R

import android.content.Intent
import android.os.Bundle
import android.app.Fragment

/**
 * A [BaseActivity] that simply contains a single fragment. The intent used to invoke this
 * activity is forwarded to the fragment as arguments during fragment instantiation. Derived
 * activities should only need to implement [SimpleSinglePaneActivity.onCreatePane].
 */
abstract class SimpleSinglePaneActivity : BaseActivity() {
    var fragment: Fragment? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentViewResId)

        if (intent.hasExtra(Intent.EXTRA_TITLE)) {
            title = intent.getStringExtra(Intent.EXTRA_TITLE)
        }

        val customTitle = intent.getStringExtra(Intent.EXTRA_TITLE)
        title = customTitle ?: title

        if (savedInstanceState == null) {
            fragment = onCreatePane()
            fragment!!.arguments = BaseActivity.intentToFragmentArguments(intent)
            fragmentManager.beginTransaction().add(R.id.root_container, fragment, "single_pane").commit()
        } else {
            fragment = fragmentManager.findFragmentByTag("single_pane")
        }
    }

    protected val contentViewResId: Int
        get() = R.layout.activity_singlepane_empty

    /**
     * Called in `onCreate` when the fragment constituting this activity is needed.
     * The returned fragment's arguments will be set to the intent used to invoke this activity.
     */
    protected abstract fun onCreatePane(): Fragment
}
