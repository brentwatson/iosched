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

package com.google.samples.apps.iosched.debug

import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.debug.actions.*
import com.google.samples.apps.iosched.explore.ExploreSessionsActivity
import com.google.samples.apps.iosched.service.SessionAlarmService
import com.google.samples.apps.iosched.settings.ConfMessageCardUtils
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGW
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.google.samples.apps.iosched.util.TimeUtils
import com.google.samples.apps.iosched.util.UIUtils
import com.google.samples.apps.iosched.util.WiFiUtils
import com.google.samples.apps.iosched.welcome.WelcomeActivity

/**
 * [android.app.Activity] displaying debug options so a developer can debug and test. This
 * functionality is only enabled when [com.google.samples.apps.iosched.BuildConfig].DEBUG
 * is true.
 */
class DebugFragment : Fragment() {

    /**
     * Area of screen used to display log log messages.
     */
    private var mLogArea: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        super.onCreate(savedInstanceState)
        val rootView = inflater.inflate(R.layout.debug_frag, null) as ViewGroup
        mLogArea = rootView.findViewById(R.id.logArea) as TextView
        val tests = rootView.findViewById(R.id.debug_action_list) as ViewGroup
        tests.addView(createTestAction(ForceSyncNowAction()))
        tests.addView(createTestAction(DisplayUserDataDebugAction()))
        tests.addView(createTestAction(ShowAllDriveFilesDebugAction()))
        tests.addView(createTestAction(ForceAppDataSyncNowAction()))
        tests.addView(createTestAction(TestScheduleHelperAction()))
        tests.addView(createTestAction(ScheduleStarredSessionAlarmsAction()))
        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                val sessionId = SessionAlarmService.DEBUG_SESSION_ID
                val sessionTitle = "Debugging with Placeholder Text"

