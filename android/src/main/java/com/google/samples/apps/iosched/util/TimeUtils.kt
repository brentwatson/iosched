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

package com.google.samples.apps.iosched.util

import android.content.Context
import android.text.TextUtils
import android.text.format.DateUtils
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.settings.SettingsUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    val SECOND = 1000
    val MINUTE = 60 * SECOND
    val HOUR = 60 * MINUTE
    val DAY = 24 * HOUR

    private val ACCEPTED_TIMESTAMP_FORMATS = arrayOf(SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US), SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US), SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US), SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US), SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US), SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US), SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US), SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss Z", Locale.US))

    private val VALID_IFMODIFIEDSINCE_FORMAT = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    fun parseTimestamp(timestamp: String): Date? {
        for (format in ACCEPTED_TIMESTAMP_FORMATS) {
            // TODO: We shouldn't be forcing the time zone when parsing dates.
            format.timeZone = TimeZone.getTimeZone("GMT")
            try {
                return format.parse(timestamp)
            } catch (ex: ParseException) {
                continue
            }

        }

        // All attempts to parse have failed
        return null
    }

    fun isValidFormatForIfModifiedSinceHeader(timestamp: String): Boolean {
        try {
            return VALID_IFMODIFIEDSINCE_FORMAT.parse(timestamp) != null
        } catch (ex: Exception) {
            return false
        }

    }

    fun timestampToMillis(timestamp: String, defaultValue: Long): Long {
        if (TextUtils.isEmpty(timestamp)) {
            return defaultValue
        }
        val d = parseTimestamp(timestamp)
        return if (d == null) defaultValue else d.time
    }

    /**
     * Format a `date` honoring the app preference for using Conference or device timezone.
     * `Context` is used to lookup the shared preference settings.
     */
    fun formatShortDate(context: Context, date: Date): String {
        val sb = StringBuilder()
        val formatter = Formatter(sb)
        return DateUtils.formatDateRange(context, formatter, date.time, date.time,
                DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_NO_YEAR,
                SettingsUtils.getDisplayTimeZone(context).id).toString()
    }

    fun formatShortTime(context: Context, time: Date): String {
        // Android DateFormatter will honor the user's current settings.
        val format = android.text.format.DateFormat.getTimeFormat(context)
        // Override with Timezone based on settings since users can override their phone's timezone
        // with Pacific time zones.
        val tz = SettingsUtils.getDisplayTimeZone(context)
        if (tz != null) {
            format.timeZone = tz
        }
        return format.format(time)
    }

    fun hasConferenceEnded(context: Context): Boolean {
        val now = UIUtils.getCurrentTime(context)
        return now > Config.CONFERENCE_END_MILLIS
    }

    fun isConferenceInProgress(context: Context): Boolean {
        val now = UIUtils.getCurrentTime(context)
        return now >= Config.CONFERENCE_START_MILLIS && now <= Config.CONFERENCE_END_MILLIS
    }

    /**
     * Returns "Today", "Tomorrow", "Yesterday", or a short date format.
     */
    fun formatHumanFriendlyShortDate(context: Context, timestamp: Long): String {
        val localTimestamp: Long
        val localTime: Long
        val now = UIUtils.getCurrentTime(context)

        val tz = SettingsUtils.getDisplayTimeZone(context)
        localTimestamp = timestamp + tz.getOffset(timestamp)
        localTime = now + tz.getOffset(now)

        val dayOrd = localTimestamp / 86400000L
        val nowOrd = localTime / 86400000L

        if (dayOrd == nowOrd) {
            return context.getString(R.string.day_title_today)
        } else if (dayOrd == nowOrd - 1) {
            return context.getString(R.string.day_title_yesterday)
        } else if (dayOrd == nowOrd + 1) {
            return context.getString(R.string.day_title_tomorrow)
        } else {
            return formatShortDate(context, Date(timestamp))
        }
    }
}
