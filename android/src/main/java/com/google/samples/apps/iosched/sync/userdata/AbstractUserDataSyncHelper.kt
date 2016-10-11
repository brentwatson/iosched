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

package com.google.samples.apps.iosched.sync.userdata

import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.samples.apps.iosched.appwidget.ScheduleWidgetProvider
import com.google.samples.apps.iosched.framework.QueryEnum
import com.google.samples.apps.iosched.gcm.ServerUtilities
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContract.*
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.util.LogUtils.*
import java.util.*


/**
 * Helper class that syncs user data in a Drive's AppData folder.

 * Protocode:

 * // when user clicks on "star":
 * session UI: run updateSession()
 * this.updateSession():
 * send addstar/removestar to contentProvider
 * send broadcast to update any dependent UI
 * save user actions as pending in shared settings_prefs

 * // on sync
 * syncadapter: call this.sync()
 * this.sync():
 * fetch remote content
 * if pending actions:
 * apply to content and update remote
 * if modified content != last synced content:
 * update contentProvider
 * send broadcast to update any dependent UI


 */
abstract class AbstractUserDataSyncHelper(protected var mContext: Context, protected var mAccountName: String) {

    protected abstract fun syncImpl(actions: List<UserAction>, hasPendingLocalData: Boolean): Boolean

    /**
     * Create a copy of current pending actions and delegate the
     * proper sync'ing to the concrete subclass on the method syncImpl.
     */
    fun sync(): Boolean {
        // Although we have a dirty flag per item, we need all schedule/viewed videos to sync,
        // because it's all sync'ed at once to a file on AppData folder. We only use the dirty flag
        // to decide if the local content was changed or not. If it was, we replace the remote
        // content.
        var hasPendingLocalData = false
        val actions = ArrayList<UserAction>()

        // Get schedule data pending sync.
        val scheduleData = mContext.contentResolver.query(
                MySchedule.buildMyScheduleUri(mAccountName),
                UserDataQueryEnum.MY_SCHEDULE.projection, null, null, null)

        if (scheduleData != null) {
            while (scheduleData.moveToNext()) {
                val userAction = UserAction()
                userAction.sessionId = scheduleData.getString(
                        scheduleData.getColumnIndex(MySchedule.SESSION_ID))
                val inSchedule = scheduleData.getInt(
                        scheduleData.getColumnIndex(MySchedule.MY_SCHEDULE_IN_SCHEDULE))
                if (inSchedule === 0) {
                    userAction.type = UserAction.TYPE.REMOVE_STAR
                } else {
                    userAction.type = UserAction.TYPE.ADD_STAR
                }
                userAction.requiresSync = scheduleData.getInt(
                        scheduleData.getColumnIndex(MySchedule.MY_SCHEDULE_DIRTY_FLAG)) == 1
                actions.add(userAction)
                if (!hasPendingLocalData && userAction.requiresSync) {
                    hasPendingLocalData = true
                }
            }
            scheduleData.close()
        }

        // Get video viewed data pending sync.
        val videoViewed = mContext.contentResolver.query(
                ScheduleContract.MyViewedVideos.buildMyViewedVideosUri(mAccountName),
                UserDataQueryEnum.MY_VIEWED_VIDEO.projection, null, null, null)

        if (videoViewed != null) {
            while (videoViewed.moveToNext()) {
                val userAction = UserAction()
                userAction.videoId = videoViewed.getString(
                        videoViewed.getColumnIndex(MyViewedVideos.VIDEO_ID))
                userAction.type = UserAction.TYPE.VIEW_VIDEO
                userAction.requiresSync = videoViewed.getInt(
                        videoViewed.getColumnIndex(
                                MyViewedVideos.MY_VIEWED_VIDEOS_DIRTY_FLAG)) == 1
                actions.add(userAction)
                if (!hasPendingLocalData && userAction.requiresSync) {
                    hasPendingLocalData = true
                }
            }
            videoViewed.close()
        }

        // Get feedback submitted data pending sync.
        val feedbackSubmitted = mContext.contentResolver.query(
                MyFeedbackSubmitted.buildMyFeedbackSubmittedUri(mAccountName),
                UserDataQueryEnum.MY_FEEDBACK_SUBMITTED.projection, null, null, null)

        if (feedbackSubmitted != null) {
            while (feedbackSubmitted.moveToNext()) {
                val userAction = UserAction()
                userAction.sessionId = feedbackSubmitted.getString(
                        feedbackSubmitted.getColumnIndex(MyFeedbackSubmitted.SESSION_ID))
                userAction.type = UserAction.TYPE.VIEW_VIDEO
                userAction.requiresSync = feedbackSubmitted.getInt(
                        feedbackSubmitted.getColumnIndex(
                                MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_DIRTY_FLAG)) == 1
                actions.add(userAction)
                if (!hasPendingLocalData && userAction.requiresSync) {
                    hasPendingLocalData = true
                }
            }
            feedbackSubmitted.close()
        }


        Log.d(TAG, "Starting Drive AppData sync. hasPendingData = " + hasPendingLocalData)

        val dataChanged = syncImpl(actions, hasPendingLocalData)

        if (hasPendingLocalData) {
            resetDirtyFlag(actions)

            // Notify other devices via GCM.
            ServerUtilities.notifyUserDataChanged(mContext)
        }
        if (dataChanged) {
            LOGD(TAG, "Notifying changes on paths related to user data on Content Resolver.")
            val resolver = mContext.contentResolver
            for (path in ScheduleContract.USER_DATA_RELATED_PATHS) {
                val uri = ScheduleContract.BASE_CONTENT_URI.buildUpon().appendPath(path).build()
                resolver.notifyChange(uri, null)
            }
            mContext.sendBroadcast(ScheduleWidgetProvider.getRefreshBroadcastIntent(mContext, false))
        }
        return dataChanged
    }

