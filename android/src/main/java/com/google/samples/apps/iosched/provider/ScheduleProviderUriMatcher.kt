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

import android.content.UriMatcher
import android.net.Uri
import android.util.SparseArray

/**
 * Provides methods to match a [android.net.Uri] to a [ScheduleUriEnum].
 *
 *
 * All methods are thread safe, except [.buildUriMatcher] and [.buildEnumsMap],
 * which is why they are called only from the constructor.
 */
class ScheduleProviderUriMatcher {

    /**
     * All methods on a [UriMatcher] are thread safe, except `addURI`.
     */
    private val mUriMatcher: UriMatcher

    private val mEnumsMap = SparseArray<ScheduleUriEnum>()

    init {
        mUriMatcher = UriMatcher(UriMatcher.NO_MATCH)
        buildUriMatcher()
    }

    private fun buildUriMatcher() {
        val authority = ScheduleContract.CONTENT_AUTHORITY

        val uris = ScheduleUriEnum.values()
        for (i in uris.indices) {
            mUriMatcher.addURI(authority, uris[i].path, uris[i].code)
        }

        buildEnumsMap()
    }

    private fun buildEnumsMap() {
        val uris = ScheduleUriEnum.values()
        for (i in uris.indices) {
            mEnumsMap.put(uris[i].code, uris[i])
        }
    }

    /**
     * Matches a `uri` to a [ScheduleUriEnum].

     * @return the [ScheduleUriEnum], or throws new UnsupportedOperationException if no match.
     */
    fun matchUri(uri: Uri): ScheduleUriEnum {
        val code = mUriMatcher.match(uri)
        try {
            return matchCode(code)
        } catch (e: UnsupportedOperationException) {
            throw UnsupportedOperationException("Unknown uri " + uri)
        }

    }

    /**
     * Matches a `code` to a [ScheduleUriEnum].

     * @return the [ScheduleUriEnum], or throws new UnsupportedOperationException if no match.
     */
    fun matchCode(code: Int): ScheduleUriEnum {
        val scheduleUriEnum = mEnumsMap.get(code)
        if (scheduleUriEnum != null) {
            return scheduleUriEnum
        } else {
            throw UnsupportedOperationException("Unknown uri with code " + code)
        }
    }
}
/**
 * This constructor needs to be called from a thread-safe method as it isn't thread-safe itself.
 */
