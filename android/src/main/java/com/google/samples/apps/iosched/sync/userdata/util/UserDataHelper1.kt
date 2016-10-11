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
package com.google.samples.apps.iosched.sync.userdata.util

import android.content.Context
import android.net.Uri
import com.google.common.base.Charsets
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.sync.userdata.UserAction
import java.util.*

/**
 * Helper class to handle the format of the User Data that is stored into AppData.
 */
object UserDataHelper {

    /** JSON Attribute name of the starred sessions values.  */
    const internal val JSON_ATTRIBUTE_STARRED_SESSIONS = "starred_sessions"

    /** JSON Attribute name of the GCM Key value.  */
    const internal val JSON_ATTRIBUTE_GCM_KEY = "gcm_key"

    /** JSON Attribute name of the feedback submitted for sessions values.  */
    const internal val JSON_ATTRIBUTE_FEEDBACK_SUBMITTED_SESSIONS = "feedback_submitted_sessions"

    /** JSON Attribute name of the viewed videos values.  */
    const internal val JSON_ATTRIBUTE_VIEWED_VIDEOS = "viewed_videos"

    /**
     * Returns a JSON string representation of the given UserData object.
     */
    fun toJsonString(userData: UserData): String {
        return Gson().toJson(userData)
    }

    /**
     * Returns the JSON string representation of the given UserData object as a byte array.
     */
    fun toByteArray(userData: UserData): ByteArray {
        return toJsonString(userData).toByteArray(Charsets.UTF_8)
    }

    /**
     * Deserializes the UserData given as a JSON string into a [UserData] object.
     */
    fun fromString(str: String?): UserData {
        if (str == null || str.isEmpty()) {
            return UserData()
        }
        return Gson().fromJson(str, UserData::class.java)
    }

    /**
     * Creates a UserData object from the given List of user actions.
     */
    fun getUserData(actions: List<UserAction>?): UserData {
        val userData = UserData()
        if (actions != null) {
            for (action in actions) {
                if (action.type == UserAction.TYPE.ADD_STAR) {
                    if (userData.starredSessionIds == null) {
                        userData.starredSessionIds = HashSet<String>()
                    }
                    userData.starredSessionIds!!.add(action.sessionId!!)
                } else if (action.type == UserAction.TYPE.VIEW_VIDEO) {
                    if (userData.viewedVideoIds == null) {
                        userData.viewedVideoIds = HashSet<String>()
                    }
                    userData.viewedVideoIds!!.add(action.videoId!!)
                } else if (action.type == UserAction.TYPE.SUBMIT_FEEDBACK) {
                    if (userData.feedbackSubmittedSessionIds == null) {
                        userData.feedbackSubmittedSessionIds = HashSet<String>()
                    }
                    userData.feedbackSubmittedSessionIds!!.add(action.sessionId!!)
                }
            }
        }
        return userData
    }

    /**
     * Reads the data from the `column` of the content's `queryUri` and returns it as an
     * Array.
     */
    private fun getColumnContentAsArray(context: Context, queryUri: Uri,
                                        column: String): MutableSet<String> {
        val cursor = context.contentResolver.query(queryUri,
                arrayOf(column), null, null, null)
        val columnValues = HashSet<String>()
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    columnValues.add(cursor.getString(0))
                } while (cursor.moveToNext())
            }
        } finally {
            cursor?.close()
        }
        return columnValues
    }

    /**
     * Returns the User Data that's on the device's local DB.
     */
    fun getLocalUserData(context: Context): UserData {
        val userData = UserData()

        // Get Starred Sessions.
        userData.starredSessionIds = getColumnContentAsArray(context,
                ScheduleContract.MySchedule.CONTENT_URI, ScheduleContract.MySchedule.SESSION_ID)

        // Get Viewed Videos.
        userData.viewedVideoIds = getColumnContentAsArray(context,
                ScheduleContract.MyViewedVideos.CONTENT_URI,
                ScheduleContract.MyViewedVideos.VIDEO_ID)

        // Get Feedback Submitted Sessions.
        userData.feedbackSubmittedSessionIds = getColumnContentAsArray(context,
                ScheduleContract.MyFeedbackSubmitted.CONTENT_URI,
                ScheduleContract.MyFeedbackSubmitted.SESSION_ID)

        return userData
    }

    /**
     * Writes the given user data into the device's local DB.
     */
    fun setLocalUserData(context: Context, userData: UserData?, accountName: String) {
        if (userData == null) {
            return
        }

        // first clear all stars.
        context.contentResolver.delete(ScheduleContract.MySchedule.CONTENT_URI,
                ScheduleContract.MySchedule.MY_SCHEDULE_ACCOUNT_NAME + " = ?",
                arrayOf(accountName))

        // Now add the ones in sessionIds.
        val actions = ArrayList<UserAction>()
        if (userData.starredSessionIds != null) {
            for (sessionId in userData.starredSessionIds!!) {
                val action = UserAction()
                action.type = UserAction.TYPE.ADD_STAR
                action.sessionId = sessionId
                actions.add(action)
            }
        }

        // first clear all viewed videos.
        context.contentResolver.delete(ScheduleContract.MyViewedVideos.CONTENT_URI,
                ScheduleContract.MyViewedVideos.MY_VIEWED_VIDEOS_ACCOUNT_NAME + " = ?",
                arrayOf(accountName))

        // Now add the viewed videos.
        if (userData.viewedVideoIds != null) {
            for (videoId in userData.viewedVideoIds!!) {
                val action = UserAction()
                action.type = UserAction.TYPE.VIEW_VIDEO
                action.videoId = videoId
                actions.add(action)
            }
        }

        // first clear all feedback submitted videos.
        context.contentResolver.delete(ScheduleContract.MyFeedbackSubmitted.CONTENT_URI,
                ScheduleContract.MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_ACCOUNT_NAME + " = ?",
                arrayOf(accountName))

        // Now add the feedback submitted videos.
        if (userData.feedbackSubmittedSessionIds != null) {
            for (sessionId in userData.feedbackSubmittedSessionIds!!) {
                val action = UserAction()
                action.type = UserAction.TYPE.SUBMIT_FEEDBACK
                action.sessionId = sessionId
                actions.add(action)
            }
        }

        UserActionHelper.updateContentProvider(context, actions, accountName)
    }

    /**
     * Represents all User specific data that can be synchronized on Google Drive App Data.
     */
    class UserData {

        @SerializedName(JSON_ATTRIBUTE_STARRED_SESSIONS)
        var starredSessionIds: MutableSet<String>? = HashSet()
            get() = field

        @SerializedName(JSON_ATTRIBUTE_FEEDBACK_SUBMITTED_SESSIONS)
        var feedbackSubmittedSessionIds: MutableSet<String>? = HashSet()
            get() = field

        @SerializedName(JSON_ATTRIBUTE_VIEWED_VIDEOS)
        var viewedVideoIds: MutableSet<String>? = HashSet()
            get() = field

        @SerializedName(JSON_ATTRIBUTE_GCM_KEY)
        var gcmKey: String? = null
    }
}
