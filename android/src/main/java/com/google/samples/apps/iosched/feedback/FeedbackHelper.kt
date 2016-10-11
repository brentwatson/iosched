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
import android.content.Intent
import android.preference.PreferenceManager
import android.text.TextUtils
import com.google.android.gms.wearable.DataMap
import com.google.samples.apps.iosched.feedback.SessionFeedbackModel.SessionFeedbackData
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.service.SessionAlarmService
import com.google.samples.apps.iosched.util.LogUtils.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A helper class for Session Feedback.
 */
class FeedbackHelper(private val mContext: Context) {

    /**
     * Saves the session feedback using the appropriate content provider, and dismisses the
     * feedback notification.
     */
    fun saveSessionFeedback(data: SessionFeedbackData) {
        if (null == data.comments) {
            data.comments = ""
        }

        val answers = data.sessionId + ", " + data.sessionRating + ", " + data.sessionRelevantAnswer +
        ", " + data.contentAnswer + ", " + data.speakerAnswer + ", " + data.comments
        LOGD(TAG, answers)

        val values = ContentValues()
        values.put(ScheduleContract.Feedback.SESSION_ID, data.sessionId)
        values.put(ScheduleContract.Feedback.UPDATED, System.currentTimeMillis())
        values.put(ScheduleContract.Feedback.SESSION_RATING, data.sessionRating)
        values.put(ScheduleContract.Feedback.ANSWER_RELEVANCE, data.sessionRelevantAnswer)
        values.put(ScheduleContract.Feedback.ANSWER_CONTENT, data.contentAnswer)
        values.put(ScheduleContract.Feedback.ANSWER_SPEAKER, data.speakerAnswer)
        values.put(ScheduleContract.Feedback.COMMENTS, data.comments)

        val uri = mContext.contentResolver.insert(ScheduleContract.Feedback.buildFeedbackUri(data.sessionId), values)
        LOGD(TAG, if (null == uri) "No feedback was saved" else uri.toString())
        dismissFeedbackNotification(data.sessionId)
    }

    /**
     * Invokes the [FeedbackWearableListenerService] to dismiss the notification on both the device
     * and wear.
     */
    private fun dismissFeedbackNotification(sessionId: String) {
        val dismissalIntent = Intent(mContext, FeedbackWearableListenerService::class.java)
        dismissalIntent.action = SessionAlarmService.ACTION_NOTIFICATION_DISMISSAL
        dismissalIntent.putExtra(SessionAlarmService.KEY_SESSION_ID, sessionId)
        mContext.startService(dismissalIntent)
    }

    /**
     * Whether a feedback notification has already been fired for a particular `sessionId`.

     * @return true if feedback notification has been fired.
     */
    fun isFeedbackNotificationFiredForSession(sessionId: String): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(mContext)
        val key = createKeyForNotification(sessionId)
        return sp.getBoolean(key, false)
    }

    /**
     * Sets feedback notification as fired for a particular `sessionId`.
     */
    fun setFeedbackNotificationAsFiredForSession(sessionId: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(mContext)
        val key = createKeyForNotification(sessionId)
        sp.edit().putBoolean(key, true).apply()
    }

    private fun createKeyForNotification(sessionId: String): String {
        return String.format("feedback_notification_fired_%s", sessionId)
    }

    companion object {

        private val TAG = makeLogTag(FeedbackHelper::class.java)

        /**
         * Returns the path for the session feedback with the given session id, as used when
         * communication with the wear app.
         */
        fun getFeedbackDataPathForWear(sessionId: String): String {
            return SessionAlarmService.PATH_FEEDBACK + "/" + sessionId
        }

        /**
         * Converts the feedback data from a [com.google.android.gms.wearable.DataMap] to a
         * [SessionFeedbackModel.SessionFeedbackData];

         * The `data` is a JSON string. The format of a typical response is:
         * [{"s":"sessionId-1234"},{"q":1,"a":2},{"q":0,"a":1},{"q":3,"a":1},{"q":2,"a":1}]

         * @return SessionFeedbackData object, or null if JSON Parsing error
         */
        fun convertDataMapToFeedbackData(data: DataMap?): SessionFeedbackData? {
            try {
                if (data == null) {
                    LOGE(TAG, "Failed to parse session data, DataMap is null")
                    return null
                }
                val jsonString = data.getString("response")
                if (TextUtils.isEmpty(jsonString)) {
                    LOGE(TAG, "Failed to parse session data, empty json string")
                    return null
                }

                LOGD(TAG, "jsonString is: " + jsonString)

                val jsonArray = JSONArray(jsonString)
                if (jsonArray.length() > 0) {
                    val sessionObj = jsonArray.get(0) as JSONObject
                    val sessionId = sessionObj.getString("s")
                    val answers = IntArray(4)
                    for (i in answers.indices) {
                        answers[i] = -1
                    }
                    for (i in 1..jsonArray.length() - 1) {
                        val answerObj = jsonArray.get(i) as JSONObject
                        val question = answerObj.getInt("q")
                        val answer = answerObj.getInt("a") + 1
                        answers[question] = answer
                    }
                    return SessionFeedbackData(sessionId, answers[0], answers[1], answers[2],
                            answers[3], null)
                }

            } catch (e: JSONException) {
                LOGE(TAG, "Failed to parse the json received from the wear", e)
            }

            return null
        }
    }

}
