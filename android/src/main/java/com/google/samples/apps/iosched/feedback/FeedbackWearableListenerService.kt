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

import android.app.Service
import android.content.Intent
import android.support.v4.app.NotificationManagerCompat
import android.util.Log
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.*
import com.google.samples.apps.iosched.service.SessionAlarmService
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.util.concurrent.TimeUnit

/**
 * A [WearableListenerService] service to receive the session feedback from the wearable
 * device and handle dismissal of notifications by deleting the associated Data Items.
 */
class FeedbackWearableListenerService : WearableListenerService() {

    private var mGoogleApiClient: GoogleApiClient? = null

    override fun onCreate() {
        super.onCreate()
        mGoogleApiClient = GoogleApiClient.Builder(this).addApi(Wearable.API).build()
        mGoogleApiClient!!.connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if (SessionAlarmService.ACTION_NOTIFICATION_DISMISSAL == action) {
                val sessionId = intent.getStringExtra(SessionAlarmService.KEY_SESSION_ID)
                LOGD(TAG, "onStartCommand(): Action = ACTION_NOTIFICATION_DISMISSAL Session: " + sessionId)
                dismissWearableNotification(sessionId)
            }
        }
        return Service.START_NOT_STICKY
    }

    /**
     * Removes the Data Item that was used to create a notification on the watch. By deleting the
     * data item, a [WearableListenerService] on the watch will be notified and the
     * notification on the watch will be removed.
     *
     *
     * Since connection to the Google API client is asynchronous, we spawn a thread and wait for
     * the connection to be established before attempting to use the Google API client.

     * @param sessionId The Session ID of the notification that should be removed
     */
    private fun dismissWearableNotification(sessionId: String) {
        Thread(Runnable {
            if (!mGoogleApiClient!!.isConnected) {
                mGoogleApiClient!!.blockingConnect(
                        FeedbackConstants.GOOGLE_API_CLIENT_CONNECTION_TIMEOUT_S.toLong(), TimeUnit.SECONDS)
            }
            if (!mGoogleApiClient!!.isConnected) {
                Log.e(TAG, "Failed to connect to mGoogleApiClient within "
                        + FeedbackConstants.GOOGLE_API_CLIENT_CONNECTION_TIMEOUT_S + " seconds")
                return@Runnable
            }
            LOGD(TAG, "dismissWearableNotification(): Attempting to dismiss wearable " + "notification")
            val putDataMapRequest = PutDataMapRequest.create(FeedbackHelper.getFeedbackDataPathForWear(sessionId))
            if (mGoogleApiClient!!.isConnected) {
                Wearable.DataApi.deleteDataItems(mGoogleApiClient, putDataMapRequest.uri).setResultCallback { deleteDataItemsResult ->
                    if (!deleteDataItemsResult.status.isSuccess) {
                        LOGD(TAG, "dismissWearableNotification(): failed to delete" + " the data item")
                    }
                }
            } else {
                Log.e(TAG, "dismissWearableNotification()): No Google API Client connection")
            }
        }).start()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer?) {
        LOGD(TAG, "onDataChanged: $dataEvents for $packageName")

        for (event in dataEvents!!) {
            LOGD(TAG, "Uri is: " + event.dataItem.uri)
            val mapItem = DataMapItem.fromDataItem(event.dataItem)
            val path = event.dataItem.uri.path
            if (event.type == DataEvent.TYPE_CHANGED) {
                if (PATH_RESPONSE == path) {
                    // we have a response
                    val data = mapItem.dataMap
                    saveFeedback(data)
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                if (path.startsWith(SessionAlarmService.PATH_FEEDBACK)) {
                    val uri = event.dataItem.uri
                    dismissLocalNotification(uri.lastPathSegment)
                }
            }
        }
    }

    /**
     * Dismisses the local notification for the given session
     */
    private fun dismissLocalNotification(sessionId: String) {
        LOGD(TAG, "dismissLocalNotification: sessionId=" + sessionId)
        NotificationManagerCompat.from(this).cancel(sessionId, SessionAlarmService.FEEDBACK_NOTIFICATION_ID)
    }

    /**
     * This converts the `data` from the wearable app and persists it.

     * @return true if successfully persisted
     */
    private fun saveFeedback(data: DataMap): Boolean {
        val feedback = FeedbackHelper.convertDataMapToFeedbackData(data)
        if (feedback != null) {
            LOGD(TAG, "Feedback answers received from wear: " + feedback.toString())
            val feedbackHelper = FeedbackHelper(this)
            feedbackHelper.saveSessionFeedback(feedback)
            return true
        }
        return false
    }

    companion object {

        private val TAG = makeLogTag(FeedbackWearableListenerService::class.java)

        val PATH_RESPONSE = "/iowear/response"
    }
}
