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

package com.google.samples.apps.iosched.sync

import android.accounts.Account
import android.content.*
import android.net.ConnectivityManager
import android.os.Bundle
import com.google.samples.apps.iosched.BuildConfig
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.feedback.FeedbackApiHelper
import com.google.samples.apps.iosched.feedback.FeedbackSyncHelper
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.service.DataBootstrapService
import com.google.samples.apps.iosched.service.SessionAlarmService
import com.google.samples.apps.iosched.service.SessionCalendarService
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.sync.userdata.AbstractUserDataSyncHelper
import com.google.samples.apps.iosched.sync.userdata.UserDataSyncHelperFactory
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.LogUtils.*
import com.google.samples.apps.iosched.util.UIUtils
import com.turbomanage.httpclient.BasicHttpClient
import java.io.IOException

/**
 * A helper class for dealing with conference data synchronization. All operations occur on the
 * thread they're called from, so it's best to wrap calls in an [android.os.AsyncTask], or
 * better yet, a [android.app.Service].
 */
class SyncHelper
/**

 * @param context Can be Application, Activity or Service context.
 */
(private val mContext: Context) {

    private val mConferenceDataHandler: ConferenceDataHandler

    private val mRemoteDataFetcher: RemoteConferenceDataFetcher

    private val mHttpClient: BasicHttpClient

    init {
        mConferenceDataHandler = ConferenceDataHandler(mContext)
        mRemoteDataFetcher = RemoteConferenceDataFetcher(mContext)
        mHttpClient = BasicHttpClient()
    }

    /**
     * Attempts to perform data synchronization. There are 3 types of data: conference, user
     * schedule and user feedback.
     *
     *
     * The conference data sync is handled by [RemoteConferenceDataFetcher]. For more details
     * about conference data, refer to the documentation at
     * https://github.com/google/iosched/blob/master/doc/SYNC.md. The user schedule data sync is
     * handled by [AbstractUserDataSyncHelper]. The user feedback sync is handled by
     * [FeedbackSyncHelper].


     * @param syncResult The sync result object to update with statistics.
     * *
     * @param account The account associated with this sync
     * *
     * @param extras Specifies additional information about the sync. This must contain key
     * *               `SyncAdapter.EXTRA_SYNC_USER_DATA_ONLY` with boolean value
     * *
     * @return true if the sync changed the data.
     */
    fun performSync(syncResult: SyncResult?, account: Account?, extras: Bundle): Boolean {
        var dataChanged = false

        if (!SettingsUtils.isDataBootstrapDone(mContext)) {
            LOGD(TAG, "Sync aborting (data bootstrap not done yet)")
            // Start the bootstrap process so that the next time sync is called,
            // it is already bootstrapped.
            DataBootstrapService.startDataBootstrapIfNecessary(mContext)
            return false
        }

        val userDataScheduleOnly = extras.getBoolean(SyncAdapter.EXTRA_SYNC_USER_DATA_ONLY, false)

        LOGI(TAG, "Performing sync for account: " + account!!)
        SettingsUtils.markSyncAttemptedNow(mContext)
        var opStart: Long
        val syncDuration: Long
        val choresDuration: Long

        opStart = System.currentTimeMillis()

        // Sync consists of 1 or more of these operations. We try them one by one and tolerate
        // individual failures on each.
        val OP_CONFERENCE_DATA_SYNC = 0
        val OP_USER_SCHEDULE_DATA_SYNC = 1
        val OP_USER_FEEDBACK_DATA_SYNC = 2

        val opsToPerform = if (userDataScheduleOnly)
            intArrayOf(OP_USER_SCHEDULE_DATA_SYNC)
        else
            intArrayOf(OP_CONFERENCE_DATA_SYNC, OP_USER_SCHEDULE_DATA_SYNC, OP_USER_FEEDBACK_DATA_SYNC)

        for (op in opsToPerform) {
            try {
                when (op) {
                    OP_CONFERENCE_DATA_SYNC -> dataChanged = dataChanged or doConferenceDataSync()
                    OP_USER_SCHEDULE_DATA_SYNC -> dataChanged = dataChanged or doUserDataSync(account.name)
                    OP_USER_FEEDBACK_DATA_SYNC ->
                        // User feedback data sync is an outgoing sync only so not affecting
                        // {@code dataChanged} value.
                        doUserFeedbackDataSync()
                }
            } catch (ex: AuthException) {
                syncResult!!.stats.numAuthExceptions++

                // If we have a token, try to refresh it.
                if (AccountUtils.hasToken(mContext, account.name)) {
                    AccountUtils.refreshAuthToken(mContext)
                } else {
                    LOGW(TAG, "No auth token yet for this account. Skipping remote sync.")
                }
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
                LOGE(TAG, "Error performing remote sync.")
                increaseIoExceptions(syncResult)
            }

        }
        syncDuration = System.currentTimeMillis() - opStart

        // If data has changed, there are a few chores we have to do.
        opStart = System.currentTimeMillis()
        if (dataChanged) {
            try {
                performPostSyncChores(mContext)
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
                LOGE(TAG, "Error performing post sync chores.")
            }

        }
        choresDuration = System.currentTimeMillis() - opStart

        val operations = mConferenceDataHandler.contentProviderOperationsDone
        if (syncResult != null && syncResult.stats != null) {
            syncResult.stats.numEntries += operations.toLong()
            syncResult.stats.numUpdates += operations.toLong()
        }

        if (dataChanged) {
            val totalDuration = choresDuration + syncDuration
            LOGD(TAG, "SYNC STATS:\n" +
                    " *  Account synced: " + (if (account == null) "null" else account.name) + "\n" +
                    " *  Content provider operations: " + operations + "\n" +
                    " *  Sync took: " + syncDuration + "ms\n" +
                    " *  Post-sync chores took: " + choresDuration + "ms\n" +
                    " *  Total time: " + totalDuration + "ms\n" +
                    " *  Total data read from cache: \n" +
                    mRemoteDataFetcher.totalBytesReadFromCache / 1024 + "kB\n" +
                    " *  Total data downloaded: \n" +
                    mRemoteDataFetcher.totalBytesDownloaded / 1024 + "kB")
        }

        LOGI(TAG, "End of sync (" + (if (dataChanged) "data changed" else "no data change") + ")")

        updateSyncInterval(mContext, account)

        return dataChanged
    }

    private fun doUserFeedbackDataSync() {
        LOGD(TAG, "Syncing feedback")
        FeedbackSyncHelper(mContext, FeedbackApiHelper(mHttpClient,
                BuildConfig.FEEDBACK_API_ENDPOINT)).sync()
    }

    /**
     * Checks if the remote server has new conference data that we need to import. If so, download
     * the new data and import it into the database.

     * @return Whether or not data was changed.
     * *
     * @throws IOException if there is a problem downloading or importing the data.
     */
    @Throws(IOException::class)
    private fun doConferenceDataSync(): Boolean {
        if (!isOnline) {
            LOGD(TAG, "Not attempting remote sync because device is OFFLINE")
            return false
        }

        LOGD(TAG, "Starting remote sync.")

        // Fetch the remote data files via RemoteConferenceDataFetcher.
        val dataFiles = mRemoteDataFetcher.fetchConferenceDataIfNewer(
                mConferenceDataHandler.dataTimestamp)

        if (dataFiles != null) {
            LOGI(TAG, "Applying remote data.")
            // Save the remote data to the database.
            mConferenceDataHandler.applyConferenceData(dataFiles,
                    mRemoteDataFetcher.serverDataTimestamp!!, true)
            LOGI(TAG, "Done applying remote data.")

            // Mark that conference data sync has succeeded.
            SettingsUtils.markSyncSucceededNow(mContext)
            return true
        } else {
            // No data to process (everything is up to date).
            // Mark that conference data sync succeeded.
            SettingsUtils.markSyncSucceededNow(mContext)
            return false
        }
    }

    /**
     * Checks if there are changes on User's Data to sync with/from remote AppData folder.

     * @return Whether or not data was changed.
     * *
     * @throws IOException if there is a problem uploading the data.
     */
    @Throws(IOException::class)
    private fun doUserDataSync(accountName: String): Boolean {
        if (!isOnline) {
            LOGD(TAG, "Not attempting userdata sync because device is OFFLINE")
            return false
        }

        LOGD(TAG, "Starting user data sync.")

        val helper = UserDataSyncHelperFactory.buildSyncHelper(
                mContext, accountName)
        val modified = helper.sync()
        if (modified) {
            // Schedule notifications for the starred sessions.
            val scheduleIntent = Intent(
                    SessionAlarmService.ACTION_SCHEDULE_ALL_STARRED_BLOCKS,
                    null, mContext, SessionAlarmService::class.java)
            mContext.startService(scheduleIntent)
        }
        return modified
    }

    private val isOnline: Boolean
        get() {
            val cm = mContext.getSystemService(
                    Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return cm.activeNetworkInfo != null && cm.activeNetworkInfo.isConnectedOrConnecting
        }

    private fun increaseIoExceptions(syncResult: SyncResult?) {
        if (syncResult != null && syncResult.stats != null) {
            ++syncResult.stats.numIoExceptions
        }
    }

    class AuthException : RuntimeException()

    companion object {

        private val TAG = makeLogTag(SyncHelper::class.java)

        @JvmOverloads fun requestManualSync(mChosenAccount: Account?, userDataSyncOnly: Boolean = false) {
            if (mChosenAccount != null) {
                LOGD(TAG, "Requesting manual sync for account " + mChosenAccount.name
                        + " userDataSyncOnly=" + userDataSyncOnly)
                val b = Bundle()
                b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                if (userDataSyncOnly) {
                    b.putBoolean(SyncAdapter.EXTRA_SYNC_USER_DATA_ONLY, true)
                }
                ContentResolver.setSyncAutomatically(mChosenAccount, ScheduleContract.CONTENT_AUTHORITY, true)
                ContentResolver.setIsSyncable(mChosenAccount, ScheduleContract.CONTENT_AUTHORITY, 1)

                val pending = ContentResolver.isSyncPending(mChosenAccount,
                        ScheduleContract.CONTENT_AUTHORITY)
                if (pending) {
                    LOGD(TAG, "Warning: sync is PENDING. Will cancel.")
                }
                val active = ContentResolver.isSyncActive(mChosenAccount,
                        ScheduleContract.CONTENT_AUTHORITY)
                if (active) {
                    LOGD(TAG, "Warning: sync is ACTIVE. Will cancel.")
                }

                if (pending || active) {
                    LOGD(TAG, "Cancelling previously pending/active sync.")
                    ContentResolver.cancelSync(mChosenAccount, ScheduleContract.CONTENT_AUTHORITY)
                }

                LOGD(TAG, "Requesting sync now.")
                ContentResolver.requestSync(mChosenAccount, ScheduleContract.CONTENT_AUTHORITY, b)
            } else {
                LOGD(TAG, "Can't request manual sync -- no chosen account.")
            }
        }

        fun performPostSyncChores(context: Context) {
            // Update search index.
            LOGD(TAG, "Updating search index.")
            context.contentResolver.update(ScheduleContract.SearchIndex.CONTENT_URI,
                    ContentValues(), null, null)

            // Sync calendar.
            LOGD(TAG, "Session data changed. Syncing starred sessions with Calendar.")
            syncCalendar(context)
        }

        private fun syncCalendar(context: Context) {
            val intent = Intent(SessionCalendarService.ACTION_UPDATE_ALL_SESSIONS_CALENDAR)
            intent.setClass(context, SessionCalendarService::class.java)
            context.startService(intent)
        }

        private fun calculateRecommendedSyncInterval(context: Context): Long {
            val now = UIUtils.getCurrentTime(context)
            val aroundConferenceStart = Config.CONFERENCE_START_MILLIS - Config.AUTO_SYNC_AROUND_CONFERENCE_THRESH
            if (now < aroundConferenceStart) {
                return Config.AUTO_SYNC_INTERVAL_LONG_BEFORE_CONFERENCE
            } else if (now <= Config.CONFERENCE_END_MILLIS) {
                return Config.AUTO_SYNC_INTERVAL_AROUND_CONFERENCE
            } else {
                return Config.AUTO_SYNC_INTERVAL_AFTER_CONFERENCE
            }
        }

        fun updateSyncInterval(context: Context, account: Account) {
            LOGD(TAG, "Checking sync interval for " + account)
            val recommended = calculateRecommendedSyncInterval(context)
            val current = SettingsUtils.getCurSyncInterval(context)
            LOGD(TAG, "Recommended sync interval $recommended, current $current")
            if (recommended != current) {
                LOGD(TAG,
                        "Setting up sync for account " + account + ", interval " + recommended + "ms")
                ContentResolver.setIsSyncable(account, ScheduleContract.CONTENT_AUTHORITY, 1)
                ContentResolver.setSyncAutomatically(account, ScheduleContract.CONTENT_AUTHORITY, true)
                if (recommended <= 0L) { // Disable periodic sync.
                    ContentResolver.removePeriodicSync(account, ScheduleContract.CONTENT_AUTHORITY,
                            Bundle())
                } else {
                    ContentResolver.addPeriodicSync(account, ScheduleContract.CONTENT_AUTHORITY,
                            Bundle(), recommended / 1000L)
                }
                SettingsUtils.setCurSyncInterval(context, recommended)
            } else {
                LOGD(TAG, "No need to update sync interval.")
            }
        }
    }
}
