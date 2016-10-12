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

package com.google.samples.apps.iosched.io

import android.content.ContentProviderOperation
import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.provider.BaseColumns
import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.io.model.Session
import com.google.samples.apps.iosched.io.model.Speaker
import com.google.samples.apps.iosched.io.model.Tag
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.provider.ScheduleDatabase
import com.google.samples.apps.iosched.util.LogUtils.*
import com.google.samples.apps.iosched.util.TimeUtils
import java.util.*

class SessionsHandler(context: Context) : JSONHandler(context) {
    private val mSessions = HashMap<String, Session>()
    private var mTagMap: HashMap<String, Tag>? = null
    private var mSpeakerMap: HashMap<String, Speaker>? = null
    private val mDefaultSessionColor: Int


    init {
        mDefaultSessionColor = JSONHandler.Companion.mContext!!.resources.getColor(R.color.default_session_color)
    }

    override fun process(element: JsonElement) {
        for (session in Gson().fromJson(element, Array<Session>::class.java)) {
            mSessions.put(session.id, session)
        }
    }

    override fun makeContentProviderOperations(list: ArrayList<ContentProviderOperation>) {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Sessions.CONTENT_URI)

        // build a map of session to session import hashcode so we know what to update,
        // what to insert, and what to delete
        val sessionHashCodes = loadSessionHashCodes()
        val incrementalUpdate = sessionHashCodes != null && sessionHashCodes.size > 0

        // set of sessions that we want to keep after the sync
        val sessionsToKeep = HashSet<String>()

        if (incrementalUpdate) {
            LOGD(TAG, "Doing incremental update for sessions.")
        } else {
            LOGD(TAG, "Doing full (non-incremental) update for sessions.")
            list.add(ContentProviderOperation.newDelete(uri).build())
        }

        var updatedSessions = 0
        for (session in mSessions.values) {
            // Set the session grouping order in the object, so it can be used in hash calculation
            session.groupingOrder = computeTypeOrder(session)

            // compute the incoming session's hashcode to figure out if we need to update
            val hashCode = session.importHashCode
            sessionsToKeep.add(session.id)

            // add session, if necessary
            if (!incrementalUpdate || !sessionHashCodes!!.containsKey(session.id) ||
                    sessionHashCodes[session.id] != hashCode) {
                ++updatedSessions
                val isNew = !incrementalUpdate || !sessionHashCodes!!.containsKey(session.id)
                buildSession(isNew, session, list)

                // add relationships to speakers and track
                buildSessionSpeakerMapping(session, list)
                buildTagsMapping(session, list)
            }
        }

        var deletedSessions = 0
        if (incrementalUpdate) {
            for (sessionId in sessionHashCodes!!.keys) {
                if (!sessionsToKeep.contains(sessionId)) {
                    buildDeleteOperation(sessionId, list)
                    ++deletedSessions
                }
            }
        }

