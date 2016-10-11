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

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager
import android.support.annotation.DrawableRes
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.samples.apps.iosched.BuildConfig
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.model.ScheduleItem
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContract.Rooms
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGE
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * An assortment of UI helpers.
 */
object UIUtils {
    private val TAG = makeLogTag(UIUtils::class.java)

    /**
     * Factor applied to session color to derive the background color on panels and when
     * a session photo could not be downloaded (or while it is being downloaded)
     */
    val SESSION_BG_COLOR_SCALE_FACTOR = 0.75f

    private val SESSION_PHOTO_SCRIM_ALPHA = 0.25f // 0=invisible, 1=visible image
    private val SESSION_PHOTO_SCRIM_SATURATION = 0.2f // 0=gray, 1=color image

    /**
     * Flags used with [DateUtils.formatDateRange].
     */
    private val TIME_FLAGS = DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE

    /**
     * Regex to search for HTML escape sequences.

     *
     * Searches for any continuous string of characters starting with an ampersand and ending with a
     * semicolon. (Example: &amp;amp;)
     */
    private val REGEX_HTML_ESCAPE = Pattern.compile(".*&\\S;.*")
    val MOCK_DATA_PREFERENCES = "mock_data"
    val PREFS_MOCK_CURRENT_TIME = "mock_current_time"

    val GOOGLE_PLUS_PACKAGE_NAME = "com.google.android.apps.plus"
    val YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"
    val TWITTER_PACKAGE_NAME = "com.twitter.app"

    val GOOGLE_PLUS_COMMON_NAME = "Google Plus"
    val TWITTER_COMMON_NAME = "Twitter"

    /**
     * Format and return the given session time and [Rooms] values using
     * [Config.CONFERENCE_TIMEZONE].
     */
    @JvmOverloads fun formatSessionSubtitle(intervalStart: Long, intervalEnd: Long, roomName: String?, recycle: StringBuilder,
                                            context: Context, shortFormat: Boolean = false): String {
        var roomName = roomName

        // Determine if the session is in the past
        val currentTimeMillis = UIUtils.getCurrentTime(context)
        val conferenceEnded = currentTimeMillis > Config.CONFERENCE_END_MILLIS
        val sessionEnded = currentTimeMillis > intervalEnd
        if (sessionEnded && !conferenceEnded) {
            return context.getString(R.string.session_finished)
        }

        if (roomName == null) {
            roomName = context.getString(R.string.unknown_room)
        }

        if (shortFormat) {
            val timeZone = SettingsUtils.getDisplayTimeZone(context)
            val intervalStartDate = Date(intervalStart)
            val shortDateFormat = SimpleDateFormat("MMM dd")
            val shortTimeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
            shortDateFormat.timeZone = timeZone
            shortTimeFormat.timeZone = timeZone
            return shortDateFormat.format(intervalStartDate) + " " +
            shortTimeFormat.format(intervalStartDate)
        } else {
            val timeInterval = formatIntervalTimeString(intervalStart, intervalEnd, recycle,
                    context)
            return context.getString(R.string.session_subtitle, timeInterval, roomName)
        }
    }

    /**
     * Format and return the given session speakers and [Rooms] values.
     */
    fun formatSessionSubtitle(roomName: String?, speakerNames: String,
                              context: Context): String {
        var roomName = roomName

        // Determine if the session is in the past
        if (roomName == null) {
            roomName = context.getString(R.string.unknown_room)
        }

        if (!TextUtils.isEmpty(speakerNames)) {
            return speakerNames + "\n" + roomName
        } else {
            return roomName!!
        }
    }

    /**
     * Format and return the given time interval using [Config.CONFERENCE_TIMEZONE]
     * (unless local time was explicitly requested by the user).
     */
    fun formatIntervalTimeString(intervalStart: Long, intervalEnd: Long,
                                 recycle: StringBuilder?, context: Context): String {
        var recycle = recycle
        if (recycle == null) {
            recycle = StringBuilder()
        } else {
            recycle.setLength(0)
        }
        val formatter = Formatter(recycle)
        return DateUtils.formatDateRange(context, formatter, intervalStart, intervalEnd, TIME_FLAGS,
                SettingsUtils.getDisplayTimeZone(context).id).toString()
    }

