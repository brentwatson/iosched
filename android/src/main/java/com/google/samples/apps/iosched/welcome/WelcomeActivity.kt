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

import android.app.AlertDialog
import android.app.Fragment
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import com.google.samples.apps.iosched.BuildConfig
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

/**
 * Terms of Service activity activated via
 * [com.google.samples.apps.iosched.core.activities.BaseActivity] functionality.
 */
class WelcomeActivity : AppCompatActivity(), WelcomeFragment.WelcomeFragmentContainer {
    internal var mContentFragment: WelcomeActivityContent? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_welcome)

        mContentFragment = getCurrentFragment(this)

        // If there's no fragment to use, we're done here.
        if (mContentFragment == null) {
            finish()
        }

        // Wire up the fragment
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.add(R.id.welcome_content, mContentFragment as Fragment?)
        fragmentTransaction.commit()

        LOGD(TAG, "Inside Create View.")

        setupAnimation()
    }

    private fun setupAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val iv = findViewById(R.id.logo) as ImageView
            val logoAnim = getDrawable(R.drawable.io_logo_white_anim) as AnimatedVectorDrawable
            iv.setImageDrawable(logoAnim)
            logoAnim.start()
        }
    }

    public override fun onResume() {
        super.onResume()

        // Show the debug warning if debug tools are enabled and it hasn't been shown yet.
        if (BuildConfig.ENABLE_DEBUG_TOOLS && !SettingsUtils.wasDebugWarningShown(this)) {
            displayDogfoodWarningDialog()
        }
    }

    /**
     * Display dogfood build warning and mark that it was shown.
     */
    private fun displayDogfoodWarningDialog() {
        AlertDialog.Builder(this).setTitle(Config.DOGFOOD_BUILD_WARNING_TITLE).setMessage(Config.DOGFOOD_BUILD_WARNING_TEXT).setPositiveButton(android.R.string.ok, null).show()
        SettingsUtils.markDebugWarningShown(this)
    }

    override fun getPositiveButton(): Button {
        return findViewById(R.id.button_accept) as Button
    }

    override fun setPositiveButtonEnabled(enabled: Boolean?) {
        try {
            positiveButton.isEnabled = enabled!!
        } catch (e: NullPointerException) {
            LOGD(TAG, "Positive welcome button doesn't exist to set enabled.")
        }

    }

    override fun getNegativeButton(): Button {
        return findViewById(R.id.button_decline) as Button
    }

    override fun setNegativeButtonEnabled(enabled: Boolean?) {
        try {
            negativeButton.isEnabled = enabled!!
        } catch (e: NullPointerException) {
            LOGD(TAG, "Negative welcome button doesn't exist to set enabled.")
        }

    }

    /**
     * The definition of a Fragment for a use in the WelcomeActivity.
     */
    internal interface WelcomeActivityContent {
        /**
         * Whether the fragment should be displayed.

         * @param context the application context.
         * *
         * @return true if the WelcomeActivityContent should be displayed.
         */
        fun shouldDisplay(context: Context): Boolean
    }

    companion object {
        private val TAG = makeLogTag(WelcomeActivity::class.java)

        /**
         * Get the current fragment to display.

         * This is the first fragment in the list that WelcomeActivityContent.shouldDisplay().

         * @param context the application context.
         * *
         * @return the WelcomeActivityContent to display or null if there's none.
         */
        private fun getCurrentFragment(context: Context): WelcomeActivityContent? {
            val welcomeActivityContents = welcomeFragments

            for (fragment in welcomeActivityContents) {
                if (fragment.shouldDisplay(context)) {
                    return fragment
                }
            }

            return null
        }

        /**
         * Whether to display the WelcomeActivity.

         * Decided whether any of the fragments need to be displayed.

         * @param context the application context.
         * *
         * @return true if the activity should be displayed.
         */
        fun shouldDisplay(context: Context): Boolean {
            val fragment = getCurrentFragment(context) ?: return false
            return true
        }

        /**
         * Get all WelcomeFragments for the WelcomeActivity.

         * @return the List of WelcomeFragments.
         */
        private val welcomeFragments: List<WelcomeActivityContent>
            get() = listOf(
                    TosFragment(),
                    ConductFragment(),
                    AttendingFragment(),
                    AccountFragment())
    }
}