        LOGD(TAG, "Sessions: " + (if (incrementalUpdate) "INCREMENTAL" else "FULL") + " update. " +
                updatedSessions + " to update, " + deletedSessions + " to delete. New total: " +
                mSessions.size)
    }

    private fun buildDeleteOperation(sessionId: String, list: MutableList<ContentProviderOperation>) {
        val sessionUri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Sessions.buildSessionUri(sessionId))
        list.add(ContentProviderOperation.newDelete(sessionUri).build())
    }

    private fun loadSessionHashCodes(): HashMap<String, String>? {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Sessions.CONTENT_URI)
        LOGD(TAG, "Loading session hashcodes for session import optimization.")
        var cursor: Cursor? = null
        try {
            cursor = JSONHandler.Companion.mContext!!.contentResolver.query(uri, SessionHashcodeQuery.PROJECTION,
                    null, null, null)
            if (cursor == null || cursor.count < 1) {
                LOGW(TAG, "Warning: failed to load session hashcodes. Not optimizing session import.")
                return null
            }
            val hashcodeMap = HashMap<String, String>()
            if (cursor.moveToFirst()) {
                do {
                    val sessionId = cursor.getString(SessionHashcodeQuery.SESSION_ID)
                    val hashcode = cursor.getString(SessionHashcodeQuery.SESSION_IMPORT_HASHCODE)
                    hashcodeMap.put(sessionId, hashcode ?: "")
                } while (cursor.moveToNext())
            }
            LOGD(TAG, "Session hashcodes loaded for " + hashcodeMap.size + " sessions.")
            return hashcodeMap
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
    }

    internal var mStringBuilder = StringBuilder()

    private fun buildSession(isInsert: Boolean,
                             session: Session, list: ArrayList<ContentProviderOperation>) {
        val builder: ContentProviderOperation.Builder
        val allSessionsUri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(ScheduleContract.Sessions.CONTENT_URI)
        val thisSessionUri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(ScheduleContract.Sessions.buildSessionUri(
                session.id))

        if (isInsert) {
            builder = ContentProviderOperation.newInsert(allSessionsUri)
        } else {
            builder = ContentProviderOperation.newUpdate(thisSessionUri)
        }

        var speakerNames = ""
        if (mSpeakerMap != null) {
            // build human-readable list of speakers
            mStringBuilder.setLength(0)
            for (i in session.speakers.indices) {
                if (mSpeakerMap!!.containsKey(session.speakers[i])) {
                    mStringBuilder.append(if (i == 0) "" else if (i == session.speakers.size - 1) " and " else ", ").append(mSpeakerMap!![session.speakers[i]]!!.name.trim { it <= ' ' })
                } else {
                    LOGW(TAG, "Unknown speaker ID " + session.speakers[i] + " in session " + session.id)
                }
            }
            speakerNames = mStringBuilder.toString()
        } else {
            LOGE(TAG, "Can't build speaker names -- speaker map is null.")
        }

        var color = mDefaultSessionColor
        try {
            if (!TextUtils.isEmpty(session.color)) {
                color = Color.parseColor(session.color)
            }
        } catch (ex: IllegalArgumentException) {
            LOGD(TAG, "Ignoring invalid formatted session color: " + session.color)
        }

        builder.withValue(ScheduleContract.SyncColumns.UPDATED, System.currentTimeMillis()).withValue(ScheduleContract.Sessions.SESSION_ID, session.id).withValue(ScheduleContract.Sessions.SESSION_LEVEL, null)            // Not available
                .withValue(ScheduleContract.Sessions.SESSION_TITLE, session.title).withValue(ScheduleContract.Sessions.SESSION_ABSTRACT, session.description).withValue(ScheduleContract.Sessions.SESSION_HASHTAG, session.hashtag).withValue(ScheduleContract.Sessions.SESSION_START, TimeUtils.timestampToMillis(session.startTimestamp, 0)).withValue(ScheduleContract.Sessions.SESSION_END, TimeUtils.timestampToMillis(session.endTimestamp, 0)).withValue(ScheduleContract.Sessions.SESSION_TAGS, session.makeTagsList()).withValue(ScheduleContract.Sessions.SESSION_SPEAKER_NAMES, speakerNames)// Note: we store this comma-separated list of tags IN ADDITION
                // to storing the tags in proper relational format (in the sessions_tags
                // relationship table). This is because when querying for sessions,
                // we don't want to incur the performance penalty of having to do a
                // subquery for every record to figure out the list of tags of each session.
                .withValue(ScheduleContract.Sessions.SESSION_KEYWORDS, null)// Note: we store the human-readable list of speakers (which is redundant
                // with the sessions_speakers relationship table) so that we can
                // display it easily in lists without having to make an additional DB query
                // (or another join) for each record.
                // Not available
                .withValue(ScheduleContract.Sessions.SESSION_URL, session.url).withValue(ScheduleContract.Sessions.SESSION_LIVESTREAM_ID,
                if (session.isLivestream) session.youtubeUrl else null).withValue(ScheduleContract.Sessions.SESSION_MODERATOR_URL, null)    // Not available
                .withValue(ScheduleContract.Sessions.SESSION_REQUIREMENTS, null)     // Not available
                .withValue(ScheduleContract.Sessions.SESSION_YOUTUBE_URL,
                        if (session.isLivestream) null else session.youtubeUrl).withValue(ScheduleContract.Sessions.SESSION_PDF_URL, null)          // Not available
                .withValue(ScheduleContract.Sessions.SESSION_NOTES_URL, null)        // Not available
                .withValue(ScheduleContract.Sessions.ROOM_ID, session.room).withValue(ScheduleContract.Sessions.SESSION_GROUPING_ORDER, session.groupingOrder).withValue(ScheduleContract.Sessions.SESSION_IMPORT_HASHCODE,
                session.importHashCode).withValue(ScheduleContract.Sessions.SESSION_MAIN_TAG, session.mainTag).withValue(ScheduleContract.Sessions.SESSION_CAPTIONS_URL, session.captionsUrl).withValue(ScheduleContract.Sessions.SESSION_PHOTO_URL, session.photoUrl).withValue(ScheduleContract.Sessions.SESSION_COLOR, color)// Disabled since this isn't being used by this app.
        // .withValue(ScheduleContract.Sessions.SESSION_RELATED_CONTENT, session.relatedContent)
        list.add(builder.build())
    }

    // The type order of a session is the order# (in its category) of the tag that indicates
    // its type. So if we sort sessions by type order, they will be neatly grouped by type,
    // with the types appearing in the order given by the tag category that represents the
    // concept of session type.
    private fun computeTypeOrder(session: Session): Int {
        var order = Integer.MAX_VALUE
        val keynoteOrder = -1
        if (mTagMap == null) {
            throw IllegalStateException("Attempt to compute type order without tag map.")
        }
        for (tagId in session.tags) {
            if (Config.Tags.SPECIAL_KEYNOTE == tagId) {
                return keynoteOrder
            }
            val tag = mTagMap!![tagId]
            if (tag != null && Config.Tags.SESSION_GROUPING_TAG_CATEGORY == tag.category) {
                if (tag.order_in_category < order) {
                    order = tag.order_in_category
                }
            }
        }
        return order
    }

    private fun buildSessionSpeakerMapping(session: Session,
                                           list: ArrayList<ContentProviderOperation>) {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Sessions.buildSpeakersDirUri(session.id))

        // delete any existing relationship between this session and speakers
        list.add(ContentProviderOperation.newDelete(uri).build())

        // add relationship records to indicate the speakers for this session
        if (session.speakers != null) {
            for (speakerId in session.speakers) {
                list.add(ContentProviderOperation.newInsert(uri).withValue(ScheduleDatabase.SessionsSpeakers.SESSION_ID, session.id).withValue(ScheduleDatabase.SessionsSpeakers.SPEAKER_ID, speakerId).build())
            }
        }
    }

    private fun buildTagsMapping(session: Session, list: ArrayList<ContentProviderOperation>) {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Sessions.buildTagsDirUri(session.id))

        // delete any existing mappings
        list.add(ContentProviderOperation.newDelete(uri).build())

        // add a mapping (a session+tag tuple) for each tag in the session
        for (tag in session.tags) {
            list.add(ContentProviderOperation.newInsert(uri).withValue(ScheduleDatabase.SessionsTags.SESSION_ID, session.id).withValue(ScheduleDatabase.SessionsTags.TAG_ID, tag).build())
        }
    }

    fun setTagMap(tagMap: HashMap<String, Tag>) {
        mTagMap = tagMap
    }

    fun setSpeakerMap(speakerMap: HashMap<String, Speaker>) {
        mSpeakerMap = speakerMap
    }

    private interface SessionHashcodeQuery {
        companion object {
            val PROJECTION = arrayOf(BaseColumns._ID, ScheduleContract.Sessions.SESSION_ID, ScheduleContract.Sessions.SESSION_IMPORT_HASHCODE)
            val _ID = 0
            val SESSION_ID = 1
            val SESSION_IMPORT_HASHCODE = 2
        }
    }

    companion object {
        private val TAG = makeLogTag(SessionsHandler::class.java)
    }
}
