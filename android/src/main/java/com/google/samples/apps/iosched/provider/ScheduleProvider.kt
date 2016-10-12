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

package com.google.samples.apps.iosched.provider

import android.app.SearchManager
import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.BaseColumns
import android.text.TextUtils
import android.util.Log
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.appwidget.ScheduleWidgetProvider
import com.google.samples.apps.iosched.provider.ScheduleContract.*
import com.google.samples.apps.iosched.provider.ScheduleDatabase.*
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.LogUtils.*
import com.google.samples.apps.iosched.util.SelectionBuilder
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.PrintWriter
import java.util.*

/**
 * [android.content.ContentProvider] that stores [ScheduleContract] data. Data is
 * usually inserted by [com.google.samples.apps.iosched.sync.SyncHelper], and queried using
 * [android.app.LoaderManager] pattern.
 */
class ScheduleProvider : ContentProvider() {

    private var mOpenHelper: ScheduleDatabase? = null

    private var mUriMatcher: ScheduleProviderUriMatcher? = null

    /**
     * Providing important state information to be included in bug reports.

     * !!! Remember !!! Any important data logged to `writer` shouldn't contain personally
     * identifiable information as it can be seen in bugreports.
     */
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<String>) {
        val context = context

        // Using try/catch block in case there are issues retrieving information to log.
        try {
            // Calling append in multiple calls is typically better than creating net new strings to
            // pass to method invocations.
            writer.print("Last sync attempted: ")
            writer.println(java.util.Date(SettingsUtils.getLastSyncAttemptedTime(context!!)))
            writer.print("Last sync successful: ")
            writer.println(java.util.Date(SettingsUtils.getLastSyncSucceededTime(context)))
            writer.print("Current sync interval: ")
            writer.println(SettingsUtils.getCurSyncInterval(context))
            writer.print("Is an account active: ")
            writer.println(AccountUtils.hasActiveAccount(context))
            val canGetAuthToken = !TextUtils.isEmpty(AccountUtils.getAuthToken(context))
            writer.print("Can an auth token be retrieved: ")
            writer.println(canGetAuthToken)

        } catch (exception: Exception) {
            writer.append("Exception while dumping state: ")
            exception.printStackTrace(writer)
        }

    }

    override fun onCreate(): Boolean {
        mOpenHelper = ScheduleDatabase(context)
        mUriMatcher = ScheduleProviderUriMatcher()
        return true
    }

    private fun deleteDatabase() {
        // TODO: wait for content provider operations to finish, then tear down
        mOpenHelper!!.close()
        val context = context
        ScheduleDatabase.deleteDatabase(context)
        mOpenHelper = ScheduleDatabase(getContext())
    }

    /** {@inheritDoc}  */
    override fun getType(uri: Uri): String? {
        val matchingUriEnum = mUriMatcher!!.matchUri(uri)
        return matchingUriEnum.contentType
    }

    /**
     * Returns a tuple of question marks. For example, if `count` is 3, returns "(?,?,?)".
     */
    private fun makeQuestionMarkTuple(count: Int): String {
        if (count < 1) {
            return "()"
        }
        val stringBuilder = StringBuilder()
        stringBuilder.append("(?")
        for (i in 1..count - 1) {
            stringBuilder.append(",?")
        }
        stringBuilder.append(")")
        return stringBuilder.toString()
    }

    /**
     * Adds the `tagsFilter` query parameter to the given `builder`. This query
     * parameter is used by the [com.google.samples.apps.iosched.explore.ExploreSessionsActivity]
     * when the user makes a selection containing multiple filters.
     */
    private fun addTagsFilter(builder: SelectionBuilder, tagsFilter: String, numCategories: String?) {
        // Note: for context, remember that session queries are done on a join of sessions
        // and the sessions_tags relationship table, and are GROUP'ed BY the session ID.
        val requiredTags = tagsFilter.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (requiredTags.size == 0) {
            // filtering by 0 tags -- no-op
            return
        } else if (requiredTags.size == 1) {
            // filtering by only one tag, so a simple WHERE clause suffices
            builder.where(Tags.TAG_ID + "=?", requiredTags[0])
        } else {
            // Filtering by multiple tags, so we must add a WHERE clause with an IN operator,
            // and add a HAVING statement to exclude groups that fall short of the number
            // of required tags. For example, if requiredTags is { "X", "Y", "Z" }, and a certain
            // session only has tags "X" and "Y", it will be excluded by the HAVING statement.
            var categories = 1
            if (numCategories != null && TextUtils.isDigitsOnly(numCategories)) {
                try {
                    categories = Integer.parseInt(numCategories)
                    LOGD(TAG, "Categories being used " + categories)
                } catch (ex: Exception) {
                    LOGE(TAG, "exception parsing categories ", ex)
                }

            }
            val questionMarkTuple = makeQuestionMarkTuple(requiredTags.size)
            builder.where(Tags.TAG_ID + " IN " + questionMarkTuple, *requiredTags)
            builder.having(
                    "COUNT(" + Qualified.SESSIONS_SESSION_ID + ") >= " + categories)
        }
    }

    /** {@inheritDoc}  */
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?,
                       sortOrder: String?): Cursor? {
        var projection = projection
        val db = mOpenHelper!!.readableDatabase

        val tagsFilter = uri.getQueryParameter(Sessions.QUERY_PARAMETER_TAG_FILTER)
        val categories = uri.getQueryParameter(Sessions.QUERY_PARAMETER_CATEGORIES)

        val matchingUriEnum = mUriMatcher!!.matchUri(uri)

        // Avoid the expensive string concatenation below if not loggable.
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "uri=" + uri + " code=" + matchingUriEnum.code + " proj=" +
                    Arrays.toString(projection) + " selection=" + selection + " args="
                    + Arrays.toString(selectionArgs) + ")")
        }

        when (matchingUriEnum) {

            ScheduleUriEnum.SEARCH_SUGGEST -> {
                val builder = SelectionBuilder()

                // Adjust incoming query to become SQL text match.
                selectionArgs!![0] = selectionArgs!![0] + "%"
                builder.table(Tables.SEARCH_SUGGEST)
                builder.where(selection!!, *selectionArgs)
                builder.map(SearchManager.SUGGEST_COLUMN_QUERY,
                        SearchManager.SUGGEST_COLUMN_TEXT_1)

                projection = arrayOf(BaseColumns._ID, SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_QUERY)

                val limit = uri.getQueryParameter(SearchManager.SUGGEST_PARAMETER_LIMIT)
                return builder.query(db, false, projection, SearchSuggest.DEFAULT_SORT, limit)
            }
            ScheduleUriEnum.SEARCH_TOPICS_SESSIONS -> {
                if (selectionArgs == null || selectionArgs.size == 0) {
                    return createMergedSearchCursor(null, null)
                }
                val selectionArg = if (selectionArgs[0] == null) "" else selectionArgs[0]
                // First we query the Tags table to find any tags that match the given query
                val tags = query(Tags.CONTENT_URI, SearchTopicsSessions.TOPIC_TAG_PROJECTION,
                        SearchTopicsSessions.TOPIC_TAG_SELECTION,
                        arrayOf(Config.Tags.CATEGORY_TOPIC, selectionArg + "%"),
                        Tags.TAG_ORDER_BY_CATEGORY)
                // Then we query the sessions_search table and get a list of sessions that match
                // the given keywords.
                var search: Cursor? = null
                if (selectionArgs[0] != null) { // dont query if there was no selectionArg.
                    search = query(ScheduleContract.Sessions.buildSearchUri(selectionArg),
                            SearchTopicsSessions.SEARCH_SESSIONS_PROJECTION,
                            null, null,
                            ScheduleContract.Sessions.SORT_BY_TYPE_THEN_TIME)
                }
                // Now that we have two cursors, we merge the cursors and return a unified view
                // of the two result sets.
                return createMergedSearchCursor(tags, search)
            }
            else -> {
                // Most cases are handled with simple SelectionBuilder.
                val builder = buildExpandedSelection(uri, matchingUriEnum.code)

                // If a special filter was specified, try to apply it.
                if (!TextUtils.isEmpty(tagsFilter) && !TextUtils.isEmpty(categories)) {
                    addTagsFilter(builder, tagsFilter, categories)
                }

                val distinct = ScheduleContractHelper.isQueryDistinct(uri)

                val cursor = builder.where(selection!!, *selectionArgs!!).query(db, distinct, projection, sortOrder!!, null)

                val context = context
                if (null != context) {
                    cursor.setNotificationUri(context.contentResolver, uri)
                }
                return cursor
            }
        }
    }

    /**
     * Create a [MatrixCursor] given the tags and search cursors.
     * @param tags Cursor with the projection [SearchTopicsSessions.TOPIC_TAG_PROJECTION].
     * *
     * @param search Cursor with the projection
     * *              [SearchTopicsSessions.SEARCH_SESSIONS_PROJECTION].
     * *
     * @return Returns a MatrixCursor always with [SearchTopicsSessions.DEFAULT_PROJECTION]
     */
    private fun createMergedSearchCursor(tags: Cursor?, search: Cursor?): Cursor {
        // How big should our MatrixCursor be?
        val maxCount = (if (tags == null) 0 else tags.count) + if (search == null) 0 else search.count

        val matrixCursor = MatrixCursor(
                SearchTopicsSessions.DEFAULT_PROJECTION, maxCount)

        // Iterate over the tags cursor and add rows.
        if (tags != null && tags.moveToFirst()) {
            do {
                matrixCursor.addRow(
                        arrayOf(tags.getLong(0), tags.getString(1), /*tag_id*/
                                "{" + tags.getString(2) + "}", /*search_snippet*/
                                1)) /*is_topic_tag*/
            } while (tags.moveToNext())
        }
        // Iterate over the search cursor and add rows.
        if (search != null && search.moveToFirst()) {
            do {
                matrixCursor.addRow(
                        arrayOf(search.getLong(0), search.getString(1), search.getString(2), /*search_snippet*/
                                0)) /*is_topic_tag*/
            } while (search.moveToNext())
        }
        return matrixCursor
    }

    /** {@inheritDoc}  */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        LOGV(TAG, "insert(uri=" + uri + ", values=" + values!!.toString()
                + ", account=" + getCurrentAccountName(uri, false) + ")")
        val db = mOpenHelper!!.writableDatabase
        val matchingUriEnum = mUriMatcher!!.matchUri(uri)
        if (matchingUriEnum.table != null) {
            db.insertOrThrow(matchingUriEnum.table, null, values)
            notifyChange(uri)
        }

        when (matchingUriEnum) {
            ScheduleUriEnum.BLOCKS -> {
                return Blocks.buildBlockUri(values.getAsString(Blocks.BLOCK_ID))
            }
            ScheduleUriEnum.TAGS -> {
                return Tags.buildTagUri(values.getAsString(Tags.TAG_ID))
            }
            ScheduleUriEnum.ROOMS -> {
                return Rooms.buildRoomUri(values.getAsString(Rooms.ROOM_ID))
            }
            ScheduleUriEnum.SESSIONS -> {
                return Sessions.buildSessionUri(values.getAsString(Sessions.SESSION_ID))
            }
            ScheduleUriEnum.SESSIONS_ID_SPEAKERS -> {
                return Speakers.buildSpeakerUri(values.getAsString(SessionsSpeakers.SPEAKER_ID))
            }
            ScheduleUriEnum.SESSIONS_ID_TAGS -> {
                return Tags.buildTagUri(values.getAsString(Tags.TAG_ID))
            }
            ScheduleUriEnum.MY_SCHEDULE -> {
                values.put(MySchedule.MY_SCHEDULE_ACCOUNT_NAME, getCurrentAccountName(uri, false))
                db.insertOrThrow(Tables.MY_SCHEDULE, null, values)
                notifyChange(uri)
                val sessionUri = Sessions.buildSessionUri(
                        values.getAsString(MyScheduleColumns.SESSION_ID))
                notifyChange(sessionUri)
                return sessionUri
            }
            ScheduleUriEnum.MY_VIEWED_VIDEOS -> {
                values.put(MyViewedVideos.MY_VIEWED_VIDEOS_ACCOUNT_NAME,
                        getCurrentAccountName(uri, false))
                db.insertOrThrow(Tables.MY_VIEWED_VIDEO, null, values)
                notifyChange(uri)
                val videoUri = Videos.buildVideoUri(
                        values.getAsString(MyViewedVideos.VIDEO_ID))
                notifyChange(videoUri)
                return videoUri
            }
            ScheduleUriEnum.MY_FEEDBACK_SUBMITTED -> {
                values.put(MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_ACCOUNT_NAME,
                        getCurrentAccountName(uri, false))
                db.insertOrThrow(Tables.MY_FEEDBACK_SUBMITTED, null, values)
                notifyChange(uri)
                val sessionUri = Sessions.buildSessionUri(
                        values.getAsString(MyFeedbackSubmitted.SESSION_ID))
                notifyChange(sessionUri)
                return sessionUri
            }
            ScheduleUriEnum.SPEAKERS -> {
                return Speakers.buildSpeakerUri(values.getAsString(Speakers.SPEAKER_ID))
            }
            ScheduleUriEnum.ANNOUNCEMENTS -> {
                return Announcements.buildAnnouncementUri(values.getAsString(Announcements.ANNOUNCEMENT_ID))
            }
            ScheduleUriEnum.SEARCH_SUGGEST -> {
                return SearchSuggest.CONTENT_URI
            }
            ScheduleUriEnum.MAPMARKERS -> {
                return MapMarkers.buildMarkerUri(values.getAsString(MapMarkers.MARKER_ID))
            }
            ScheduleUriEnum.MAPTILES -> {
                return MapTiles.buildFloorUri(values.getAsString(MapTiles.TILE_FLOOR))
            }
            ScheduleUriEnum.FEEDBACK_FOR_SESSION -> {
                return Feedback.buildFeedbackUri(values.getAsString(Feedback.SESSION_ID))
            }
            ScheduleUriEnum.HASHTAGS -> {
                return Hashtags.buildHashtagUri(values.getAsString(Hashtags.HASHTAG_NAME))
            }
            ScheduleUriEnum.VIDEOS -> {
                return Videos.buildVideoUri(values.getAsString(Videos.VIDEO_ID))
            }
            else -> {
                throw UnsupportedOperationException("Unknown insert uri: " + uri)
            }
        }
    }

    /** {@inheritDoc}  */
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        val accountName = getCurrentAccountName(uri, false)
        LOGV(TAG, "update(uri=" + uri + ", values=" + values!!.toString()
                + ", account=" + accountName + ")")

        val db = mOpenHelper!!.writableDatabase
        val matchingUriEnum = mUriMatcher!!.matchUri(uri)
        if (matchingUriEnum == ScheduleUriEnum.SEARCH_INDEX) {
            // update the search index
            ScheduleDatabase.updateSessionSearchIndex(db)
            return 1
        }

        val builder = buildSimpleSelection(uri)
        if (matchingUriEnum == ScheduleUriEnum.MY_SCHEDULE) {
            values.remove(MySchedule.MY_SCHEDULE_ACCOUNT_NAME)
            builder.where(MySchedule.MY_SCHEDULE_ACCOUNT_NAME + "=?", accountName)
        }
        if (matchingUriEnum == ScheduleUriEnum.MY_VIEWED_VIDEOS) {
            values.remove(MyViewedVideos.MY_VIEWED_VIDEOS_ACCOUNT_NAME)
            builder.where(MyViewedVideos.MY_VIEWED_VIDEOS_ACCOUNT_NAME + "=?", accountName)
        }
        if (matchingUriEnum == ScheduleUriEnum.MY_FEEDBACK_SUBMITTED) {
            values.remove(MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_ACCOUNT_NAME)
            builder.where(MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_ACCOUNT_NAME + "=?",
                    accountName)
        }

        val retVal = builder.where(selection!!, *selectionArgs!!).update(db, values)
        notifyChange(uri)
        return retVal
    }

    /** {@inheritDoc}  */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val accountName = getCurrentAccountName(uri, false)
        LOGV(TAG, "delete(uri=$uri, account=$accountName)")
        if (uri === ScheduleContract.BASE_CONTENT_URI) {
            // Handle whole database deletes (e.g. when signing out)
            deleteDatabase()
            notifyChange(uri)
            return 1
        }
        val db = mOpenHelper!!.writableDatabase
        val builder = buildSimpleSelection(uri)
        val matchingUriEnum = mUriMatcher!!.matchUri(uri)
        if (matchingUriEnum == ScheduleUriEnum.MY_SCHEDULE) {
            builder.where(MySchedule.MY_SCHEDULE_ACCOUNT_NAME + "=?", accountName)
        }
        if (matchingUriEnum == ScheduleUriEnum.MY_VIEWED_VIDEOS) {
            builder.where(MyViewedVideos.MY_VIEWED_VIDEOS_ACCOUNT_NAME + "=?", accountName)
        }
        if (matchingUriEnum == ScheduleUriEnum.MY_FEEDBACK_SUBMITTED) {
            builder.where(
                    MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_ACCOUNT_NAME + "=?", accountName)
        }

        val retVal = builder.where(selection!!, *selectionArgs!!).delete(db)
        notifyChange(uri)
        return retVal
    }

    /**
     * Notifies the system that the given `uri` data has changed.
     *
     *
     * We only notify changes if the uri wasn't called by the sync adapter, to avoid issuing a large
     * amount of notifications while doing a sync. The
     * [com.google.samples.apps.iosched.sync.ConferenceDataHandler] notifies all top level
     * conference paths once the conference data sync is done, and the
     * [com.google.samples.apps.iosched.sync.userdata.AbstractUserDataSyncHelper] notifies all
     * user data related paths once the user data sync is done.
     */
    private fun notifyChange(uri: Uri) {
        if (!ScheduleContractHelper.isUriCalledFromSyncAdapter(uri)) {
            val context = context
            context!!.contentResolver.notifyChange(uri, null)

            // Widgets can't register content observers so we refresh widgets separately.
            context.sendBroadcast(ScheduleWidgetProvider.getRefreshBroadcastIntent(context, false))
        }
    }

    /**
     * Apply the given set of [ContentProviderOperation], executing inside
     * a [SQLiteDatabase] transaction. All changes will be rolled back if
     * any single one fails.
     */
    @Throws(OperationApplicationException::class)
    override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult?> {
        val db = mOpenHelper!!.writableDatabase
        db.beginTransaction()
        try {
            val numOperations = operations.size
            val results = arrayOfNulls<ContentProviderResult>(numOperations)
            for (i in 0..numOperations - 1) {
                results[i] = operations[i].apply(this, results, i)
            }
            db.setTransactionSuccessful()
            return results
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Build a simple [SelectionBuilder] to match the requested
     * [Uri]. This is usually enough to support [.insert],
     * [.update], and [.delete] operations.
     */
    private fun buildSimpleSelection(uri: Uri): SelectionBuilder {
        val builder = SelectionBuilder()
        val matchingUriEnum = mUriMatcher!!.matchUri(uri)
        // The main Uris, corresponding to the root of each type of Uri, do not have any selection
        // criteria so the full table is used. The others apply a selection criteria.
        when (matchingUriEnum) {
            ScheduleUriEnum.BLOCKS, ScheduleUriEnum.TAGS, ScheduleUriEnum.ROOMS, ScheduleUriEnum.SESSIONS, ScheduleUriEnum.SPEAKERS, ScheduleUriEnum.ANNOUNCEMENTS, ScheduleUriEnum.MAPMARKERS, ScheduleUriEnum.MAPTILES, ScheduleUriEnum.SEARCH_SUGGEST, ScheduleUriEnum.HASHTAGS, ScheduleUriEnum.VIDEOS -> return builder.table(matchingUriEnum.table!!)
            ScheduleUriEnum.BLOCKS_ID -> {
                val blockId = Blocks.getBlockId(uri)
                return builder.table(Tables.BLOCKS).where(Blocks.BLOCK_ID + "=?", blockId)
            }
            ScheduleUriEnum.TAGS_ID -> {
                val tagId = Tags.getTagId(uri)
                return builder.table(Tables.TAGS).where(Tags.TAG_ID + "=?", tagId)
            }
            ScheduleUriEnum.ROOMS_ID -> {
                val roomId = Rooms.getRoomId(uri)
                return builder.table(Tables.ROOMS).where(Rooms.ROOM_ID + "=?", roomId)
            }
            ScheduleUriEnum.SESSIONS_ID -> {
                val sessionId = Sessions.getSessionId(uri)
                return builder.table(Tables.SESSIONS).where(Sessions.SESSION_ID + "=?", sessionId)
            }
            ScheduleUriEnum.SESSIONS_ID_SPEAKERS -> {
                val sessionId = Sessions.getSessionId(uri)
                return builder.table(Tables.SESSIONS_SPEAKERS).where(Sessions.SESSION_ID + "=?", sessionId)
            }
            ScheduleUriEnum.SESSIONS_ID_TAGS -> {
                val sessionId = Sessions.getSessionId(uri)
                return builder.table(Tables.SESSIONS_TAGS).where(Sessions.SESSION_ID + "=?", sessionId)
            }
            ScheduleUriEnum.SESSIONS_MY_SCHEDULE -> {
                val sessionId = Sessions.getSessionId(uri)
                return builder.table(Tables.MY_SCHEDULE).where(ScheduleContract.MyScheduleColumns.SESSION_ID + "=?", sessionId)
            }
            ScheduleUriEnum.MY_SCHEDULE -> {
                return builder.table(Tables.MY_SCHEDULE).where(MySchedule.MY_SCHEDULE_ACCOUNT_NAME + "=?",
                        getCurrentAccountName(uri, false))
            }
            ScheduleUriEnum.MY_VIEWED_VIDEOS -> {
                return builder.table(Tables.MY_VIEWED_VIDEO).where(MyViewedVideos.MY_VIEWED_VIDEOS_ACCOUNT_NAME + "=?",
                        getCurrentAccountName(uri, false))
            }
            ScheduleUriEnum.MY_FEEDBACK_SUBMITTED -> {
                return builder.table(Tables.MY_FEEDBACK_SUBMITTED).where(MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_ACCOUNT_NAME + "=?",
                        getCurrentAccountName(uri, false))
            }
            ScheduleUriEnum.SPEAKERS_ID -> {
                val speakerId = Speakers.getSpeakerId(uri)
                return builder.table(Tables.SPEAKERS).where(Speakers.SPEAKER_ID + "=?", speakerId)
            }
            ScheduleUriEnum.ANNOUNCEMENTS_ID -> {
                val announcementId = Announcements.getAnnouncementId(uri)
                return builder.table(Tables.ANNOUNCEMENTS).where(Announcements.ANNOUNCEMENT_ID + "=?", announcementId)
            }
            ScheduleUriEnum.MAPMARKERS_FLOOR -> {
                val floor = MapMarkers.getMarkerFloor(uri)
                return builder.table(Tables.MAPMARKERS).where(MapMarkers.MARKER_FLOOR + "=?", floor)
            }
            ScheduleUriEnum.MAPMARKERS_ID -> {
                val markerId = MapMarkers.getMarkerId(uri)
                return builder.table(Tables.MAPMARKERS).where(MapMarkers.MARKER_ID + "=?", markerId)
            }
            ScheduleUriEnum.MAPTILES_FLOOR -> {
                val floor = MapTiles.getFloorId(uri)
                return builder.table(Tables.MAPTILES).where(MapTiles.TILE_FLOOR + "=?", floor)
            }
            ScheduleUriEnum.FEEDBACK_FOR_SESSION -> {
                val session_id = Feedback.getSessionId(uri)
                return builder.table(Tables.FEEDBACK).where(Feedback.SESSION_ID + "=?", session_id)
            }
            ScheduleUriEnum.FEEDBACK_ALL -> {
                return builder.table(Tables.FEEDBACK)
            }
            ScheduleUriEnum.HASHTAGS_NAME -> {
                val hashtagName = Hashtags.getHashtagName(uri)
                return builder.table(Tables.HASHTAGS).where(Hashtags.HASHTAG_NAME + "=?", hashtagName)
            }
            ScheduleUriEnum.VIDEOS_ID -> {
                val videoId = Videos.getVideoId(uri)
                return builder.table(Tables.VIDEOS).where(Videos.VIDEO_ID + "=?", videoId)
            }
            else -> {
                throw UnsupportedOperationException("Unknown uri for " + uri)
            }
        }
    }

    private fun getCurrentAccountName(uri: Uri, sanitize: Boolean): String {
        var accountName: String? = ScheduleContractHelper.getOverrideAccountName(uri)
        if (accountName == null) {
            accountName = AccountUtils.getActiveAccountName(context!!)
        }
        if (sanitize) {
            // sanitize accountName when concatenating (http://xkcd.com/327/)
            accountName = if (accountName != null) accountName.replace("'", "''") else null
        }
        return accountName!!
    }

    /**
     * Build an advanced [SelectionBuilder] to match the requested
     * [Uri]. This is usually only used by [.query], since it
     * performs table joins useful for [Cursor] data.
     */
    private fun buildExpandedSelection(uri: Uri, match: Int): SelectionBuilder {
        val builder = SelectionBuilder()
        val matchingUriEnum = mUriMatcher!!.matchCode(match) ?: throw UnsupportedOperationException("Unknown uri: " + uri)
        when (matchingUriEnum) {
            ScheduleUriEnum.BLOCKS -> {
                return builder.table(Tables.BLOCKS)
            }
            ScheduleUriEnum.BLOCKS_BETWEEN -> {
                val segments = uri.pathSegments
                val startTime = segments[2]
                val endTime = segments[3]
                return builder.table(Tables.BLOCKS).where(Blocks.BLOCK_START + ">=?", startTime).where(Blocks.BLOCK_START + "<=?", endTime)
            }
            ScheduleUriEnum.BLOCKS_ID -> {
                val blockId = Blocks.getBlockId(uri)
                return builder.table(Tables.BLOCKS).where(Blocks.BLOCK_ID + "=?", blockId)
            }
            ScheduleUriEnum.TAGS -> {
                return builder.table(Tables.TAGS)
            }
            ScheduleUriEnum.TAGS_ID -> {
                val tagId = Tags.getTagId(uri)
                return builder.table(Tables.TAGS).where(Tags.TAG_ID + "=?", tagId)
            }
            ScheduleUriEnum.ROOMS -> {
                return builder.table(Tables.ROOMS)
            }
            ScheduleUriEnum.ROOMS_ID -> {
                val roomId = Rooms.getRoomId(uri)
                return builder.table(Tables.ROOMS).where(Rooms.ROOM_ID + "=?", roomId)
            }
            ScheduleUriEnum.ROOMS_ID_SESSIONS -> {
                val roomId = Rooms.getRoomId(uri)
                return builder.table(Tables.SESSIONS_JOIN_ROOMS, getCurrentAccountName(uri, true)).mapToTable(Sessions._ID, Tables.SESSIONS).mapToTable(Sessions.ROOM_ID, Tables.SESSIONS).where(Qualified.SESSIONS_ROOM_ID + "=?", roomId).groupBy(Qualified.SESSIONS_SESSION_ID)
            }
            ScheduleUriEnum.SESSIONS -> {
                // We query sessions on the joined table of sessions with rooms and tags.
                // Since there may be more than one tag per session, we GROUP BY session ID.
                // The starred sessions ("my schedule") are associated with a user, so we
                // use the current user to select them properly
                return builder.table(Tables.SESSIONS_JOIN_ROOMS_TAGS, getCurrentAccountName(uri, true)).mapToTable(Sessions._ID, Tables.SESSIONS).mapToTable(Sessions.ROOM_ID, Tables.SESSIONS).mapToTable(Sessions.SESSION_ID, Tables.SESSIONS).map(Sessions.SESSION_IN_MY_SCHEDULE, "IFNULL(in_schedule, 0)").groupBy(Qualified.SESSIONS_SESSION_ID)
            }
            ScheduleUriEnum.SESSIONS_COUNTER -> {
                return builder.table(Tables.SESSIONS_JOIN_MYSCHEDULE, getCurrentAccountName(uri, true)).map(Sessions.SESSION_INTERVAL_COUNT, "count(1)").map(Sessions.SESSION_IN_MY_SCHEDULE, "IFNULL(in_schedule, 0)").groupBy(Sessions.SESSION_START + ", " + Sessions.SESSION_END)
            }
            ScheduleUriEnum.SESSIONS_MY_SCHEDULE -> {
                return builder.table(Tables.SESSIONS_JOIN_ROOMS_TAGS_FEEDBACK_MYSCHEDULE,
                        getCurrentAccountName(uri, true)).mapToTable(Sessions._ID, Tables.SESSIONS).mapToTable(Sessions.ROOM_ID, Tables.SESSIONS).mapToTable(Sessions.SESSION_ID, Tables.SESSIONS).map(Sessions.HAS_GIVEN_FEEDBACK, Subquery.SESSION_HAS_GIVEN_FEEDBACK).map(Sessions.SESSION_IN_MY_SCHEDULE, "IFNULL(in_schedule, 0)").where("( " + Sessions.SESSION_IN_MY_SCHEDULE + "=1 OR " +
                        Sessions.SESSION_TAGS +
                        " LIKE '%" + Config.Tags.SPECIAL_KEYNOTE + "%' )").groupBy(Qualified.SESSIONS_SESSION_ID)
            }
            ScheduleUriEnum.SESSIONS_UNSCHEDULED -> {
                val interval = Sessions.getInterval(uri)
                return builder.table(Tables.SESSIONS_JOIN_ROOMS_TAGS_FEEDBACK_MYSCHEDULE,
                        getCurrentAccountName(uri, true)).mapToTable(Sessions._ID, Tables.SESSIONS).mapToTable(Sessions.ROOM_ID, Tables.SESSIONS).mapToTable(Sessions.SESSION_ID, Tables.SESSIONS).map(Sessions.SESSION_IN_MY_SCHEDULE, "IFNULL(in_schedule, 0)").where(Sessions.SESSION_IN_MY_SCHEDULE + "=0").where(Sessions.SESSION_START + ">=?", interval[0].toString()).where(Sessions.SESSION_START + "<?", interval[1].toString()).groupBy(Qualified.SESSIONS_SESSION_ID)
            }
            ScheduleUriEnum.SESSIONS_SEARCH -> {
                val query = Sessions.getSearchQuery(uri)
                return builder.table(Tables.SESSIONS_SEARCH_JOIN_SESSIONS_ROOMS,
                        getCurrentAccountName(uri, true)).map(Sessions.SEARCH_SNIPPET, Subquery.SESSIONS_SNIPPET).mapToTable(Sessions._ID, Tables.SESSIONS).mapToTable(Sessions.SESSION_ID, Tables.SESSIONS).mapToTable(Sessions.ROOM_ID, Tables.SESSIONS).map(Sessions.SESSION_IN_MY_SCHEDULE, "IFNULL(in_schedule, 0)").where(SessionsSearchColumns.BODY + " MATCH ?", query!!)
            }
            ScheduleUriEnum.SESSIONS_AT -> {
                val segments = uri.pathSegments
                val time = segments[2]
                return builder.table(Tables.SESSIONS_JOIN_ROOMS, getCurrentAccountName(uri, true)).mapToTable(Sessions._ID, Tables.SESSIONS).mapToTable(Sessions.ROOM_ID, Tables.SESSIONS).where(Sessions.SESSION_START + "<=?", time).where(Sessions.SESSION_END + ">=?", time)
            }
            ScheduleUriEnum.SESSIONS_ID -> {
                val sessionId = Sessions.getSessionId(uri)
                return builder.table(Tables.SESSIONS_JOIN_ROOMS, getCurrentAccountName(uri, true)).mapToTable(Sessions._ID, Tables.SESSIONS).mapToTable(Sessions.ROOM_ID, Tables.SESSIONS).mapToTable(Sessions.SESSION_ID, Tables.SESSIONS).map(Sessions.SESSION_IN_MY_SCHEDULE, "IFNULL(in_schedule, 0)").where(Qualified.SESSIONS_SESSION_ID + "=?", sessionId)
            }
            ScheduleUriEnum.SESSIONS_ID_SPEAKERS -> {
                val sessionId = Sessions.getSessionId(uri)
                return builder.table(Tables.SESSIONS_SPEAKERS_JOIN_SPEAKERS).mapToTable(Speakers._ID, Tables.SPEAKERS).mapToTable(Speakers.SPEAKER_ID, Tables.SPEAKERS).where(Qualified.SESSIONS_SPEAKERS_SESSION_ID + "=?", sessionId)
            }
            ScheduleUriEnum.SESSIONS_ID_TAGS -> {
                val sessionId = Sessions.getSessionId(uri)
                return builder.table(Tables.SESSIONS_TAGS_JOIN_TAGS).mapToTable(Tags._ID, Tables.TAGS).mapToTable(Tags.TAG_ID, Tables.TAGS).where(Qualified.SESSIONS_TAGS_SESSION_ID + "=?", sessionId)
            }
            ScheduleUriEnum.SESSIONS_ROOM_AFTER -> {
                val room = Sessions.getRoom(uri)
                val time = Sessions.getAfterForRoom(uri)
                return builder.table(Tables.SESSIONS_JOIN_ROOMS_TAGS, getCurrentAccountName(uri, true)).mapToTable(Sessions._ID, Tables.SESSIONS).mapToTable(Sessions.ROOM_ID, Tables.SESSIONS).mapToTable(Sessions.SESSION_ID, Tables.SESSIONS).where(Qualified.SESSIONS_ROOM_ID + "=?", room).where("(" + Sessions.SESSION_START + "<= ? AND " + Sessions.SESSION_END +
                        " >= ?) OR (" + Sessions.SESSION_START + " >= ?)", time,
                        time,
                        time).map(Sessions.SESSION_IN_MY_SCHEDULE, "IFNULL(in_schedule, 0)").groupBy(Qualified.SESSIONS_SESSION_ID)
            }
            ScheduleUriEnum.SESSIONS_AFTER -> {
                val time = Sessions.getAfter(uri)
                return builder.table(Tables.SESSIONS_JOIN_ROOMS_TAGS, getCurrentAccountName(uri, true)).mapToTable(Sessions._ID, Tables.SESSIONS).mapToTable(Sessions.SESSION_ID, Tables.SESSIONS).mapToTable(Sessions.ROOM_ID, Tables.SESSIONS).map(Sessions.SESSION_IN_MY_SCHEDULE, "IFNULL(in_schedule, 0)").where("(" + Sessions.SESSION_START + "<= ? AND " + Sessions.SESSION_END +
                        " >= ?) OR (" + Sessions.SESSION_START + " >= ?)", time,
                        time, time).groupBy(Qualified.SESSIONS_SESSION_ID)
            }
            ScheduleUriEnum.SPEAKERS -> {
                return builder.table(Tables.SPEAKERS)
            }
            ScheduleUriEnum.MY_SCHEDULE -> {
                // force a where condition to avoid leaking schedule info to another account
                // Note that, since SelectionBuilder always join multiple where calls using AND,
                // even if malicious code specifying additional conditions on account_name won't
                // be able to fetch data from a different account.
                return builder.table(Tables.MY_SCHEDULE).where(MySchedule.MY_SCHEDULE_ACCOUNT_NAME + "=?",
                        getCurrentAccountName(uri, true))
            }
            ScheduleUriEnum.MY_FEEDBACK_SUBMITTED -> {
                // force a where condition to avoid leaking schedule info to another account
                // Note that, since SelectionBuilder always join multiple where calls using AND,
                // even if malicious code specifying additional conditions on account_name won't
                // be able to fetch data from a different account.
                return builder.table(Tables.MY_FEEDBACK_SUBMITTED).where(MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_ACCOUNT_NAME + "=?",
                        getCurrentAccountName(uri, true))
            }
            ScheduleUriEnum.MY_VIEWED_VIDEOS -> {
                // force a where condition to avoid leaking schedule info to another account
                // Note that, since SelectionBuilder always join multiple where calls using AND,
                // even if malicious code specifying additional conditions on account_name won't
                // be able to fetch data from a different account.
                return builder.table(Tables.MY_VIEWED_VIDEO).where(MyViewedVideos.MY_VIEWED_VIDEOS_ACCOUNT_NAME + "=?",
                        getCurrentAccountName(uri, true))
            }
            ScheduleUriEnum.SPEAKERS_ID -> {
                val speakerId = Speakers.getSpeakerId(uri)
                return builder.table(Tables.SPEAKERS).where(Speakers.SPEAKER_ID + "=?", speakerId)
            }
            ScheduleUriEnum.SPEAKERS_ID_SESSIONS -> {
                val speakerId = Speakers.getSpeakerId(uri)
                return builder.table(Tables.SESSIONS_SPEAKERS_JOIN_SESSIONS_ROOMS).mapToTable(Sessions._ID, Tables.SESSIONS).mapToTable(Sessions.SESSION_ID, Tables.SESSIONS).mapToTable(Sessions.ROOM_ID, Tables.SESSIONS).where(Qualified.SESSIONS_SPEAKERS_SPEAKER_ID + "=?", speakerId)
            }
            ScheduleUriEnum.ANNOUNCEMENTS -> {
                return builder.table(Tables.ANNOUNCEMENTS)
            }
            ScheduleUriEnum.ANNOUNCEMENTS_ID -> {
                val announcementId = Announcements.getAnnouncementId(uri)
                return builder.table(Tables.ANNOUNCEMENTS).where(Announcements.ANNOUNCEMENT_ID + "=?", announcementId)
            }
            ScheduleUriEnum.MAPMARKERS -> {
                return builder.table(Tables.MAPMARKERS)
            }
            ScheduleUriEnum.MAPMARKERS_FLOOR -> {
                val floor = MapMarkers.getMarkerFloor(uri)
                return builder.table(Tables.MAPMARKERS).where(MapMarkers.MARKER_FLOOR + "=?", floor)
            }
            ScheduleUriEnum.MAPMARKERS_ID -> {
                val roomId = MapMarkers.getMarkerId(uri)
                return builder.table(Tables.MAPMARKERS).where(MapMarkers.MARKER_ID + "=?", roomId)
            }
            ScheduleUriEnum.MAPTILES -> {
                return builder.table(Tables.MAPTILES)
            }
            ScheduleUriEnum.MAPTILES_FLOOR -> {
                val floor = MapTiles.getFloorId(uri)
                return builder.table(Tables.MAPTILES).where(MapTiles.TILE_FLOOR + "=?", floor)
            }
            ScheduleUriEnum.FEEDBACK_FOR_SESSION -> {
                val sessionId = Feedback.getSessionId(uri)
                return builder.table(Tables.FEEDBACK).where(Feedback.SESSION_ID + "=?", sessionId)
            }
            ScheduleUriEnum.FEEDBACK_ALL -> {
                return builder.table(Tables.FEEDBACK)
            }
            ScheduleUriEnum.HASHTAGS -> {
                return builder.table(Tables.HASHTAGS)
            }
            ScheduleUriEnum.HASHTAGS_NAME -> {
                val hashtagName = Hashtags.getHashtagName(uri)
                return builder.table(Tables.HASHTAGS).where(HashtagColumns.HASHTAG_NAME + "=?", hashtagName)
            }
            ScheduleUriEnum.VIDEOS -> {
                return builder.table(Tables.VIDEOS)
            }
            ScheduleUriEnum.VIDEOS_ID -> {
                val videoId = Videos.getVideoId(uri)
                return builder.table(Tables.VIDEOS).where(VideoColumns.VIDEO_ID + "=?", videoId)
            }
            else -> {
                throw UnsupportedOperationException("Unknown uri: " + uri)
            }
        }
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        throw UnsupportedOperationException("openFile is not supported for " + uri)
    }

    private interface Subquery {
        companion object {
            val SESSION_HAS_GIVEN_FEEDBACK = "(SELECT COUNT(1) FROM " +
            Tables.FEEDBACK + " WHERE " + Qualified.FEEDBACK_SESSION_ID + "=" +
            Qualified.SESSIONS_SESSION_ID + ")"

            val SESSIONS_SNIPPET = "snippet(" + Tables.SESSIONS_SEARCH + ",'{','}','\u2026')"
        }
    }

    /**
     * [ScheduleContract] fields that are fully qualified with a specific
     * parent [Tables]. Used when needed to work around SQL ambiguity.
     */
    private interface Qualified {
        companion object {
            val SESSIONS_SESSION_ID = Tables.SESSIONS + "." + Sessions.SESSION_ID
            val SESSIONS_ROOM_ID = Tables.SESSIONS + "." + Sessions.ROOM_ID
            val SESSIONS_TAGS_SESSION_ID = Tables.SESSIONS_TAGS + "." +
            ScheduleDatabase.SessionsTags.SESSION_ID

            val SESSIONS_SPEAKERS_SESSION_ID = Tables.SESSIONS_SPEAKERS + "."  +
            SessionsSpeakers.SESSION_ID

            val SESSIONS_SPEAKERS_SPEAKER_ID = Tables.SESSIONS_SPEAKERS + "."  +
            SessionsSpeakers.SPEAKER_ID

            val FEEDBACK_SESSION_ID = Tables.FEEDBACK + "." + Feedback.SESSION_ID
        }
    }

    companion object {

        private val TAG = makeLogTag(ScheduleProvider::class.java)
    }
}
