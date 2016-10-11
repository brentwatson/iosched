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

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.util.TimeUtils
import com.google.samples.apps.iosched.util.UIUtils
import com.google.samples.apps.iosched.welcome.WelcomeActivity

import java.util.HashMap
import java.util.TimeZone

import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

/**
 * Utilities and constants related to app settings_prefs.
 */
object SettingsUtils {

    private val TAG = makeLogTag(SettingsUtils::class.java)

    /**
     * This is changed each year to effectively reset certain preferences that should be re-asked
     * each year. Note, res/xml/settings_prefs.xml must be updated when this value is updated.
     */
    private val CONFERENCE_YEAR_PREF_POSTFIX = "_2015"

    /**
     * Boolean preference indicating the user would like to see times in their local timezone
     * throughout the app.
     */
    val PREF_LOCAL_TIMES = "pref_local_times"

    /**
     * Boolean preference indicating that the user will be attending the conference.
     */
    val PREF_ATTENDEE_AT_VENUE = "pref_attendee_at_venue" + CONFERENCE_YEAR_PREF_POSTFIX

    /**
     * Boolean preference indicating whether the app has
     * `com.google.samples.apps.iosched.ui.BaseActivity.performDataBootstrap installed` the
     * `R.raw.bootstrap_data bootstrap data`.
     */
    val PREF_DATA_BOOTSTRAP_DONE = "pref_data_bootstrap_done"

    /**
     * Boolean indicating whether the app should attempt to sign in on startup (default true).
     */
    val PREF_USER_REFUSED_SIGN_IN = "pref_user_refused_sign_in" + CONFERENCE_YEAR_PREF_POSTFIX

    /**
     * Boolean indicating whether the debug build warning was already shown.
     */
    val PREF_DEBUG_BUILD_WARNING_SHOWN = "pref_debug_build_warning_shown"

    /**
     * Boolean indicating whether ToS has been accepted.
     */
    val PREF_TOS_ACCEPTED = "pref_tos_accepted" + CONFERENCE_YEAR_PREF_POSTFIX

    /**
     * Boolean indicating whether CoC has been accepted.
     */
    private val PREF_CONDUCT_ACCEPTED = "pref_conduct_accepted" + CONFERENCE_YEAR_PREF_POSTFIX

    /**
     * Boolean indicating whether ToS has been accepted.
     */
    val PREF_DECLINED_WIFI_SETUP = "pref_declined_wifi_setup" + CONFERENCE_YEAR_PREF_POSTFIX

    /**
     * Boolean indicating whether user has answered if they are local or remote.
     */
    val PREF_ANSWERED_LOCAL_OR_REMOTE = "pref_answered_local_or_remote" + CONFERENCE_YEAR_PREF_POSTFIX

    /**
     * Long indicating when a sync was last ATTEMPTED (not necessarily succeeded).
     */
    val PREF_LAST_SYNC_ATTEMPTED = "pref_last_sync_attempted"

    /**
     * Long indicating when a sync last SUCCEEDED.
     */
    val PREF_LAST_SYNC_SUCCEEDED = "pref_last_sync_succeeded"

    /**
     * Long storing the sync interval that's currently configured.
     */
    val PREF_CUR_SYNC_INTERVAL = "pref_cur_sync_interval"

    /**
     * Boolean indicating app should sync sessions with local calendar
     */
    val PREF_SYNC_CALENDAR = "pref_sync_calendar"

    /**
     * Boolean indicating whether the app has performed the (one-time) welcome flow.
     */
    val PREF_WELCOME_DONE = "pref_welcome_done" + CONFERENCE_YEAR_PREF_POSTFIX

    /**
     * Boolean indicating if the app can collect Analytics.
     */
    val PREF_ANALYTICS_ENABLED = "pref_analytics_enabled"

    /**
     * Boolean indicating whether to show session reminder notifications.
     */
    val PREF_SHOW_SESSION_REMINDERS = "pref_show_session_reminders" + CONFERENCE_YEAR_PREF_POSTFIX

    /**
     * Boolean indicating whether to show session feedback notifications.
     */
    val PREF_SHOW_SESSION_FEEDBACK_REMINDERS = "pref_show_session_feedback_reminders" + CONFERENCE_YEAR_PREF_POSTFIX

