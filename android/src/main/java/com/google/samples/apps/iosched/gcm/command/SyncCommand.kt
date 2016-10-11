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
package com.google.samples.apps.iosched.gcm.command

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.samples.apps.iosched.gcm.GCMCommand
import com.google.samples.apps.iosched.sync.TriggerSyncReceiver
import com.google.samples.apps.iosched.util.LogUtils.LOGI
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.util.*

class SyncCommand : GCMCommand() {

    override fun execute(context: Context, type: String, extraData: String?) {
        LOGI(TAG, "Received GCM message: " + type)
        val syncJitter: Int
        var syncData: SyncData? = null
        if (extraData != null) {
            try {
                val gson = Gson()
                syncData = gson.fromJson(extraData, SyncData::class.java)
            } catch (e: JsonSyntaxException) {
                LOGI(TAG, "Error while decoding extraData: " + e.toString())
            }

        }

        if (syncData != null && syncData.sync_jitter != 0) {
            syncJitter = syncData.sync_jitter
        } else {
            syncJitter = DEFAULT_TRIGGER_SYNC_MAX_JITTER_MILLIS
        }

        scheduleSync(context, syncJitter)
    }

    private fun scheduleSync(context: Context, syncJitter: Int) {
        val jitterMillis = (RANDOM.nextFloat() * syncJitter).toInt()
        val debugMessage = "Scheduling next sync for " + jitterMillis + "ms"
        LOGI(TAG, debugMessage)

        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(
                AlarmManager.RTC,
                System.currentTimeMillis() + jitterMillis,
                PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(context, TriggerSyncReceiver::class.java),
                        PendingIntent.FLAG_CANCEL_CURRENT))

    }

    inner class SyncData {
        val sync_jitter: Int = 0
    }

    companion object {
        private val TAG = makeLogTag("SyncCommand")
        private val DEFAULT_TRIGGER_SYNC_MAX_JITTER_MILLIS = 15 * 60 * 1000 // 15 minutes
        private val RANDOM = Random()
    }
}
