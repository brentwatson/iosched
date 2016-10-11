/*
 * Copyright 2015 Google Inc. All rights reserved.
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

package com.google.samples.apps.iosched.appwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.myschedule.MyScheduleActivity
import com.google.samples.apps.iosched.sync.SyncHelper
import com.google.samples.apps.iosched.ui.TaskStackBuilderProxyActivity
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

/**
 * The app widget's AppWidgetProvider.
 */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, widgetIntent: Intent) {
        val action = widgetIntent.action

        if (REFRESH_ACTION == action) {
            LOGD(TAG, "received REFRESH_ACTION from widget")
            val shouldSync = widgetIntent.getBooleanExtra(EXTRA_PERFORM_SYNC, false)

            // Trigger sync
            val chosenAccount = AccountUtils.getActiveAccount(context)
            if (shouldSync && chosenAccount != null) {
                SyncHelper.requestManualSync(chosenAccount)
            }

            // Notify the widget that the list view needs to be updated.
            val mgr = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, ScheduleWidgetProvider::class.java)
            mgr.notifyAppWidgetViewDataChanged(mgr.getAppWidgetIds(cn),
                    R.id.widget_schedule_list)

        }
        super.onReceive(context, widgetIntent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        LOGD(TAG, "updating app widget")
        val isAuthenticated = AccountUtils.hasActiveAccount(context)
        for (appWidgetId in appWidgetIds) {
            // Specify the service to provide data for the collection widget.  Note that we need to
            // embed the appWidgetId via the data otherwise it will be ignored.
            val intent = Intent(context, ScheduleWidgetRemoteViewsService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            intent.data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
            val rv = RemoteViews(context.packageName, R.layout.widget)
            rv.setRemoteAdapter(R.id.widget_schedule_list, intent)

            // Set the empty view to be displayed if the collection is empty.  It must be a sibling
            // view of the collection view.
            rv.setEmptyView(R.id.widget_schedule_list, android.R.id.empty)
            LOGD(TAG, "setting widget empty view")
            rv.setTextViewText(android.R.id.empty, context.resources.getString(if (isAuthenticated)
                R.string.empty_widget_text
            else
                R.string.empty_widget_text_signed_out))

            val refreshPendingIntent = PendingIntent.getBroadcast(context, 0,
                    getRefreshBroadcastIntent(context, true), PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

            val onClickIntent = TaskStackBuilderProxyActivity.getTemplate(context)
            val onClickPendingIntent = PendingIntent.getActivity(context, 0,
                    onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setPendingIntentTemplate(R.id.widget_schedule_list, onClickPendingIntent)

            val openAppIntent = Intent(context, MyScheduleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val openAppPendingIntent = PendingIntent.getActivity(context, 0,
                    openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.widget_logo, openAppPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, rv)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private val TAG = makeLogTag(ScheduleWidgetProvider::class.java)

        private val REFRESH_ACTION = "com.google.samples.apps.iosched.appwidget.action.REFRESH"
        private val EXTRA_PERFORM_SYNC = "com.google.samples.apps.iosched.appwidget.extra.PERFORM_SYNC"

        fun getRefreshBroadcastIntent(context: Context, performSync: Boolean): Intent {
            return Intent(REFRESH_ACTION).setComponent(ComponentName(context, ScheduleWidgetProvider::class.java)).putExtra(EXTRA_PERFORM_SYNC, performSync)
        }
    }
}
