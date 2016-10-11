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

package com.google.samples.apps.iosched.util

import android.content.Context
import android.content.pm.PackageManager

import com.google.samples.apps.iosched.util.LogUtils.*

object NetUtils {
    private val TAG = makeLogTag(NetUtils::class.java)
    private var mUserAgent: String? = null

    fun getUserAgent(appName: String, context: Context): String {
        if (mUserAgent == null) {
            mUserAgent = appName
            try {
                val packageName = context.packageName
                val version = context.packageManager.getPackageInfo(packageName, 0).versionName
                mUserAgent = "$mUserAgent ($packageName/$version)"
                LOGD(TAG, "User agent set to: " + mUserAgent!!)
            } catch (e: PackageManager.NameNotFoundException) {
                LOGE(TAG, "Unable to find self by package name", e)
            }

        }
        return mUserAgent!!
    }
}
