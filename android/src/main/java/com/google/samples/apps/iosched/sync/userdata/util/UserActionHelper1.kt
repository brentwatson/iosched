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

import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
import android.os.RemoteException
import android.util.Log
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.sync.userdata.UserAction
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.util.*

/**
 * Helper class to handle the format of the User Actions done on the device.
 */
object UserActionHelper {
    private val TAG = makeLogTag(UserActionHelper::class.java)

    /**
     * Update content providers as a batch command based on the given list of User Actions.
     */
    fun updateContentProvider(context: Context, userActions: List<UserAction>,
                              account: String) {
        val batch = ArrayList<ContentProviderOperation>()
        for (action in userActions) {
            batch.add(createUpdateOperation(context, action, account))
        }
        try {
            context.contentResolver.applyBatch(ScheduleContract.CONTENT_AUTHORITY, batch)
        } catch (e: RemoteException) {
            Log.e(TAG, "Could not apply operations", e)
        } catch (e: OperationApplicationException) {
            Log.e(TAG, "Could not apply operations", e)
        }

    }

    /**
     * Creates the correct content provider update operation depending on the type of the user
     * action.
     */
    private fun createUpdateOperation(context: Context,
                                      action: UserAction, account: String): ContentProviderOperation {
        if (action.type == UserAction.TYPE.ADD_STAR) {
            return ContentProviderOperation.newInsert(
                    ScheduleContractHelper.addOverrideAccountName(
                            ScheduleContract.MySchedule.CONTENT_URI, account)).withValue(ScheduleContract.MySchedule.MY_SCHEDULE_DIRTY_FLAG, "0").withValue(ScheduleContract.MySchedule.SESSION_ID, action.sessionId).build()
        } else if (action.type == UserAction.TYPE.SUBMIT_FEEDBACK) {
            return ContentProviderOperation.newInsert(
                    ScheduleContractHelper.addOverrideAccountName(
                            ScheduleContract.MyFeedbackSubmitted.CONTENT_URI, account)).withValue(ScheduleContract.MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_DIRTY_FLAG, "0").withValue(ScheduleContract.MyFeedbackSubmitted.SESSION_ID, action.sessionId).build()
        } else if (action.type == UserAction.TYPE.VIEW_VIDEO) {
            return ContentProviderOperation.newInsert(
                    ScheduleContractHelper.addOverrideAccountName(
                            ScheduleContract.MyViewedVideos.CONTENT_URI, account)).withValue(ScheduleContract.MyViewedVideos.MY_VIEWED_VIDEOS_DIRTY_FLAG, "0").withValue(ScheduleContract.MyViewedVideos.VIDEO_ID, action.videoId).build()
        } else {
            return ContentProviderOperation.newDelete(
                    ScheduleContractHelper.addOverrideAccountName(
                            ScheduleContract.MySchedule.CONTENT_URI, account)).withSelection(
                    ScheduleContract.MySchedule.SESSION_ID + " = ? AND " +
                            ScheduleContract.MySchedule.MY_SCHEDULE_ACCOUNT_NAME + " = ? ",
                    arrayOf(action.sessionId, account)).build()
        }
    }
}
