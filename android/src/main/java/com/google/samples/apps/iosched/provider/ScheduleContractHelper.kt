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

package com.google.samples.apps.iosched.provider

import android.net.Uri
import android.text.TextUtils

/**
 * Provides helper methods for specifying query parameters on `Uri`s.
 */
object ScheduleContractHelper {

    val QUERY_PARAMETER_DISTINCT = "distinct"

    private val QUERY_PARAMETER_OVERRIDE_ACCOUNT_NAME = "overrideAccountName"

    private val QUERY_PARAMETER_CALLER_IS_SYNC_ADAPTER = "callerIsSyncAdapter"


    fun isUriCalledFromSyncAdapter(uri: Uri): Boolean {
        return uri.getBooleanQueryParameter(QUERY_PARAMETER_CALLER_IS_SYNC_ADAPTER, false)
    }

    fun setUriAsCalledFromSyncAdapter(uri: Uri): Uri {
        return uri.buildUpon().appendQueryParameter(QUERY_PARAMETER_CALLER_IS_SYNC_ADAPTER, "true").build()
    }

    fun isQueryDistinct(uri: Uri): Boolean {
        return !TextUtils.isEmpty(uri.getQueryParameter(QUERY_PARAMETER_DISTINCT))
    }

    fun formatQueryDistinctParameter(parameter: String): String {
        return ScheduleContractHelper.QUERY_PARAMETER_DISTINCT + " " + parameter
    }

    fun getOverrideAccountName(uri: Uri): String {
        return uri.getQueryParameter(QUERY_PARAMETER_OVERRIDE_ACCOUNT_NAME)
    }

    /**
     * Adds an account override parameter to the `uri`. This is used by the
     * [ScheduleProvider] when fetching account-specific data.
     */
    fun addOverrideAccountName(uri: Uri, accountName: String): Uri {
        return uri.buildUpon().appendQueryParameter(
                QUERY_PARAMETER_OVERRIDE_ACCOUNT_NAME, accountName).build()
    }
}
