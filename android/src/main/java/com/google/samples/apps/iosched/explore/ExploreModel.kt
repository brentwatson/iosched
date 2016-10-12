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

package com.google.samples.apps.iosched.explore

import android.content.Context
import android.content.CursorLoader
import android.content.Loader
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import com.google.common.annotations.VisibleForTesting
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.explore.data.LiveStreamData
import com.google.samples.apps.iosched.explore.data.SessionData
import com.google.samples.apps.iosched.explore.data.ThemeGroup
import com.google.samples.apps.iosched.explore.data.TopicGroup
import com.google.samples.apps.iosched.framework.Model
import com.google.samples.apps.iosched.framework.QueryEnum
import com.google.samples.apps.iosched.framework.UserActionEnum
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.util.LogUtils.*
import com.google.samples.apps.iosched.util.TimeUtils
import com.google.samples.apps.iosched.util.UIUtils
import java.util.*

/**
 * This is an implementation of a [Model] that queries the sessions at Google I/O and extracts
 * the data needed to present the Explore I/O user interface.

 * The process of loading and reading the data is typically done in the lifecycle of a
 * [com.google.samples.apps.iosched.framework.PresenterFragmentImpl].
 */
class ExploreModel(private val mContext: Context) : Model {

    /**
     * Topic groups loaded from the database pre-randomly filtered and stored by topic name.
     */
    private var mTopics: Map<String, TopicGroup> = HashMap()

    /**
     * Theme groups loaded from the database pre-randomly filtered and stored by topic name.
     */
    private var mThemes: Map<String, ThemeGroup> = HashMap()

    var tagTitles: Map<String, String>? = null
        private set

    var keynoteData: SessionData? = null
        private set

    var liveStreamData: LiveStreamData? = null
        private set

    val topics: Collection<TopicGroup>
        get() = mTopics.values

    val themes: Collection<ThemeGroup>
        get() = mThemes.values


    override fun getQueries(): Array<out QueryEnum> {
        return ExploreQueryEnum.values()
    }

    override fun readDataFromCursor(cursor: Cursor?, query: QueryEnum): Boolean {
        LOGD(TAG, "readDataFromCursor")
        if (query === ExploreQueryEnum.SESSIONS) {
            LOGD(TAG, "Reading session data from cursor.")

            // As we go through the session query results we will be collecting X numbers of session
            // data per Topic and Y numbers of sessions per Theme. When new topics or themes are
            // seen a group will be created.

            // As we iterate through the list of sessions we are also watching out for the
            // keynote and any live sessions streaming right now.

            // The following adjusts the theme and topic limits based on whether the attendee is at
            // the venue.
            val atVenue = SettingsUtils.isAttendeeAtVenue(mContext)
            val themeSessionLimit = getThemeSessionLimit(mContext)

            val topicSessionLimit = getTopicSessionLimit(mContext)

            val liveStreamData = LiveStreamData()
            val topicGroups = HashMap<String, TopicGroup>()
            val themeGroups = HashMap<String, ThemeGroup>()

            // Iterating through rows in Sessions query.
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val session = SessionData()
                    populateSessionFromCursorRow(session, cursor)

                    // Sessions missing titles, descriptions, ids, or images aren't eligible for the
                    // Explore screen.
                    if (TextUtils.isEmpty(session.sessionName) ||
                            TextUtils.isEmpty(session.details) ||
                            TextUtils.isEmpty(session.sessionId) ||
                            TextUtils.isEmpty(session.imageUrl)) {
                        continue
                    }

                    if (!atVenue &&
                            !session.isLiveStreamAvailable && !session.isVideoAvailable) {
                        // Skip the opportunity to present the session for those not on site since it
                        // won't be viewable as there is neither a live stream nor video available.
                        continue
                    }

                    val tags = session.tags

                    if (Config.Tags.SPECIAL_KEYNOTE == session.mainTag) {
                        val keynoteData = SessionData()
                        populateSessionFromCursorRow(keynoteData, cursor)
                        rewriteKeynoteDetails(keynoteData)
                        this.keynoteData = keynoteData
                    } else if (session.isLiveStreamNow(mContext)) {
                        liveStreamData.addSessionData(session)
                    }

                    // TODO: Refactor into a system wide way of parsing these tags.
                    if (!TextUtils.isEmpty(tags)) {
                        val tagsTokenizer = StringTokenizer(tags, ",")
                        while (tagsTokenizer.hasMoreTokens()) {
                            val rawTag = tagsTokenizer.nextToken()
                            if (rawTag.startsWith("TOPIC_")) {
                                var topicGroup: TopicGroup? = topicGroups[rawTag]
                                if (topicGroup == null) {
                                    topicGroup = TopicGroup()
                                    topicGroup.title = rawTag
                                    topicGroup.id = rawTag
                                    topicGroups.put(rawTag, topicGroup)
                                }
                                topicGroup.addSessionData(session)

                            } else if (rawTag.startsWith("THEME_")) {
                                var themeGroup: ThemeGroup? = themeGroups[rawTag]
                                if (themeGroup == null) {
                                    themeGroup = ThemeGroup()
                                    themeGroup.title = rawTag
                                    themeGroup.id = rawTag
                                    themeGroups.put(rawTag, themeGroup)
                                }
                                themeGroup.addSessionData(session)
                            }
                        }
                    }
                } while (cursor.moveToNext())
            }