    /**
     * Return the [TimeZone] the app is set to use (either user or conference).

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun getDisplayTimeZone(context: Context): TimeZone {
        val defaultTz = TimeZone.getDefault()
        return if (isUsingLocalTime(context) && defaultTz != null)
            defaultTz
        else
            Config.CONFERENCE_TIMEZONE
    }

    /**
     * Return true if the user has indicated they want the schedule in local times, false if they
     * want to use the conference time zone. This preference is enabled/disabled by the user in the
     * [SettingsActivity].

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun isUsingLocalTime(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_LOCAL_TIMES, false)
    }

    /**
     * Return true if the user has indicated they're attending I/O in person. This preference can be
     * enabled/disabled by the user in the
     * [SettingsActivity].

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun isAttendeeAtVenue(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_ATTENDEE_AT_VENUE, true)
    }

    /**
     * Mark that the app has finished loading the `R.raw.bootstrap_data bootstrap data`.

     * @param context Context to be used to edit the [android.content.SharedPreferences].
     */
    fun markDataBootstrapDone(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(PREF_DATA_BOOTSTRAP_DONE, true).apply()
    }

    /**
     * Return true when the `R.raw.bootstrap_data_json bootstrap data` has been marked loaded.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun isDataBootstrapDone(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_DATA_BOOTSTRAP_DONE, false)
    }

    /**
     * Set the attendee preference indicating whether they'll be attending Google I/O on site.

     * @param context  Context to be used to edit the [android.content.SharedPreferences].
     * *
     * @param newValue New value that will be set.
     */
    fun setAttendeeAtVenue(context: Context, newValue: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(PREF_ATTENDEE_AT_VENUE, newValue).apply()
    }

    /**
     * Mark that the user explicitly chose not to sign in so app doesn't ask them again.

     * @param context Context to be used to edit the [android.content.SharedPreferences].
     */
    fun markUserRefusedSignIn(context: Context, refused: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(PREF_USER_REFUSED_SIGN_IN, refused).apply()
    }

    /**
     * Return true if user refused to sign in, false if they haven't refused (yet).

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun hasUserRefusedSignIn(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_USER_REFUSED_SIGN_IN, false)
    }

    /**
     * Return true if the
     * `com.google.samples.apps.iosched.welcome.WelcomeActivity.displayDogfoodWarningDialog() Dogfood Build Warning`
     * has already been marked as shown, false if not.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun wasDebugWarningShown(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_DEBUG_BUILD_WARNING_SHOWN, false)
    }

    /**
     * Mark the
     * `com.google.samples.apps.iosched.welcome.WelcomeActivity.displayDogfoodWarningDialog() Dogfood Build Warning`
     * shown to user.

     * @param context Context to be used to edit the [android.content.SharedPreferences].
     */
    fun markDebugWarningShown(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(PREF_DEBUG_BUILD_WARNING_SHOWN, true).apply()
    }

    /**
     * Return true if user has accepted the
     * [Tos][WelcomeActivity], false if they haven't (yet).

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun isTosAccepted(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_TOS_ACCEPTED, false)
    }

    /**
     * Return true if user has accepted the Code of
     * [Conduct][com.google.samples.apps.iosched.welcome.ConductFragment], false if they haven't (yet).

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun isConductAccepted(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_CONDUCT_ACCEPTED, false)
    }

    /**
     * Mark `newValue whether` the user has accepted the TOS so the app doesn't ask again.

     * @param context Context to be used to edit the [android.content.SharedPreferences].
     * *
     * @param newValue New value that will be set.
     */
    fun markTosAccepted(context: Context, newValue: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(PREF_TOS_ACCEPTED, newValue).apply()
    }

    /**
     * Mark `newValue whether` the user has accepted the Code of Conduct so the app doesn't ask again.

     * @param context Context to be used to edit the [android.content.SharedPreferences].
     * *
     * @param newValue New value that will be set.
     */
    fun markConductAccepted(context: Context, newValue: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(PREF_CONDUCT_ACCEPTED, newValue).apply()
    }

    /**
     * Return true if user has already declined WiFi setup, but false if they haven't yet.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun hasDeclinedWifiSetup(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_DECLINED_WIFI_SETUP, false)
    }

    /**
     * Mark that the user has explicitly declined WiFi setup assistance.

     * @param context  Context to be used to edit the [android.content.SharedPreferences].
     * *
     * @param newValue New value that will be set.
     */
    fun markDeclinedWifiSetup(context: Context, newValue: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(PREF_DECLINED_WIFI_SETUP, newValue).apply()
    }

