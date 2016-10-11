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
package com.google.samples.apps.iosched.debug.actions

import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.AsyncTask
import android.os.Bundle
import com.google.samples.apps.iosched.debug.DebugAction
import com.google.samples.apps.iosched.sync.ConferenceDataHandler
import com.google.samples.apps.iosched.sync.SyncHelper
import com.google.samples.apps.iosched.util.AccountUtils

/**
 * A DebugAction that runs an immediate full sync.
 */
class ForceSyncNowAction : DebugAction {
    override fun run(context: Context, callback: DebugAction.Callback) {
        ConferenceDataHandler.resetDataTimestamp(context)
        val bundle = Bundle()
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        object : AsyncTask<Context, Void, Void>() {
            override fun doInBackground(vararg contexts: Context): Void? {
                val account = AccountUtils.getActiveAccount(context)
                if (account == null) {
                    callback.done(false, "Cannot sync if there is no active account.")
                } else {
                    SyncHelper(contexts[0]).performSync(SyncResult(),
                            AccountUtils.getActiveAccount(context), bundle)
                }
                return null
            }
        }.execute(context)
    }

    override val label: String
        get() = "Force data sync now"

}
