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

package com.google.samples.apps.iosched.feedback


import android.content.Context
import android.content.CursorLoader
import android.content.Loader
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.google.common.annotations.VisibleForTesting
import com.google.samples.apps.iosched.framework.Model
import com.google.samples.apps.iosched.framework.QueryEnum
import com.google.samples.apps.iosched.framework.UserActionEnum
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.util.AnalyticsHelper

class SessionFeedbackModel(private val mSessionUri: Uri, private val mContext: Context, private val mFeedbackHelper: FeedbackHelper) : Model {

    var sessionTitle: String? = null
        private set

    var sessionSpeakers: String? = null
        private set

    override fun getQueries(): Array<out QueryEnum> {
        return SessionFeedbackQueryEnum.values()
    }

    override fun readDataFromCursor(cursor: Cursor, query: QueryEnum): Boolean {
        if (!cursor.moveToFirst()) {
            return false
        }

        if (SessionFeedbackQueryEnum.SESSION == query) {
            sessionTitle = cursor.getString(cursor.getColumnIndex(
                    ScheduleContract.Sessions.SESSION_TITLE))

            sessionSpeakers = cursor.getString(cursor.getColumnIndex(
                    ScheduleContract.Sessions.SESSION_SPEAKER_NAMES))

            return true
        }

        return false
    }

    override fun createCursorLoader(loaderId: Int, uri: Uri, args: Bundle): Loader<Cursor> {
        var loader: CursorLoader? = null
        if (loaderId == SessionFeedbackQueryEnum.SESSION.id) {
            loader = getCursorLoaderInstance(mContext, uri,
                    SessionFeedbackQueryEnum.SESSION.projection, null, null, null)
        }
        return loader!!
    }

    @VisibleForTesting
    fun getCursorLoaderInstance(context: Context, uri: Uri, projection: Array<String>,
                                selection: String?, selectionArgs: Array<String>?, sortOrder: String?): CursorLoader {
        return CursorLoader(context, uri, projection, selection, selectionArgs, sortOrder)
    }

    override fun requestModelUpdate(action: UserActionEnum, args: Bundle?): Boolean {
        if (SessionFeedbackUserActionEnum.SUBMIT == action) {
            mFeedbackHelper.saveSessionFeedback(SessionFeedbackData(getSessionId(mSessionUri),
                    args!!.getInt(DATA_RATING_INT), args.getInt(DATA_SESSION_RELEVANT_ANSWER_INT),
                    args.getInt(DATA_CONTENT_ANSWER_INT), args.getInt(DATA_SPEAKER_ANSWER_INT),
                    args.getString(DATA_COMMENT_STRING)))

            // ANALYTICS EVENT: Send session feedback
            // Contains: Session title.  Feedback is NOT included.
            sendAnalyticsEvent("Session", "Feedback", sessionTitle!!)

            return true
        } else {
            return false
        }
    }

    @VisibleForTesting
    fun getSessionId(uri: Uri): String {
        return ScheduleContract.Sessions.getSessionId(uri)
    }

    @VisibleForTesting
    fun sendAnalyticsEvent(category: String, action: String, label: String) {
        AnalyticsHelper.sendEvent(category, action, label)
    }

    enum class SessionFeedbackQueryEnum private constructor(private val id: Int, private val projection: Array<String>) : QueryEnum {
        SESSION(0, arrayOf(ScheduleContract.Sessions.SESSION_TITLE, ScheduleContract.Sessions.SESSION_SPEAKER_NAMES));

        override fun getId(): Int {
            return id
        }

        override fun getProjection(): Array<String> {
            return projection
        }

    }

    enum class SessionFeedbackUserActionEnum private constructor(private val id: Int) : UserActionEnum {
        SUBMIT(1);

        override fun getId(): Int {
            return id
        }

    }

    class SessionFeedbackData(var sessionId: String, var sessionRating: Int, var sessionRelevantAnswer: Int,
                              var contentAnswer: Int, var speakerAnswer: Int, var comments: String?) {

        override fun toString(): String {
            return "SessionId: " + sessionId +
                    " SessionRating: " + sessionRating +
                    " SessionRelevantAnswer: " + sessionRelevantAnswer +
                    " ContentAnswer: " + contentAnswer +
                    " SpeakerAnswer: " + speakerAnswer +
                    " Comments: " + comments
        }
    }

    companion object {

        val DATA_RATING_INT = "DATA_RATING_INT"

        val DATA_SESSION_RELEVANT_ANSWER_INT = "DATA_SESSION_RELEVANT_ANSWER_INT"

        val DATA_CONTENT_ANSWER_INT = "DATA_CONTENT_ANSWER_INT"

        val DATA_SPEAKER_ANSWER_INT = "DATA_SPEAKER_ANSWER_INT"

        val DATA_COMMENT_STRING = "DATA_COMMENT_STRING"
    }

}