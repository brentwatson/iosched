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

package com.google.samples.apps.iosched

import android.app.Application
import android.content.Intent
import com.google.android.gms.security.ProviderInstaller
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.util.LogUtils.*

/**
 * [android.app.Application] used to initialize Analytics. Code initialized in
 * Application classes is rare since this code will be run any time a ContentProvider, Activity,
 * or Service is used by the user or system. Analytics, dependency injection, and multi-dex
 * frameworks are in this very small set of use cases.
 */
class AppApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AnalyticsHelper.prepareAnalytics(applicationContext)
        SettingsUtils.markDeclinedWifiSetup(applicationContext, false)

        // Ensure an updated security provider is installed into the system when a new one is
        // available via Google Play services.
        try {
            ProviderInstaller.installIfNeededAsync(applicationContext,
                    object : ProviderInstaller.ProviderInstallListener {
                        override fun onProviderInstalled() {
                            LOGW(TAG, "New security provider installed.")
                        }

                        override fun onProviderInstallFailed(errorCode: Int, intent: Intent) {
                            LOGE(TAG, "New security provider install failed.")
                            // No notification shown there is no user intervention needed.
                        }
                    })
        } catch (ignorable: Exception) {
            LOGE(TAG, "Unknown issue trying to install a new security provider.", ignorable)
        }

    }

    companion object {

        private val TAG = makeLogTag(AppApplication::class.java)
    }
}
