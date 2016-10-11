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

package com.google.samples.apps.iosched.settings

import com.google.samples.apps.iosched.util.TimeUtils
import com.google.samples.apps.iosched.util.UIUtils

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

import java.util.HashMap
import java.util.Random

/**
 * Utilities and constants to deal with enabling & showing conference message cards.
 */
object ConfMessageCardUtils {

    /**
     * Boolean preference indicating whether to show conference info cards in Explore stream.
     */
    val PREF_CONF_MESSAGE_CARDS_ENABLED = "pref_conf_message_cards_enabled"

    /**
     * Boolean preference indicating whether to show conference info cards in Explore stream.
     */
    val PREF_ANSWERED_CONF_MESSAGE_CARDS_PROMPT = "pref_answered_conf_message_cards_prompt"

    private val random = Random()

    /**
     * A random int from zero to this number is requested to determine whether the wifi feedback
     * card should be active. This allows for randomization across attendees.
     */
    private val WIFI_FEEDBACK_RANDOM_INT_UPPER_RANGE = 30

    /**
     * Enum holding all the different kinds of Conference Message Cards that can appear in Explore.
     * For use with hasDismissedConfMessageCard and markDismissedConfMessageCard.
     */
    enum class ConfMessageCard private constructor(startTime: String, endTime: String) {
        /**
         * Card showing information about wristbands & badges.
         */
        CONFERENCE_CREDENTIALS("2015-05-27T09:00:00-07:00", "2015-05-27T18:00:00-07:00"),

        /**
         * Card showing information about getting into the keynote.
         */
        KEYNOTE_ACCESS("2015-05-27T09:00:00-07:00", "2015-05-28T09:30:00-07:00"),

        /**
         * Card showing information about the after hours party.
         */
        AFTER_HOURS("2015-05-28T11:00:00-07:00", "2015-05-28T17:00:00-07:00"),

        /**
         * Card allowing developers to provide feedback for the wifi onsite.
         */
        WIFI_FEEDBACK("2015-05-28T09:30:00-07:00", "2015-05-29T17:30:00-07:00");

        internal var mStartTime: Long = 0
        internal var mEndTime: Long = 0

        init {
            mStartTime = TimeUtils.parseTimestamp(startTime)!!.time
            mEndTime = TimeUtils.parseTimestamp(endTime)!!.time
        }


        fun isActive(millisSinceEpoch: Long): Boolean {
            val returnVal = mStartTime <= millisSinceEpoch && mEndTime >= millisSinceEpoch

            // Wifi card is active randomly to spread the time attendees are shown the cards.
            // TODO: Refactor this into message cards configuration module.
            if (WIFI_FEEDBACK == this) {
                return random.nextInt(WIFI_FEEDBACK_RANDOM_INT_UPPER_RANGE) == 1
            }
            return returnVal
        }
    }

    /**
     * Private mapping of ConfMessageCard enum to strings to be stored as preferences. Preference
     * is a boolean value indicating whether the user has dismissed that particular card yet.
     */
    private val ConfMessageCardsDismissedMap = HashMap<ConfMessageCard, String>()
    private val dismiss_prefix = "pref_conf_message_cards_dismissed_"

    private val ConfMessageCardsShouldShowMap = HashMap<ConfMessageCard, String>()

    private val should_show_prefix = "pref_conf_message_cards_should_show_"

    init {
        ConfMessageCardsDismissedMap.put(ConfMessageCard.CONFERENCE_CREDENTIALS, dismiss_prefix + "conference_credentials")
        ConfMessageCardsDismissedMap.put(ConfMessageCard.KEYNOTE_ACCESS, dismiss_prefix + "keynote_access")
        ConfMessageCardsDismissedMap.put(ConfMessageCard.AFTER_HOURS, dismiss_prefix + "after_hours")
        ConfMessageCardsDismissedMap.put(ConfMessageCard.WIFI_FEEDBACK, dismiss_prefix + "wifi_feedback")

        ConfMessageCardsShouldShowMap.put(ConfMessageCard.CONFERENCE_CREDENTIALS, should_show_prefix + "conference_credentials")
        ConfMessageCardsShouldShowMap.put(ConfMessageCard.KEYNOTE_ACCESS, should_show_prefix + "keynote_access")
        ConfMessageCardsShouldShowMap.put(ConfMessageCard.AFTER_HOURS, should_show_prefix + "after_hours")
        ConfMessageCardsShouldShowMap.put(ConfMessageCard.WIFI_FEEDBACK, should_show_prefix + "wifi_feedback")
    }


    /**
     * Return true if conference info cards are enabled, false if user has disabled them.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun isConfMessageCardsEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_CONF_MESSAGE_CARDS_ENABLED, false)
    }

    /**
     * Set a new value for the conference message cards enabled preference.

     * @param context  Context to be used to edit the [android.content.SharedPreferences].
     * *
     * @param newValue New value to be set, setting this to null results in un-setting the value.
     */
    fun setConfMessageCardsEnabled(context: Context,
                                   newValue: Boolean?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (newValue == null) {
            sp.edit().remove(PREF_CONF_MESSAGE_CARDS_ENABLED).apply()
        } else {
            sp.edit().putBoolean(PREF_CONF_MESSAGE_CARDS_ENABLED, newValue).apply()
        }
    }

