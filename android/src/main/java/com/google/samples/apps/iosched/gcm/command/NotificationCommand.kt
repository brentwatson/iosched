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
import android.net.Uri
import android.support.v4.app.NotificationCompat
import android.text.TextUtils
import com.google.gson.Gson
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.gcm.GCMCommand
import com.google.samples.apps.iosched.myschedule.MyScheduleActivity
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.util.LogUtils.*
import com.google.samples.apps.iosched.util.TimeUtils
import com.google.samples.apps.iosched.util.UIUtils

class NotificationCommand : GCMCommand() {

    private class NotificationCommandModel {
        internal var format: String? = null
        internal var audience: String? = null
        internal var minVersion: String? = null
        internal var maxVersion: String? = null
        internal var title: String? = null
        internal var message: String? = null
        internal var expiry: String? = null
        internal var dialogTitle: String? = null
        internal var dialogText: String? = null
        internal var dialogYes: String? = null
        internal var dialogNo: String? = null
        internal var url: String? = null
    }

    override fun execute(context: Context, type: String, payload: String?) {
        LOGI(TAG, "Received GCM message: " + type)
        LOGI(TAG, "Parsing GCM notification command: " + payload)
        val gson = Gson()
        val command: NotificationCommandModel?
        try {
            command = gson.fromJson(payload, NotificationCommandModel::class.java)
            if (command == null) {
                LOGE(TAG, "Failed to parse command (gson returned null).")
                return
            }
            LOGD(TAG, "Format: " + command.format!!)
            LOGD(TAG, "Audience: " + command.audience!!)
            LOGD(TAG, "Title: " + command.title!!)
            LOGD(TAG, "Message: " + command.message!!)
            LOGD(TAG, "Expiry: " + command.expiry!!)
            LOGD(TAG, "URL: " + command.url!!)
            LOGD(TAG, "Dialog title: " + command.dialogTitle!!)
            LOGD(TAG, "Dialog text: " + command.dialogText!!)
            LOGD(TAG, "Dialog yes: " + command.dialogYes!!)
            LOGD(TAG, "Dialog no: " + command.dialogNo!!)
            LOGD(TAG, "Min version code: " + command.minVersion!!)
            LOGD(TAG, "Max version code: " + command.maxVersion!!)
        } catch (ex: Exception) {
            ex.printStackTrace()
            LOGE(TAG, "Failed to parse GCM notification command.")
            return
        }

        LOGD(TAG, "Processing notification command.")
        processCommand(context, command)
    }

    private fun processCommand(context: Context, command: NotificationCommandModel) {
        // Check format
        if ("1.0.00" != command.format) {
            LOGW(TAG, "GCM notification command has unrecognized format: " + command.format!!)
            return
        }

        // Check app version
        if (!TextUtils.isEmpty(command.minVersion) || !TextUtils.isEmpty(command.maxVersion)) {
            LOGD(TAG, "Command has version range.")
            var minVersion = 0
            var maxVersion = Integer.MAX_VALUE
            try {
                if (!TextUtils.isEmpty(command.minVersion)) {
                    minVersion = Integer.parseInt(command.minVersion)
                }
                if (!TextUtils.isEmpty(command.maxVersion)) {
                    maxVersion = Integer.parseInt(command.maxVersion)
                }
                LOGD(TAG, "Version range: $minVersion - $maxVersion")
                val pinfo = context.packageManager.getPackageInfo(context.packageName, 0)
                LOGD(TAG, "My version code: " + pinfo.versionCode)
                if (pinfo.versionCode < minVersion) {
                    LOGD(TAG, "Skipping command because our version is too old, "
                            + pinfo.versionCode + " < " + minVersion)
                    return
                }
                if (pinfo.versionCode > maxVersion) {
                    LOGD(TAG, "Skipping command because our version is too new, "
                            + pinfo.versionCode + " > " + maxVersion)
                    return
                }
            } catch (ex: NumberFormatException) {
                LOGE(TAG, "Version spec badly formatted: min=" + command.minVersion
                        + ", max=" + command.maxVersion)
                return
            } catch (ex: Exception) {
                LOGE(TAG, "Unexpected problem doing version check.", ex)
                return
            }

        }

        // Check if we are the right audience
        LOGD(TAG, "Checking audience: " + command.audience!!)
        if ("remote" == command.audience) {
            if (SettingsUtils.isAttendeeAtVenue(context)) {
                LOGD(TAG, "Ignoring notification because audience is remote and attendee is on-site")
                return
            } else {
                LOGD(TAG, "Relevant (attendee is remote).")
            }
        } else if ("local" == command.audience) {
            if (!SettingsUtils.isAttendeeAtVenue(context)) {
                LOGD(TAG, "Ignoring notification because audience is on-site and attendee is remote.")
                return
            } else {
                LOGD(TAG, "Relevant (attendee is local).")
            }
        } else if ("all" == command.audience) {
            LOGD(TAG, "Relevant (audience is 'all').")
        } else {
            LOGE(TAG, "Invalid audience on GCM notification command: " + command.audience!!)
            return
        }

        // Check if it expired
        val expiry = if (command.expiry == null) null else TimeUtils.parseTimestamp(command.expiry!!)
        if (expiry == null) {
            LOGW(TAG, "Failed to parse expiry field of GCM notification command: " + command.expiry!!)
            return
        } else if (expiry.time < UIUtils.getCurrentTime(context)) {
            LOGW(TAG, "Got expired GCM notification command. Expiry: " + expiry.toString())
            return
        } else {
            LOGD(TAG, "Message is still valid (expiry is in the future: " + expiry.toString() + ")")
        }

        // decide the intent that will be fired when the user clicks the notification
        val intent: Intent
        if (TextUtils.isEmpty(command.dialogText)) {
            // notification leads directly to the URL, no dialog
            if (TextUtils.isEmpty(command.url)) {
                intent = Intent(context, MyScheduleActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } else {
                intent = Intent(Intent.ACTION_VIEW, Uri.parse(command.url))
            }
        } else {
            // use a dialog
            intent = Intent(context, MyScheduleActivity::class.java).setFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra(MyScheduleActivity.EXTRA_DIALOG_TITLE,
                    if (command.dialogTitle == null) "" else command.dialogTitle)
            intent.putExtra(MyScheduleActivity.EXTRA_DIALOG_MESSAGE,
                    if (command.dialogText == null) "" else command.dialogText)
            intent.putExtra(MyScheduleActivity.EXTRA_DIALOG_YES,
                    if (command.dialogYes == null) "OK" else command.dialogYes)
            intent.putExtra(MyScheduleActivity.EXTRA_DIALOG_NO,
                    if (command.dialogNo == null) "" else command.dialogNo)
            intent.putExtra(MyScheduleActivity.EXTRA_DIALOG_URL,
                    if (command.url == null) "" else command.url)
        }

        val title = if (TextUtils.isEmpty(command.title))
            context.getString(R.string.app_name)
        else
            command.title
        val message = if (TextUtils.isEmpty(command.message)) "" else command.message

        // fire the notification
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(0, NotificationCompat.Builder(context).setWhen(System.currentTimeMillis()).setSmallIcon(R.drawable.ic_stat_notification).setTicker(command.message).setContentTitle(title).setContentText(message).setContentIntent(PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT))//.setColor(context.getResources().getColor(R.color.theme_primary))
                // Note: setColor() is available in the support lib v21+.
                // We commented it out because we want the source to compile
                // against support lib v20. If you are using support lib
                // v21 or above on Android L, uncomment this line.
                .setAutoCancel(true).build())
    }

    companion object {
        private val TAG = makeLogTag("NotificationCommand")
    }

}
