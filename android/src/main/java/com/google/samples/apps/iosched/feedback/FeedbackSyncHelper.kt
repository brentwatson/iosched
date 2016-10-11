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

package com.google.samples.apps.iosched.feedback

import android.content.ContentValues
import android.content.Context
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.util.LogUtils.*
import java.util.*

/**
 * Provides unidirectional sync from the feedback data provided by the user to the server feedback
 * API.
 */
class FeedbackSyncHelper(internal var mContext:

                         Context, internal var mFeedbackApiHelper: FeedbackApiHelper) {

    fun sync() {
        val cr = mContext.contentResolver
        val newFeedbackUri = ScheduleContract.Feedback.CONTENT_URI
        val c = cr.query(newFeedbackUri,
                null,
                ScheduleContract.Feedback.SYNCED + " = 0",
                null,
                null)
        LOGD(TAG, "Number of unsynced feedbacks: " + c!!.count)
        val questions = HashMap<String, String>()
        val updatedSessions = ArrayList<String>()

        try {
            while (c.moveToNext()) {
                val localSessionId = c.getString(c.getColumnIndex(ScheduleContract.Feedback.SESSION_ID))
                var remoteSessionId = localSessionId
                // EventPoint uses a different Session ID for the keynote than our backend
                if ("__keynote__" == remoteSessionId) {
                    remoteSessionId = "14f5088b-d0e2-e411-b87f-00155d5066d7"
                }

                var data: String

                data = c.getString(c.getColumnIndex(ScheduleContract.Feedback.SESSION_RATING))
                questions.put(
                        QUESTION_KEYS[ScheduleContract.Feedback.SESSION_RATING]!!,
                        RATING_ANSWERS[data]!!)

                data = c.getString(c.getColumnIndex(ScheduleContract.Feedback.ANSWER_RELEVANCE))
                questions.put(
                        QUESTION_KEYS[ScheduleContract.Feedback.ANSWER_RELEVANCE]!!,
                        RELEVANCE_ANSWERS[data]!!)

                data = c.getString(c.getColumnIndex(ScheduleContract.Feedback.ANSWER_CONTENT))
                questions.put(
                        QUESTION_KEYS[ScheduleContract.Feedback.ANSWER_CONTENT]!!,
                        CONTENT_ANSWERS[data]!!)

                data = c.getString(c.getColumnIndex(ScheduleContract.Feedback.ANSWER_SPEAKER))
                questions.put(
                        QUESTION_KEYS[ScheduleContract.Feedback.ANSWER_SPEAKER]!!,
                        SPEAKER_ANSWERS[data]!!)

                data = c.getString(c.getColumnIndex(ScheduleContract.Feedback.COMMENTS))
                questions.put(
                        QUESTION_KEYS[ScheduleContract.Feedback.COMMENTS]!!,
                        data)

                if (mFeedbackApiHelper.sendSessionToServer(remoteSessionId, questions)) {
                    LOGI(TAG, "Successfully updated session " + remoteSessionId)
                    updatedSessions.add(localSessionId)
                } else {
                    LOGE(TAG, "Couldn't update session " + remoteSessionId)
                }
            }
        } catch (e: Exception) {
            LOGE(TAG, "Couldn't read from cursor " + e)
        } finally {
            c.close()
        }

        // Flip the "synced" flag to true for any successfully updated sessions, but leave them
        // in the database to prevent duplicate feedback
        val contentValues = ContentValues()
        contentValues.put(ScheduleContract.Feedback.SYNCED, 1)
        for (sessionId in updatedSessions) {
            cr.update(ScheduleContract.Feedback.buildFeedbackUri(sessionId), contentValues, null, null)
        }

    }

    companion object {
        private val TAG = makeLogTag(FeedbackSyncHelper::class.java)

        private val QUESTION_KEYS = HashMap<String, String>()

        init {
            QUESTION_KEYS.put(ScheduleContract.Feedback.SESSION_RATING, "Q10")
            QUESTION_KEYS.put(ScheduleContract.Feedback.ANSWER_RELEVANCE, "Q20")
            QUESTION_KEYS.put(ScheduleContract.Feedback.ANSWER_CONTENT, "Q30")
            QUESTION_KEYS.put(ScheduleContract.Feedback.ANSWER_SPEAKER, "Q40")
            QUESTION_KEYS.put(ScheduleContract.Feedback.COMMENTS, "Q50")
        }

        private val RATING_ANSWERS = HashMap<String, String>()

        init {
            RATING_ANSWERS.put("1", "aece21ff-2cbe-e411-b87f-00155d5066d7")
            RATING_ANSWERS.put("2", "afce21ff-2cbe-e411-b87f-00155d5066d7")
            RATING_ANSWERS.put("3", "b0ce21ff-2cbe-e411-b87f-00155d5066d7")
            RATING_ANSWERS.put("4", "b1ce21ff-2cbe-e411-b87f-00155d5066d7")
            RATING_ANSWERS.put("5", "b2ce21ff-2cbe-e411-b87f-00155d5066d7")
        }

        private val RELEVANCE_ANSWERS = HashMap<String, String>()

        init {
            RELEVANCE_ANSWERS.put("1", "9bce21ff-2cbe-e411-b87f-00155d5066d7")
            RELEVANCE_ANSWERS.put("2", "9cce21ff-2cbe-e411-b87f-00155d5066d7")
            RELEVANCE_ANSWERS.put("3", "9dce21ff-2cbe-e411-b87f-00155d5066d7")
            RELEVANCE_ANSWERS.put("4", "9ece21ff-2cbe-e411-b87f-00155d5066d7")
            RELEVANCE_ANSWERS.put("5", "9fce21ff-2cbe-e411-b87f-00155d5066d7")
        }

        private val CONTENT_ANSWERS = HashMap<String, String>()

        init {
            CONTENT_ANSWERS.put("1", "a1ce21ff-2cbe-e411-b87f-00155d5066d7")
            CONTENT_ANSWERS.put("2", "a2ce21ff-2cbe-e411-b87f-00155d5066d7")
            CONTENT_ANSWERS.put("3", "a3ce21ff-2cbe-e411-b87f-00155d5066d7")
            CONTENT_ANSWERS.put("4", "a4ce21ff-2cbe-e411-b87f-00155d5066d7")
            CONTENT_ANSWERS.put("5", "a5ce21ff-2cbe-e411-b87f-00155d5066d7")
        }

        private val SPEAKER_ANSWERS = HashMap<String, String>()

        init {
            SPEAKER_ANSWERS.put("1", "a8ce21ff-2cbe-e411-b87f-00155d5066d7")
            SPEAKER_ANSWERS.put("2", "a9ce21ff-2cbe-e411-b87f-00155d5066d7")
            SPEAKER_ANSWERS.put("3", "aace21ff-2cbe-e411-b87f-00155d5066d7")
            SPEAKER_ANSWERS.put("4", "abce21ff-2cbe-e411-b87f-00155d5066d7")
            SPEAKER_ANSWERS.put("5", "acce21ff-2cbe-e411-b87f-00155d5066d7")
        }
    }
}
