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

package com.google.samples.apps.iosched.sync.userdata.gms

import android.app.PendingIntent
import android.content.Context
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.text.TextUtils
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.drive.Drive
import com.google.android.gms.drive.DriveId
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.sync.userdata.AbstractUserDataSyncHelper
import com.google.samples.apps.iosched.sync.userdata.UserAction
import com.google.samples.apps.iosched.sync.userdata.util.UserDataHelper
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.LogUtils.*
import java.io.IOException
import java.util.*


/**
 * Helper class that syncs user data with Google Drive AppData folder using the Google Play services
 * Drive API.
 */
class GMSUserDataSyncHelper(context: Context, accountName: String) : AbstractUserDataSyncHelper(context, accountName) {

    private var mGoogleApiClient: GoogleApiClient? = null

    /**
     * Synchronization steps:
     * 1. If there are local changes, sync the latest local version with remote
     * and ignore merge conflicts. The last write wins.
     * 2. If there are no local changes, fetch the latest remote version. If
     * it includes changes, notify that preferences have changed.
     */
    override fun syncImpl(actions: List<UserAction>, hasPendingLocalData: Boolean): Boolean {
        try {
            setupApiClient()
            val helper = DriveHelper(mGoogleApiClient!!)
            val result = helper.connectIfNecessary()
            if (result != null && !result.isSuccess) {
                handleConnectionResult(result)
                return false
            }

            // Call and await a sync using DriveApi#requestSync to ensure that the caches
            // are up-to-date.
            val status = helper.requestSync()
            LOGD(TAG, "Drive Request Sync status " + status.isSuccess)
            if (!status.isSuccess && status.hasResolution()) {
                postNotification(status.resolution)
                return false
            }

            val driveId = helper.getOrCreateFile(APPDATA_FILE_NAME, FILE_MIME_TYPE)
            // Get the remote and local user data.
            val remote = UserDataHelper.fromString(
                    fetchRemoteData(helper, driveId))
            val local = UserDataHelper.getUserData(actions)

            val remoteGcmKey = remote.gcmKey
            var localGcmKey = AccountUtils.getGcmKey(mContext, mAccountName)
            LOGD(TAG, "Local GCM key: " + AccountUtils.sanitizeGcmKey(localGcmKey))
            LOGD(TAG, "Remote GCM key: " + if (remoteGcmKey == null)
                "(null)"
            else
                AccountUtils.sanitizeGcmKey(remoteGcmKey))

            // if the remote data came with a GCM key, it should override ours
            if (!TextUtils.isEmpty(remoteGcmKey)) {
                if (remoteGcmKey == localGcmKey) {
                    LOGD(TAG, "Remote GCM key is the same as local, so no action necessary.")
                } else {
                    LOGD(TAG, "Remote GCM key is different from local. OVERRIDING local.")
                    localGcmKey = remoteGcmKey!!
                    AccountUtils.setGcmKey(mContext, mAccountName, localGcmKey)
                }
            }

            // If remote data is the same as local, and the remote end already has a GCM key,
            // there is nothing we need to do.
            if (remote == local && !TextUtils.isEmpty(remoteGcmKey)) {
                LOGD(TAG, "Update is not needed (local is same as remote, and remote has key)")
                return false
            }

            val merged: UserDataHelper.UserData
            if (hasPendingLocalData || TextUtils.isEmpty(remoteGcmKey)) {
                // merge local dirty actions into remote content
                if (hasPendingLocalData) {
                    LOGD(TAG, "Has pending local data, merging.")
                    merged = mergeDirtyActions(actions, remote)
                } else {
                    LOGD(TAG, "No pending local data, just updating remote GCM key.")
                    merged = remote
                }
                // add the GCM key special item
                merged.gcmKey = localGcmKey
                // save to remote
                LOGD(TAG, "Sending user data to Drive, gcm key " + AccountUtils.sanitizeGcmKey(localGcmKey))

                val content = UserDataHelper.toJsonString(merged)
                helper.saveDriveFile(driveId, content)
            } else {
                merged = remote
            }

            UserDataHelper.setLocalUserData(mContext, merged, mAccountName)
            return true

        } catch (ex: IOException) {
            LOGE(TAG, "Could not sync myschedule", ex)
        }

        return false
    }

    private fun postNotification(resolution: PendingIntent) {
        val builder = NotificationCompat.Builder(mContext).setContentTitle(mContext.getString(R.string.app_data_notification_resolution_title)).setContentText(mContext.getString(
                R.string.app_data_notification_resolution_content)).setSmallIcon(R.drawable.ic_stat_notification).setContentIntent(resolution).setAutoCancel(true)
        NotificationManagerCompat.from(mContext).notify(ERROR_NOTIFICATION_ID, builder.build())
    }

    private fun setupApiClient() {
        mGoogleApiClient = GoogleApiClient.Builder(mContext).addApi(Drive.API).addScope(Drive.SCOPE_APPFOLDER).setAccountName(mAccountName).build()
    }

    @Throws(IOException::class)
    private fun fetchRemoteData(helper: DriveHelper, driveId: DriveId): String {
        return helper.getContentsFromDrive(driveId)!!
    }

    private fun handleConnectionResult(result: ConnectionResult) {
        if (result.hasResolution()) {
            postNotification(result.resolution)
        } else {
            // No resolution exists so silently fail. This can happen when a user cancels the OAuth2
            // request. In this case we will rely on the OAuth2 consent prompts the next time the
            // app is started.
        }
    }

    /**
     * Updates the remote preferences file with the given JSON content.
     */
    @Throws(IOException::class)
    private fun mergeDirtyActions(actions: List<UserAction>,
                                  userData: UserDataHelper.UserData): UserDataHelper.UserData {
        // apply "dirty" actions:
        for (action in actions) {
            if (action.requiresSync) {
                if (UserAction.TYPE.ADD_STAR == action.type) {
                    if (userData.starredSessionIds == null) {
                        userData.starredSessionIds = HashSet<String>()
                    }
                    userData.starredSessionIds!!.add(action.sessionId!!)
                } else if (UserAction.TYPE.SUBMIT_FEEDBACK == action.type) {
                    if (userData.feedbackSubmittedSessionIds == null) {
                        userData.feedbackSubmittedSessionIds = HashSet<String>()
                    }
                    userData.feedbackSubmittedSessionIds!!.add(action.sessionId!!)
                } else if (UserAction.TYPE.VIEW_VIDEO == action.type) {
                    if (userData.viewedVideoIds == null) {
                        userData.viewedVideoIds = HashSet<String>()
                    }
                    userData.viewedVideoIds!!.add(action.videoId!!)
                } else {
                    if (userData.starredSessionIds == null) {
                        userData.starredSessionIds = HashSet<String>()
                    }
                    userData.starredSessionIds!!.remove(action.sessionId)
                }
            }
        }
        return userData
    }

    companion object {
        private val TAG = makeLogTag(GMSUserDataSyncHelper::class.java)
        private val APPDATA_FILE_NAME = "user_data.json"
        private val FILE_MIME_TYPE = "application/json"

        private val ERROR_NOTIFICATION_ID = 101
    }
}
