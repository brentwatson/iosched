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

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.gcm.GCMCommand
import com.google.samples.apps.iosched.myschedule.MyScheduleActivity

import com.google.samples.apps.iosched.util.LogUtils.LOGI
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

class AnnouncementCommand : GCMCommand() {

    override fun execute(context: Context, type: String, extraData: String?) {
        LOGI(TAG, "Received GCM message: " + type)
        displayNotification(context, extraData!!)
    }

    private fun displayNotification(context: Context, message: String) {
        LOGI(TAG, "Displaying notification: " + message)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(0, NotificationCompat.Builder(context).setWhen(System.currentTimeMillis()).setSmallIcon(R.drawable.ic_stat_notification).setTicker(message).setContentTitle(context.getString(R.string.app_name)).setContentText(message).setContentIntent(
                PendingIntent.getActivity(context, 0,
                        Intent(context, MyScheduleActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        0))//.setColor(context.getResources().getColor(R.color.theme_primary))
                // Note: setColor() is available in the support lib v21+.
                // We commented it out because we want the source to compile
                // against support lib v20. If you are using support lib
                // v21 or above on Android L, uncomment this line.
                .setAutoCancel(true).build())
    }

    companion object {
        private val TAG = makeLogTag("AnnouncementCommand")
    }

}