    /**
     * Returns true if user already answered the conference info cards prompt, false if they
     * haven't yet.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun hasAnsweredConfMessageCardsPrompt(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_ANSWERED_CONF_MESSAGE_CARDS_PROMPT, false)
    }

    /**
     * Mark that the user has answered the conference info cards prompt so app doesn't bother
     * them again.

     * @param context Context to be used to edit the [android.content.SharedPreferences].
     * *
     * @param newValue New value to be set, setting this to null results in un-setting the value.
     */
    fun markAnsweredConfMessageCardsPrompt(context: Context,
                                           newValue: Boolean?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (newValue == null) {
            sp.edit().remove(PREF_ANSWERED_CONF_MESSAGE_CARDS_PROMPT).apply()
        } else {
            sp.edit().putBoolean(PREF_ANSWERED_CONF_MESSAGE_CARDS_PROMPT, newValue).apply()
        }
    }

    /**
     * Returns true if user has already seen the passed-in message card, false if they haven't yet.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     * *
     * @param card    One of the ConfMessageCard enum values
     */
    fun hasDismissedConfMessageCard(context: Context, card: ConfMessageCard): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(ConfMessageCardsDismissedMap[card], false)
    }

    /**
     * Mark that the user has dismissed one of the conference message cards so the app doesn't
     * show that card to them again.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     * *
     * @param card    One of the ConfMessageCard enum values
     */
    fun markDismissedConfMessageCard(context: Context, card: ConfMessageCard) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(ConfMessageCardsDismissedMap[card], true).apply()
    }

    /**
     * Set the dismissal state of one of the conference message cards.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     * *
     * @param card    One of the ConfMessageCard enum values
     * *
     * @param newValue   True, False, or null to unset.
     */
    fun setDismissedConfMessageCard(context: Context, card: ConfMessageCard,
                                    newValue: Boolean?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (newValue == null) {
            sp.edit().remove(ConfMessageCardsDismissedMap[card]).apply()
        } else {
            sp.edit().putBoolean(ConfMessageCardsDismissedMap[card], newValue).apply()
        }
    }

    /**
     * Indicates whether the app should show the specified message card in the card feed.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     * *
     * @param card    One of the ConfMessageCard enum values
     */
    fun shouldShowConfMessageCard(context: Context, card: ConfMessageCard): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(ConfMessageCardsShouldShowMap[card], false)
    }

    /**
     * Updates the "should show" state of the specified message card. By default, they should not
     * show, but based on external signals, they can be set to be shown.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     * *
     * @param card    One of the ConfMessageCard enum values
     * *
     * @param newValue Value to set or null to unset.
     */
    fun markShouldShowConfMessageCard(context: Context, card: ConfMessageCard,
                                      newValue: Boolean?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)

        if (newValue == null) {
            sp.edit().remove(ConfMessageCardsShouldShowMap[card]).apply()
        } else {
            sp.edit().putBoolean(ConfMessageCardsShouldShowMap[card], newValue).apply()
        }
    }

    /**
     * Register a `listener` which is notified when these settings are changed.

     * @param context A context that has the same lifecycle as the listener that will be returned.
     * *
     * @param listener Listener to register.
     */
    fun registerPreferencesChangeListener(context: Context,
                                          listener: ConferencePrefChangeListener) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Unregister a `listener` which is notified when these settings are changed.

     * @param context A context that has the same lifecycle as the listener that will be returned.
     * *
     * @param listener Listener to unregister.
     */
    fun unregisterPreferencesChangeListener(context: Context,
                                            listener: ConferencePrefChangeListener) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Unset the dismissal state of all conference message cards.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun unsetStateForAllCards(context: Context) {
        for (card in ConfMessageCard.values()) {
            setDismissedConfMessageCard(context, card, null /* new state */)
        }
    }

    /**
     * Mark appropriate cards active.

     * @param context Context to be used to lookup the [android.content.SharedPreferences].
     */
    fun enableActiveCards(context: Context) {
        val currentTime = UIUtils.getCurrentTime(context)
        for (card in ConfMessageCard.values()) {
            if (card.isActive(currentTime)) {
                markShouldShowConfMessageCard(context, card, true)
            }
        }
    }

    /**
     * Class that listens for [ConfMessageCardUtils] specific preferences and calls
     * onPrefChanged with the specific key and value.
     */
    open class ConferencePrefChangeListener : SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            if (PREF_ANSWERED_CONF_MESSAGE_CARDS_PROMPT == key) {
                onPrefChanged(PREF_ANSWERED_CONF_MESSAGE_CARDS_PROMPT,
                        sp.getBoolean(PREF_ANSWERED_CONF_MESSAGE_CARDS_PROMPT, true))
            } else if (PREF_CONF_MESSAGE_CARDS_ENABLED == key) {
                onPrefChanged(PREF_CONF_MESSAGE_CARDS_ENABLED,
                        sp.getBoolean(PREF_CONF_MESSAGE_CARDS_ENABLED, false))
            } else if (key != null && key.startsWith(dismiss_prefix)) {
                onPrefChanged(key, sp.getBoolean(key, false))
            } else if (key != null && key.startsWith(should_show_prefix)) {
                onPrefChanged(key, sp.getBoolean(key, false))
            }
        }

        protected open fun onPrefChanged(key: String, value: Boolean) {
        }
    }
}