    /**
     * Returns true if user has already indicated whether they're a local or remote I/O attendee,
     * false if they haven't answered yet.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun hasAnsweredLocalOrRemote(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_ANSWERED_LOCAL_OR_REMOTE, false)
    }

    /**
     * Mark that the user answered whether they're a local or remote I/O attendee.

     * @param context  Context to be used to edit the [android.content.SharedPreferences].
     * *
     * @param newValue New value that will be set.
     */
    fun markAnsweredLocalOrRemote(context: Context, newValue: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(PREF_ANSWERED_LOCAL_OR_REMOTE, newValue).apply()
    }

    /**
     * Return true if the first-app-run-activities have already been executed.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun isFirstRunProcessComplete(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_WELCOME_DONE, false)
    }

    /**
     * Mark `newValue whether` this is the first time the first-app-run-processes have run.
     * Managed by [the][com.google.samples.apps.iosched.ui.BaseActivity]
     * [two][com.google.samples.apps.iosched.core.activities.BaseActivity] base activities.

     * @param context  Context to be used to edit the [android.content.SharedPreferences].
     * *
     * @param newValue New value that will be set.
     */
    fun markFirstRunProcessesDone(context: Context, newValue: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(PREF_WELCOME_DONE, newValue).apply()
    }

    /**
     * Return a long representing the last time a sync was attempted (regardless of success).

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun getLastSyncAttemptedTime(context: Context): Long {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getLong(PREF_LAST_SYNC_ATTEMPTED, 0L)
    }

    /**
     * Mark a sync was attempted (stores current time as 'last sync attempted' preference).

     * @param context Context to be used to edit the [android.content.SharedPreferences].
     */
    fun markSyncAttemptedNow(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putLong(PREF_LAST_SYNC_ATTEMPTED, UIUtils.getCurrentTime(context)).apply()
    }

    /**
     * Return a long representing the last time a sync succeeded.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun getLastSyncSucceededTime(context: Context): Long {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getLong(PREF_LAST_SYNC_SUCCEEDED, 0L)
    }

    /**
     * Mark that a sync succeeded (stores current time as 'last sync succeeded' preference).

     * @param context Context to be used to edit the [android.content.SharedPreferences].
     */
    fun markSyncSucceededNow(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putLong(PREF_LAST_SYNC_SUCCEEDED, UIUtils.getCurrentTime(context)).apply()
    }

    /**
     * Return true if analytics are enabled, false if user has disabled them.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun isAnalyticsEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_ANALYTICS_ENABLED, true)
    }

    /**
     * Return true if session reminders are enabled, false if user has disabled them.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun shouldShowSessionReminders(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_SHOW_SESSION_REMINDERS, true)
    }

    /**
     * Return true if session feedback reminders are enabled, false if user has disabled them.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun shouldShowSessionFeedbackReminders(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_SHOW_SESSION_FEEDBACK_REMINDERS, true)
    }

    /**
     * Return a long representing the current data sync interval time.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun getCurSyncInterval(context: Context): Long {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getLong(PREF_CUR_SYNC_INTERVAL, 0L)
    }

    /**
     * Set a new interval for the data sync time.

     * @param context  Context to be used to edit the [android.content.SharedPreferences].
     * *
     * @param newValue New value that will be set.
     */
    fun setCurSyncInterval(context: Context, newValue: Long) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putLong(PREF_CUR_SYNC_INTERVAL, newValue).apply()
    }

    /**
     * Return true if calendar sync is enabled, false if disabled.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun shouldSyncCalendar(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_SYNC_CALENDAR, false)
    }

    /**
     * Helper method to register a settings_prefs listener. This method does not automatically handle
     * `unregisterOnSharedPreferenceChangeListener() un-registering` the listener at the end
     * of the `context` lifecycle.

     * @param context  Context to be used to lookup the [android.content.SharedPreferences].
     * *
     * @param listener Listener to register.
     */
    fun registerOnSharedPreferenceChangeListener(context: Context,
                                                 listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Helper method to un-register a settings_prefs listener typically registered with
     * `registerOnSharedPreferenceChangeListener()`

     * @param context  Context to be used to lookup the [android.content.SharedPreferences].
     * *
     * @param listener Listener to un-register.
     */
    fun unregisterOnSharedPreferenceChangeListener(context: Context,
                                                   listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
