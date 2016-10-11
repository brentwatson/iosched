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

package com.google.samples.apps.iosched.settings

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceFragment

import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.service.SessionCalendarService
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout
import com.google.samples.apps.iosched.util.UIUtils

/**
 * Activity for customizing app settings.
 */
class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_act)
        overridePendingTransition(0, 0)
    }

    override fun getSelfNavDrawerItem(): Int {
        return BaseActivity.NAVDRAWER_ITEM_SETTINGS
    }

    override fun onActionBarAutoShowOrHide(shown: Boolean) {
        super.onActionBarAutoShowOrHide(shown)
        val frame = findViewById(R.id.main_content) as DrawShadowFrameLayout
        frame.setShadowVisible(shown, shown)
    }

    /**
     * The Fragment is added via the R.layout.settings_act layout xml.
     */
    class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.settings_prefs)

            SettingsUtils.registerOnSharedPreferenceChangeListener(activity, this)
        }

        override fun onDestroy() {
            super.onDestroy()
            SettingsUtils.unregisterOnSharedPreferenceChangeListener(activity, this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            if (SettingsUtils.PREF_SYNC_CALENDAR == key) {
                val intent: Intent
                if (SettingsUtils.shouldSyncCalendar(activity)) {
                    // Add all calendar entries
                    intent = Intent(SessionCalendarService.ACTION_UPDATE_ALL_SESSIONS_CALENDAR)
                } else {
                    // Remove all calendar entries
                    intent = Intent(SessionCalendarService.ACTION_CLEAR_ALL_SESSIONS_CALENDAR)
                }

                intent.setClass(activity, SessionCalendarService::class.java)
                activity.startService(intent)
            }
        }


        private fun setContentTopClearance(clearance: Int) {
            if (view != null) {
                view!!.setPadding(view!!.paddingLeft, clearance,
                        view!!.paddingRight, view!!.paddingBottom)
            }
        }

        override fun onResume() {
            super.onResume()

            // configure the fragment's top clearance to take our overlaid controls (Action Bar
            // and spinner box) into account.
            val actionBarSize = UIUtils.calculateActionBarSize(activity)
            val drawShadowFrameLayout = activity.findViewById(R.id.main_content) as DrawShadowFrameLayout
            drawShadowFrameLayout?.setShadowTopOffset(actionBarSize)
            setContentTopClearance(actionBarSize)
        }
    }
}
