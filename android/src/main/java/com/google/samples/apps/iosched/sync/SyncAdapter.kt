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
import android.os.Bundle
import com.google.samples.apps.iosched.BuildConfig
import com.google.samples.apps.iosched.util.LogUtils.*
import java.util.regex.Pattern

/**
 * Sync adapter for Google I/O data. Used for download sync only. For upload sync,
 */
class SyncAdapter(private val mContext: Context, autoInitialize: Boolean) : AbstractThreadedSyncAdapter(mContext, autoInitialize) {

    init {

        //noinspection ConstantConditions,PointlessBooleanExpression
        if (!BuildConfig.DEBUG) {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                LOGE(TAG, "Uncaught sync exception, suppressing UI in release build.",
                        throwable)
            }
        }
    }

    override fun onPerformSync(account: Account, extras: Bundle, authority: String,
                               provider: ContentProviderClient, syncResult: SyncResult) {
        val uploadOnly = extras.getBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, false)
        val initialize = extras.getBoolean(ContentResolver.SYNC_EXTRAS_INITIALIZE, false)
        val userScheduleDataOnly = extras.getBoolean(EXTRA_SYNC_USER_DATA_ONLY,
                false)

        val logSanitizedAccountName = sSanitizeAccountNamePattern.matcher(account.name).replaceAll("$1...$2@")

        // This Adapter is declared not to support uploading in its xml file.
        // {@code ContentResolver.SYNC_EXTRAS_UPLOAD} is set by the system in some cases, but never
        // by the app. Conference data only is a download sync, user schedule data sync is both
        // ways and uses {@code EXTRA_SYNC_USER_DATA_ONLY}, session feedback data sync is
        // upload only and isn't managed by this SyncAdapter as it doesn't need periodic sync.
        if (uploadOnly) {
            return
        }

        LOGI(TAG, "Beginning sync for account " + logSanitizedAccountName + "," +
                " uploadOnly=" + uploadOnly +
                " userScheduleDataOnly =" + userScheduleDataOnly +
                " initialize=" + initialize)

        // Sync from bootstrap and remote data, as needed
        SyncHelper(mContext).performSync(syncResult, account, extras)
    }

    companion object {
        private val TAG = makeLogTag(SyncAdapter::class.java)

        private val sSanitizeAccountNamePattern = Pattern.compile("(.).*?(.?)@")
        val EXTRA_SYNC_USER_DATA_ONLY = "com.google.samples.apps.iosched.EXTRA_SYNC_USER_DATA_ONLY"
    }

}