    private fun resetDirtyFlag(actions: ArrayList<UserAction>) {
        val ops = ArrayList<ContentProviderOperation>()
        for (action in actions) {

            val baseUri: Uri
            val with: String
            val withSelectionValue: Array<String>
            val dirtyField: String

            if (action.type == UserAction.TYPE.VIEW_VIDEO) {
                baseUri = MyViewedVideos.buildMyViewedVideosUri(mAccountName)
                with = MyViewedVideos.VIDEO_ID + "=?"
                withSelectionValue = arrayOf(action.videoId!!)
                dirtyField = MyViewedVideos.MY_VIEWED_VIDEOS_DIRTY_FLAG
            } else if (action.type == UserAction.TYPE.SUBMIT_FEEDBACK) {
                baseUri = MyFeedbackSubmitted.buildMyFeedbackSubmittedUri(mAccountName)
                with = MyFeedbackSubmitted.SESSION_ID + "=?"
                withSelectionValue = arrayOf(action.sessionId!!)
                dirtyField = MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_DIRTY_FLAG
            } else {
                baseUri = MySchedule.buildMyScheduleUri(mAccountName)
                with = MySchedule.SESSION_ID + "=? AND " +
                MySchedule.MY_SCHEDULE_IN_SCHEDULE + "=?"
                withSelectionValue = arrayOf(action.sessionId!!, if (action.type == UserAction.TYPE.ADD_STAR) "1" else "0")
                dirtyField = MySchedule.MY_SCHEDULE_DIRTY_FLAG
            }

            val op = ContentProviderOperation.newUpdate(
                    ScheduleContractHelper.setUriAsCalledFromSyncAdapter(baseUri)).withSelection(with, withSelectionValue).withValue(dirtyField, 0).build()
            LOGD(TAG, op.toString())
            ops.add(op)
        }
        try {
            val result = mContext.contentResolver.applyBatch(
                    ScheduleContract.CONTENT_AUTHORITY, ops)
            LOGD(TAG, "Result of cleaning dirty flags is " + Arrays.toString(result))
        } catch (ex: Exception) {
            LOGW(TAG, "Could not update dirty flags. Ignoring.", ex)
        }

    }

    private enum class UserDataQueryEnum private constructor(private val id: Int, private val projection: Array<String>) : QueryEnum {
        MY_SCHEDULE(0, arrayOf(MySchedule.SESSION_ID, MySchedule.MY_SCHEDULE_IN_SCHEDULE, MySchedule.MY_SCHEDULE_DIRTY_FLAG)),

        MY_FEEDBACK_SUBMITTED(0, arrayOf(MyFeedbackSubmitted.SESSION_ID, MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_DIRTY_FLAG)),

        MY_VIEWED_VIDEO(0, arrayOf(MyViewedVideos.VIDEO_ID, MyViewedVideos.MY_VIEWED_VIDEOS_DIRTY_FLAG));

        override fun getId(): Int {
            return id
        }

        override fun getProjection(): Array<String> {
            return projection
        }

    }

    companion object {
        private val TAG = makeLogTag(AbstractUserDataSyncHelper::class.java)
    }
}
