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
package com.google.samples.apps.iosched.gcm

import android.content.Context
import android.content.Intent
import com.google.android.gcm.GCMBaseIntentService
import com.google.samples.apps.iosched.BuildConfig
import com.google.samples.apps.iosched.gcm.command.*
import com.google.samples.apps.iosched.util.LogUtils.*
import java.util.*

/**
 * [android.app.IntentService] responsible for handling GCM messages.
 */
class GCMIntentService : GCMBaseIntentService(BuildConfig.GCM_SENDER_ID) {

    override fun onRegistered(context: Context, regId: String) {
        LOGI(TAG, "Device registered: regId=" + regId)
    }

    override fun onUnregistered(context: Context, regId: String) {
        LOGI(TAG, "Device unregistered")
        ServerUtilities.unregister(context, regId)
    }

    override fun onMessage(context: Context, intent: Intent) {
        var action: String? = intent.getStringExtra("action")
        val extraData = intent.getStringExtra("extraData")
        LOGD(TAG, "Got GCM message, action=$action, extraData=$extraData")

        if (action == null) {
            LOGE(TAG, "Message received without command action")
            return
        }

        action = action.toLowerCase()
        val command = MESSAGE_RECEIVERS[action]
        if (command == null) {
            LOGE(TAG, "Unknown command received: " + action)
        } else {
            command.execute(this, action, extraData)
        }

    }

    public override fun onError(context: Context, errorId: String) {
        LOGE(TAG, "Received error: " + errorId)
    }

    override fun onRecoverableError(context: Context?, errorId: String?): Boolean {
        // log message
        LOGW(TAG, "Received recoverable error: " + errorId!!)
        return super.onRecoverableError(context, errorId)
    }

    companion object {

        private val TAG = makeLogTag("GCM")

        private val MESSAGE_RECEIVERS: Map<String, GCMCommand>

        init {
            // Known messages and their GCM message receivers
            val receivers = HashMap<String, GCMCommand>()
            receivers.put("test", TestCommand())
            receivers.put("announcement", AnnouncementCommand())
            receivers.put("sync_schedule", SyncCommand())
            receivers.put("sync_user", SyncUserCommand())
            receivers.put("notification", NotificationCommand())
            MESSAGE_RECEIVERS = Collections.unmodifiableMap(receivers)
        }
    }
}