            for (group in themeGroups.values) {
                group.trimSessionData(themeSessionLimit)
            }
            for (group in topicGroups.values) {
                group.trimSessionData(topicSessionLimit)
            }
            if (liveStreamData.sessions.size > 0) {
                this.liveStreamData = liveStreamData
            }
            mThemes = themeGroups
            mTopics = topicGroups
            return true
        } else if (query === ExploreQueryEnum.TAGS) {
            LOGW(TAG, "TAGS query loaded")
            val newTagTitles = HashMap<String, String>()
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val tagId = cursor.getString(cursor.getColumnIndex(
                            ScheduleContract.Tags.TAG_ID))
                    val tagName = cursor.getString(cursor.getColumnIndex(
                            ScheduleContract.Tags.TAG_NAME))
                    newTagTitles.put(tagId, tagName)
                } while (cursor.moveToNext())
                tagTitles = newTagTitles
            }
            return true
        }
        return false
    }

    private fun rewriteKeynoteDetails(keynoteData: SessionData) {
        val startTime: Long
        val endTime: Long
        val currentTime: Long
        currentTime = UIUtils.getCurrentTime(mContext)
        if (keynoteData.startDate != null) {
            startTime = keynoteData.startDate.time
        } else {
            LOGD(TAG, "Keynote start time wasn't set")
            startTime = 0
        }
        if (keynoteData.endDate != null) {
            endTime = keynoteData.endDate.time
        } else {
            LOGD(TAG, "Keynote end time wasn't set")
            endTime = java.lang.Long.MAX_VALUE
        }

        val stringBuilder = StringBuilder()
        if (currentTime >= startTime && currentTime < endTime) {
            stringBuilder.append(mContext.getString(R.string.live_now))
        } else {
            val shortDate = TimeUtils.formatShortDate(mContext, keynoteData.startDate)
            stringBuilder.append(shortDate)

            if (startTime > 0) {
                stringBuilder.append(" / ")
                stringBuilder.append(TimeUtils.formatShortTime(mContext,
                        java.util.Date(startTime)))
            }
        }
        keynoteData.details = stringBuilder.toString()
    }

    private fun populateSessionFromCursorRow(session: SessionData, cursor: Cursor) {
        session.updateData(
                cursor.getString(cursor.getColumnIndex(
                        ScheduleContract.Sessions.SESSION_TITLE)),
                cursor.getString(cursor.getColumnIndex(
                        ScheduleContract.Sessions.SESSION_ABSTRACT)),
                cursor.getString(cursor.getColumnIndex(
                        ScheduleContract.Sessions.SESSION_ID)),
                cursor.getString(cursor.getColumnIndex(
                        ScheduleContract.Sessions.SESSION_PHOTO_URL)),
                cursor.getString(cursor.getColumnIndex(
                        ScheduleContract.Sessions.SESSION_MAIN_TAG)),
                cursor.getLong(cursor.getColumnIndex(
                        ScheduleContract.Sessions.SESSION_START)),
                cursor.getLong(cursor.getColumnIndex(
                        ScheduleContract.Sessions.SESSION_END)),
                cursor.getString(cursor.getColumnIndex(
                        ScheduleContract.Sessions.SESSION_LIVESTREAM_ID)),
                cursor.getString(cursor.getColumnIndex(
                        ScheduleContract.Sessions.SESSION_YOUTUBE_URL)),
                cursor.getString(cursor.getColumnIndex(
                        ScheduleContract.Sessions.SESSION_TAGS)),
                cursor.getLong(cursor.getColumnIndex(
                        ScheduleContract.Sessions.SESSION_IN_MY_SCHEDULE)) == 1L)
    }

    override fun createCursorLoader(loaderId: Int, uri: Uri, args: Bundle?): Loader<Cursor> {
        var loader: CursorLoader? = null

        if (loaderId == ExploreQueryEnum.SESSIONS.id) {

            // Create and return the Loader.
            loader = getCursorLoaderInstance(mContext, uri,
                    ExploreQueryEnum.SESSIONS.projection, null, null,
                    ScheduleContract.Sessions.SORT_BY_TYPE_THEN_TIME)
        } else if (loaderId == ExploreQueryEnum.TAGS.id) {
            LOGW(TAG, "Starting sessions tag query")
            loader = CursorLoader(mContext, ScheduleContract.Tags.CONTENT_URI,
                    ExploreQueryEnum.TAGS.projection, null, null, null)
        } else {
            LOGE(TAG, "Invalid query loaderId: " + loaderId)
        }
        return loader!!
    }

    @VisibleForTesting
    fun getCursorLoaderInstance(context: Context, uri: Uri, projection: Array<String>,
                                selection: String?, selectionArgs: Array<String>?, sortOrder: String): CursorLoader {
        return CursorLoader(context, uri, projection, selection, selectionArgs, sortOrder)
    }

    override fun requestModelUpdate(action: UserActionEnum, args: Bundle?): Boolean {
        return true
    }

    /**
     * Enumeration of the possible queries that can be done by this Model to retrieve data.
     */
    enum class ExploreQueryEnum private constructor(private val id: Int, private val projection: Array<String>) : QueryEnum {

        /**
         * Query that retrieves a list of sessions.

         * Once the data has been loaded it can be retrieved using `getThemes()` and
         * `getTopics()`.
         */
        SESSIONS(0x1, arrayOf(ScheduleContract.Sessions.SESSION_ID, ScheduleContract.Sessions.SESSION_TITLE, ScheduleContract.Sessions.SESSION_ABSTRACT, ScheduleContract.Sessions.SESSION_TAGS, ScheduleContract.Sessions.SESSION_MAIN_TAG, ScheduleContract.Sessions.SESSION_PHOTO_URL, ScheduleContract.Sessions.SESSION_START, ScheduleContract.Sessions.SESSION_END, ScheduleContract.Sessions.SESSION_LIVESTREAM_ID, ScheduleContract.Sessions.SESSION_YOUTUBE_URL, ScheduleContract.Sessions.SESSION_IN_MY_SCHEDULE, ScheduleContract.Sessions.SESSION_START, ScheduleContract.Sessions.SESSION_END)),

        TAGS(0x2, arrayOf(ScheduleContract.Tags.TAG_ID, ScheduleContract.Tags.TAG_NAME));

        override fun getId(): Int {
            return id
        }

        override fun getProjection(): Array<String> {
            return projection
        }
    }

    /**
     * Enumeration of the possible events that a user can trigger that would affect the state of
     * the date of this Model.
     */
    enum class ExploreUserActionEnum private constructor(private val id: Int) : UserActionEnum {
        /**
         * Event that is triggered when a user re-enters the video library this triggers a reload
         * so that we can display another set of randomly selected videos.
         */
        RELOAD(2);

        override fun getId(): Int {
            return id
        }

    }

    companion object {

        private val TAG = makeLogTag(ExploreModel::class.java)

        fun getTopicSessionLimit(context: Context): Int {
            val atVenue = SettingsUtils.isAttendeeAtVenue(context)
            val topicSessionLimit: Int
            if (atVenue) {
                topicSessionLimit = context.resources.getInteger(R.integer.explore_topic_theme_onsite_max_item_count)
            } else {
                topicSessionLimit = 0
            }
            return topicSessionLimit
        }

        fun getThemeSessionLimit(context: Context): Int {
            val atVenue = SettingsUtils.isAttendeeAtVenue(context)
            val themeSessionLimit: Int
            if (atVenue) {
                themeSessionLimit = context.resources.getInteger(R.integer.explore_topic_theme_onsite_max_item_count)
            } else {
                themeSessionLimit = context.resources.getInteger(R.integer.explore_theme_max_item_count_offsite)
            }
            return themeSessionLimit
        }
    }
}