                val intent = Intent(
                        SessionAlarmService.ACTION_NOTIFY_SESSION_FEEDBACK,
                        null, context, SessionAlarmService::class.java)
                intent.putExtra(SessionAlarmService.EXTRA_SESSION_ID, sessionId)
                intent.putExtra(SessionAlarmService.EXTRA_SESSION_START, System.currentTimeMillis() - 30 * 60 * 1000)
                intent.putExtra(SessionAlarmService.EXTRA_SESSION_END, System.currentTimeMillis())
                intent.putExtra(SessionAlarmService.EXTRA_SESSION_TITLE, sessionTitle)
                context.startService(intent)
                Toast.makeText(context, "Showing DEBUG session feedback notification.", Toast.LENGTH_LONG).show()
            }

            override val label: String
                get() = "Show session feedback notification"
        }))
        tests.addView(createTestAction(ShowSessionNotificationDebugAction()))
        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                context.startActivity(Intent(context, WelcomeActivity::class.java))
            }

            override val label: String
                get() = "Display Welcome Activity"
        }))

        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                SettingsUtils.markTosAccepted(context, false)
                SettingsUtils.markConductAccepted(context, false)
                SettingsUtils.setAttendeeAtVenue(context, false)
                SettingsUtils.markAnsweredLocalOrRemote(context, false)
                AccountUtils.setActiveAccount(context, null)
                ConfMessageCardUtils.unsetStateForAllCards(context)
            }

            override val label: String
                get() = "Reset Welcome Flags"
        }))
        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                val intent = Intent(context, ExploreSessionsActivity::class.java)
                intent.putExtra(ExploreSessionsActivity.EXTRA_FILTER_TAG, "TOPIC_ANDROID")
                context.startActivity(intent)
            }

            override val label: String
                get() = "Show Explore Sessions Activity (Android Topic)"
        }))
        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                LOGW(TAG, "Unsetting all Explore I/O message card answers.")
                ConfMessageCardUtils.markAnsweredConfMessageCardsPrompt(context, null)
                ConfMessageCardUtils.setConfMessageCardsEnabled(context, null)
                ConfMessageCardUtils.unsetStateForAllCards(context)
            }

            override val label: String
                get() = "Unset all Explore I/O-based card answers"
        }))
        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                val currentTime = java.util.Date(UIUtils.getCurrentTime(context))
                val newTime = java.util.Date(Config.CONFERENCE_START_MILLIS - TimeUtils.HOUR * 3)
                LOGW(TAG, "Setting time from $currentTime to $newTime")
                UIUtils.setCurrentTime(context, newTime.time)
            }

            override val label: String
                get() = "Set time to 3 hours before Conf"
        }))
        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                val currentTime = java.util.Date(UIUtils.getCurrentTime(context))
                val newTime = java.util.Date(Config.CONFERENCE_START_MILLIS - TimeUtils.DAY)
                LOGW(TAG, "Setting time from $currentTime to $newTime")
                UIUtils.setCurrentTime(context, newTime.time)
            }

            override val label: String
                get() = "Set time to Day Before Conf"
        }))

        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                val currentTime = java.util.Date(UIUtils.getCurrentTime(context))
                val newTime = java.util.Date(Config.CONFERENCE_START_MILLIS + TimeUtils.HOUR * 3)
                LOGW(TAG, "Setting time from " + currentTime +
                        " to " + newTime)
                UIUtils.setCurrentTime(context, newTime.time)
                LOGW(TAG, "Unsetting all Explore I/O card answers and settings.")
                ConfMessageCardUtils.markAnsweredConfMessageCardsPrompt(context, null)
                ConfMessageCardUtils.setConfMessageCardsEnabled(context, null)
                SettingsUtils.markDeclinedWifiSetup(context, false)
                WiFiUtils.uninstallConferenceWiFi(context)
            }

            override val label: String
                get() = "Set time to 3 hours after Conf start"
        }))
        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                val currentTime = java.util.Date(UIUtils.getCurrentTime(context))
                val newTime = java.util.Date(Config.CONFERENCE_DAYS[1][0] + TimeUtils.HOUR * 3)
                LOGW(TAG, "Setting time from $currentTime to $newTime")
                UIUtils.setCurrentTime(context, newTime.time)
            }

            override val label: String
                get() = "Set time to 3 hours after 2nd day start"
        }))
        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                val currentTime = java.util.Date(UIUtils.getCurrentTime(context))
                val newTime = java.util.Date(Config.CONFERENCE_END_MILLIS + TimeUtils.HOUR * 3)
                LOGW(TAG, "Setting time from $currentTime to $newTime")
                UIUtils.setCurrentTime(context, newTime.time)
            }

            override val label: String
                get() = "Set time to 3 hours after Conf end"
        }))
        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                ConfMessageCardUtils.markShouldShowConfMessageCard(context,
                        ConfMessageCardUtils.ConfMessageCard.CONFERENCE_CREDENTIALS, true)
            }

            override val label: String
                get() = "Force 'Conference Credentials' message card."
        }))
        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                ConfMessageCardUtils.markShouldShowConfMessageCard(context,
                        ConfMessageCardUtils.ConfMessageCard.KEYNOTE_ACCESS, true)
            }

            override val label: String
                get() = "Force 'Keynote Access' message card."
        }))
        tests.addView(createTestAction(object : DebugAction {
            override fun run(context: Context, callback: DebugAction.Callback) {
                ConfMessageCardUtils.markShouldShowConfMessageCard(context,
                        ConfMessageCardUtils.ConfMessageCard.AFTER_HOURS, true)
            }

            override val label: String
                get() = "Force 'After Hours' message card."
        }))

        return rootView
    }

    protected fun createTestAction(test: DebugAction): View {
        val testButton = Button(this.activity)
        testButton.text = test.label
        testButton.setOnClickListener { view ->
            val start = System.currentTimeMillis()
            mLogArea!!.text = ""
            //test.run(view.context) { success, message ->
                //logTimed(System.currentTimeMillis() - start,
                //        (if (success) "[OK] " else "[FAIL] ") + message)
            //}
        }
        return testButton
    }

    protected fun logTimed(time: Long, message: String) {
        var message = message
        message = "[" + time + "ms] " + message
        Log.d(TAG, message)
        mLogArea!!.append(message + "\n")
    }

    private fun setContentTopClearance(clearance: Int) {
        if (view != null) {
            view!!.setPadding(view!!.paddingLeft, clearance,
                    view!!.paddingRight, view!!.paddingBottom)
        }
    }

    override fun onResume() {
        super.onResume()

        // configure fragment's top clearance to take our overlaid controls (Action Bar
        // and spinner box) into account.
        val actionBarSize = UIUtils.calculateActionBarSize(activity)
        val drawShadowFrameLayout = activity.findViewById(R.id.main_content) as DrawShadowFrameLayout
        drawShadowFrameLayout?.setShadowTopOffset(actionBarSize)
        setContentTopClearance(actionBarSize + resources.getDimensionPixelSize(R.dimen.explore_grid_padding))
    }

    companion object {

        private val TAG = makeLogTag(DebugFragment::class.java)
    }
}