    fun isSameDayDisplay(time1: Long, time2: Long, context: Context): Boolean {
        val displayTimeZone = SettingsUtils.getDisplayTimeZone(context)
        val cal1 = Calendar.getInstance(displayTimeZone)
        val cal2 = Calendar.getInstance(displayTimeZone)
        cal1.timeInMillis = time1
        cal2.timeInMillis = time2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Populate the given [TextView] with the requested text, formatting
     * through [Html.fromHtml] when applicable. Also sets
     * [TextView.setMovementMethod] so inline links are handled.
     */
    fun setTextMaybeHtml(view: TextView, text: String) {
        if (TextUtils.isEmpty(text)) {
            view.text = ""
            return
        }
        if (text.contains("<") && text.contains(">") || REGEX_HTML_ESCAPE.matcher(text).find()) {
            view.text = Html.fromHtml(text)
            view.movementMethod = LinkMovementMethod.getInstance()
        } else {
            view.text = text
        }
    }

    fun getLiveBadgeText(context: Context, start: Long, end: Long): String {
        val now = getCurrentTime(context)

        if (now < start) {
            // Will be live later
            return context.getString(R.string.live_available)
        } else if (start <= now && now <= end) {
            // Live right now!
            // Indicated by a visual live now badge
            return ""
        } else {
            // Too late.
            return ""
        }
    }

    /**
     * Given a snippet string with matching segments surrounded by curly
     * braces, turn those areas into bold spans, removing the curly braces.
     */
    fun buildStyledSnippet(snippet: String): Spannable {
        val builder = SpannableStringBuilder(snippet)

        // Walk through string, inserting bold snippet spans
        var startIndex: Int = Int.MIN_VALUE
        var endIndex = -1
        var delta = 0
        while (startIndex != -1) {
            startIndex = snippet.indexOf('{', endIndex)
            endIndex = snippet.indexOf('}', startIndex)

            // Remove braces from both sides
            builder.delete(startIndex - delta, startIndex - delta + 1)
            builder.delete(endIndex - delta - 1, endIndex - delta)

            // Insert bold style
            builder.setSpan(StyleSpan(Typeface.BOLD),
                    startIndex - delta, endIndex - delta - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            //builder.setSpan(new ForegroundColorSpan(0xff111111),
            //        startIndex - delta, endIndex - delta - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            delta += 2
        }

        return builder
    }

    /**
     * This allows the app to specify a `packageName` to handle the `intent`, if the
     * `packageName` is available on the device and can handle it. An example use is to open
     * a Google + stream directly using the Google + app.
     */
    fun preferPackageForIntent(context: Context, intent: Intent, packageName: String) {
        val pm = context.packageManager
        if (pm != null) {
            for (resolveInfo in pm.queryIntentActivities(intent, 0)) {
                if (resolveInfo.activityInfo.packageName == packageName) {
                    intent.`package` = packageName
                    break
                }
            }
        }
    }

    private val BRIGHTNESS_THRESHOLD = 130

    /**
     * Calculate whether a color is light or dark, based on a commonly known
     * brightness formula.

     * @see {@literal http://en.wikipedia.org/wiki/HSV_color_space%23Lightness}
     */
    fun isColorDark(color: Int): Boolean {
        return (30 * Color.red(color) +
                59 * Color.green(color) +
                11 * Color.blue(color)) / 100 <= BRIGHTNESS_THRESHOLD
    }

    fun isTablet(context: Context): Boolean {
        return context.resources.configuration.smallestScreenWidthDp >= 600
    }

    // Shows whether a notification was fired for a particular session time block. In the
    // event that notification has not been fired yet, return false and set the bit.
    fun isNotificationFiredForBlock(context: Context, blockId: String): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val key = String.format("notification_fired_%s", blockId)
        val fired = sp.getBoolean(key, false)
        sp.edit().putBoolean(key, true).apply()
        return fired
    }

    private val sAppLoadTime = System.currentTimeMillis()

    /**
     * Retrieve the current time. If the current build is a debug build the mock time is returned
     * when set.
     */
    fun getCurrentTime(context: Context): Long {
        if (BuildConfig.DEBUG) {
            return context.getSharedPreferences(MOCK_DATA_PREFERENCES, Context.MODE_PRIVATE).getLong(PREFS_MOCK_CURRENT_TIME, System.currentTimeMillis()) + System.currentTimeMillis() - sAppLoadTime
        } else {
            return System.currentTimeMillis()
        }
    }

    /**
     * Set the current time only when the current build is a debug build.
     */
    fun setCurrentTime(context: Context, newTime: Long) {
        if (BuildConfig.DEBUG) {
            context.getSharedPreferences(MOCK_DATA_PREFERENCES, Context.MODE_PRIVATE).edit().putLong(PREFS_MOCK_CURRENT_TIME, newTime).apply()
        }
    }

    @Deprecated("")
    fun shouldShowLiveSessionsOnly(context: Context): Boolean {
        return !SettingsUtils.isAttendeeAtVenue(context) && getCurrentTime(context) < Config.CONFERENCE_END_MILLIS
    }

    /**
     * If an activity's intent is for a Google I/O web URL that the app can handle
     * natively, this method translates the intent to the equivalent native intent.
     */
    fun tryTranslateHttpIntent(activity: Activity) {
        val intent = activity.intent ?: return

        val uri = intent.data
        if (uri == null || TextUtils.isEmpty(uri.path)) {
            return
        }

        val sessionDetailWebUrlPrefix = Uri.parse(Config.SESSION_DETAIL_WEB_URL_PREFIX)
        val prefixPath = sessionDetailWebUrlPrefix.path
        val path = uri.path

        if (sessionDetailWebUrlPrefix.scheme == uri.scheme &&
                sessionDetailWebUrlPrefix.host == uri.host &&
                path.startsWith(prefixPath)) {
            val sessionId = path.substring(prefixPath.length)
            activity.intent = Intent(
                    Intent.ACTION_VIEW,
                    ScheduleContract.Sessions.buildSessionUri(sessionId))
        }
    }

    private val RES_IDS_ACTION_BAR_SIZE = intArrayOf(R.attr.actionBarSize)

    /** Calculates the Action Bar height in pixels.  */
    fun calculateActionBarSize(context: Context?): Int {
        if (context == null) {
            return 0
        }

        val curTheme = context.theme ?: return 0

        val att = curTheme.obtainStyledAttributes(RES_IDS_ACTION_BAR_SIZE) ?: return 0

        val size = att.getDimension(0, 0f)
        att.recycle()
        return size.toInt()
    }

    fun setColorOpaque(color: Int): Int {
        return Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun scaleColor(color: Int, factor: Float, scaleAlpha: Boolean): Int {
        return Color.argb(if (scaleAlpha) Math.round(Color.alpha(color) * factor) else Color.alpha(color),
                Math.round(Color.red(color) * factor), Math.round(Color.green(color) * factor),
                Math.round(Color.blue(color) * factor))
    }

    fun scaleSessionColorToDefaultBG(color: Int): Int {
        return scaleColor(color, SESSION_BG_COLOR_SCALE_FACTOR, false)
    }


    fun fireSocialIntent(context: Context, uri: Uri, packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        UIUtils.preferPackageForIntent(context, intent, packageName)
        context.startActivity(intent)
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun isRtl(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return false
        } else {
            return context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        }
    }

    fun setAccessibilityIgnore(view: View) {
        view.isClickable = false
        view.isFocusable = false
        view.contentDescription = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    fun setUpButterBar(butterBar: View?, messageText: String, actionText: String?,
                       listener: View.OnClickListener) {
        if (butterBar == null) {
            LOGE(TAG, "Failed to set up butter bar: it's null.")
            return
        }

        val textView = butterBar.findViewById(R.id.butter_bar_text) as TextView
        if (textView != null) {
            textView.text = messageText
        }

        val button = butterBar.findViewById(R.id.butter_bar_button) as Button
        if (button != null) {
            button.text = actionText ?: ""
            button.visibility = if (!TextUtils.isEmpty(actionText)) View.VISIBLE else View.GONE
        }

        button.setOnClickListener(listener)
        butterBar.visibility = View.VISIBLE
    }

    fun getProgress(value: Int, min: Int, max: Int): Float {
        if (min == max) {
            throw IllegalArgumentException("Max ($max) cannot equal min ($min)")
        }

        return (value - min) / (max - min).toFloat()
    }

    @DrawableRes fun getSessionIcon(sessionType: Int): Int {
        when (sessionType) {
            ScheduleItem.SESSION_TYPE_SESSION -> return R.drawable.ic_session
            ScheduleItem.SESSION_TYPE_CODELAB -> return R.drawable.ic_codelab
            ScheduleItem.SESSION_TYPE_BOXTALK -> return R.drawable.ic_sandbox
            ScheduleItem.SESSION_TYPE_MISC -> return R.drawable.ic_misc
            else -> return R.drawable.ic_misc
        }
    }

    @DrawableRes fun getBreakIcon(breakTitle: String): Int {
        if (!TextUtils.isEmpty(breakTitle)) {
            if (breakTitle.contains("After")) {
                return R.drawable.ic_after_hours
            } else if (breakTitle.contains("Badge")) {
                return R.drawable.ic_badge_pickup
            } else if (breakTitle.contains("Pre-Keynote")) {
                return R.drawable.ic_session
            }
        }
        return R.drawable.ic_food
    }

    /**
     * @param startTime The start time of a session in millis.
     * *
     * @param context The context to be used for getting the display timezone.
     * *
     * @return Formats a given startTime to the specific short time.
     * *         example: 12:00 AM
     */
    fun formatTime(startTime: Long, context: Context): String {
        val sb = StringBuilder()
        DateUtils.formatDateRange(context, Formatter(sb), startTime, startTime,
                DateUtils.FORMAT_SHOW_TIME,
                SettingsUtils.getDisplayTimeZone(context).id)
        return sb.toString()
    }

    /**
     * @param startTime The start time of a session.
     * *
     * @return Returns the Day index such as 1 or 2 based on the given start time.
     */
    fun startTimeToDayIndex(startTime: Long): Int {
        if (startTime <= Config.CONFERENCE_DAYS[0][1] && startTime >= Config.CONFERENCE_DAYS[0][0]) {
            return 1
        } else if (startTime <= Config.CONFERENCE_DAYS[1][1] && startTime >= Config.CONFERENCE_DAYS[1][0]) {
            return 2
        }
        return 0
    }

    // Desaturates and color-scrims the image
    fun makeSessionImageScrimColorFilter(sessionColor: Int): ColorFilter {
        val a = SESSION_PHOTO_SCRIM_ALPHA
        //        return new ColorMatrixColorFilter(new float[]{
        //                a, 0, 0, 0, 0,
        //                0, a, 0, 0, 0,
        //                0, 0, a, 0, 0,
        //                0, 0, 0, 0, 255
        //        });
        //        return new ColorMatrixColorFilter(new float[]{
        //                a, 0, 0, 0, Color.red(sessionColor) * (1 - a),
        //                0, a, 0, 0, Color.green(sessionColor) * (1 - a),
        //                0, 0, a, 0, Color.blue(sessionColor) * (1 - a),
        //                0, 0, 0, 0, 255
        //        });
        //        return new ColorMatrixColorFilter(new float[]{
        //                0.213f * a, 0.715f * a, 0.072f * a, 0, Color.red(sessionColor) * (1 - a),
        //                0.213f * a, 0.715f * a, 0.072f * a, 0, Color.green(sessionColor) * (1 - a),
        //                0.213f * a, 0.715f * a, 0.072f * a, 0, Color.blue(sessionColor) * (1 - a),
        //                0, 0, 0, 0, 255
        //        });
        //        ColorMatrix cm = new ColorMatrix();
        //        cm.setSaturation(0f);
        //        cm.postConcat(alphaMatrix(0.5f, Color.WHITE));
        //        cm.postConcat(multiplyBlendMatrix(sessionColor, 0.9f));
        //        return new ColorMatrixColorFilter(cm);
        val sat = SESSION_PHOTO_SCRIM_SATURATION // saturation (0=gray, 1=color)
        return ColorMatrixColorFilter(floatArrayOf(((1 - 0.213f) * sat + 0.213f) * a, ((0 - 0.715f) * sat + 0.715f) * a, ((0 - 0.072f) * sat + 0.072f) * a, 0f, Color.red(sessionColor) * (1 - a), ((0 - 0.213f) * sat + 0.213f) * a, ((1 - 0.715f) * sat + 0.715f) * a, ((0 - 0.072f) * sat + 0.072f) * a, 0f, Color.green(sessionColor) * (1 - a), ((0 - 0.213f) * sat + 0.213f) * a, ((0 - 0.715f) * sat + 0.715f) * a, ((1 - 0.072f) * sat + 0.072f) * a, 0f, Color.blue(sessionColor) * (1 - a), 0f, 0f, 0f, 0f, 255f))
        //        a = 0.2f;
        //        return new ColorMatrixColorFilter(new float[]{
        //                0.213f * a, 0.715f * a, 0.072f * a, 0, Color.red(sessionColor) - 255 * a / 2,
        //                0.213f * a, 0.715f * a, 0.072f * a, 0, Color.green(sessionColor) - 255 * a / 2,
        //                0.213f * a, 0.715f * a, 0.072f * a, 0, Color.blue(sessionColor) - 255 * a / 2,
        //                0, 0, 0, 0, 255
        //        });
    }

    //    private static final float[] mAlphaMatrixValues = {
    //            0, 0, 0, 0, 0,
    //            0, 0, 0, 0, 0,
    //            0, 0, 0, 0, 0,
    //            0, 0, 0, 1, 0
    //    };
    //    private static final ColorMatrix mMultiplyBlendMatrix = new ColorMatrix();
    //    private static final float[] mMultiplyBlendMatrixValues = {
    //            0, 0, 0, 0, 0,
    //            0, 0, 0, 0, 0,
    //            0, 0, 0, 0, 0,
    //            0, 0, 0, 1, 0
    //    };
    //    private static final ColorMatrix mWhitenessColorMatrix = new ColorMatrix();
    //
    //    /**
    //     * Simulates alpha blending an image with {@param color}.
    //     */
    //    private static ColorMatrix alphaMatrix(float alpha, int color) {
    //        mAlphaMatrixValues[0] = 255 * alpha / 255;
    //        mAlphaMatrixValues[6] = Color.green(color) * alpha / 255;
    //        mAlphaMatrixValues[12] = Color.blue(color) * alpha / 255;
    //        mAlphaMatrixValues[4] = 255 * (1 - alpha);
    //        mAlphaMatrixValues[9] = 255 * (1 - alpha);
    //        mAlphaMatrixValues[14] = 255 * (1 - alpha);
    //        mWhitenessColorMatrix.set(mAlphaMatrixValues);
    //        return mWhitenessColorMatrix;
    //    }
    //    /**
    //     * Simulates multiply blending an image with a single {@param color}.
    //     *
    //     * Multiply blending is [Sa * Da, Sc * Dc]. See {@link android.graphics.PorterDuff}.
    //     */
    //    private static ColorMatrix multiplyBlendMatrix(int color, float alpha) {
    //        mMultiplyBlendMatrixValues[0] = multiplyBlend(Color.red(color), alpha);
    //        mMultiplyBlendMatrixValues[6] = multiplyBlend(Color.green(color), alpha);
    //        mMultiplyBlendMatrixValues[12] = multiplyBlend(Color.blue(color), alpha);
    //        mMultiplyBlendMatrix.set(mMultiplyBlendMatrixValues);
    //        return mMultiplyBlendMatrix;
    //    }
    //
    //    private static float multiplyBlend(int color, float alpha) {
    //        return color * alpha / 255.0f + (1 - alpha);
    //    }

    /**
     * This helper method creates a 'nice' scrim or background protection for layering text over
     * an image. This non-linear scrim is less noticable than a linear or constant one.

     * Borrowed from github.com/romannurik/muzei

     * Creates an approximated cubic gradient using a multi-stop linear gradient. See
     * [this post](https://plus.google.com/+RomanNurik/posts/2QvHVFWrHZf) for more
     * details.
     */
    fun makeCubicGradientScrimDrawable(baseColor: Int, numStops: Int, gravity: Int): Drawable {
        var numStops = numStops
        numStops = Math.max(numStops, 2)

        val paintDrawable = PaintDrawable()
        paintDrawable.shape = RectShape()

        val stopColors = IntArray(numStops)

        val alpha = Color.alpha(baseColor)

        for (i in 0..numStops - 1) {
            val x = (i * 1f / (numStops - 1)).toDouble()
            val opacity = Math.max(0.0, Math.min(1.0, Math.pow(x, 3.0)))
            stopColors[i] = baseColor and 0x00ffffff or ((alpha * opacity).toInt() shl 24)
        }

        val x0: Float
        val x1: Float
        val y0: Float
        val y1: Float
        when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.LEFT -> {
                x0 = 1f
                x1 = 0f
            }
            Gravity.RIGHT -> {
                x0 = 0f
                x1 = 1f
            }
            else -> {
                x0 = 0f
                x1 = 0f
            }
        }
        when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> {
                y0 = 1f
                y1 = 0f
            }
            Gravity.BOTTOM -> {
                y0 = 0f
                y1 = 1f
            }
            else -> {
                y0 = 0f
                y1 = 0f
            }
        }

        paintDrawable.shaderFactory = object : ShapeDrawable.ShaderFactory() {
            override fun resize(width: Int, height: Int): Shader {
                val linearGradient = LinearGradient(
                        width * x0,
                        height * y0,
                        width * x1,
                        height * y1,
                        stopColors, null,
                        Shader.TileMode.CLAMP)
                return linearGradient
            }
        }

        return paintDrawable
    }
}
