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

import android.content.ContentProvider
import android.net.Uri
import android.text.format.Time
import java.util.*
import java.util.regex.Pattern

/**
 * Various utility methods used by [com.google.samples.apps.iosched.io.JSONHandler].
 */
object ParserUtils {
    /** Used to sanitize a string to be [Uri] safe.  */
    private val sSanitizePattern = Pattern.compile("[^a-z0-9-_]")

    /**
     * Sanitize the given string to be [Uri] safe for building
     * [ContentProvider] paths.
     */
    fun sanitizeId(input: String?): String? {
        if (input == null) {
            return null
        }
        return sSanitizePattern.matcher(input.replace("+", "plus").toLowerCase()).replaceAll("")
    }

    /**
     * Parse the given string as a RFC 3339 timestamp, returning the value as
     * milliseconds since the epoch.
     */
    fun parseTime(timestamp: String): Long {
        val time = Time()
        time.parse3339(timestamp)
        return time.toMillis(false)
    }

    fun joinStrings(connector: String, strings: ArrayList<String>, recycle: StringBuilder?): String {
        var recycle = recycle
        if (strings.size <= 0) {
            return ""
        }
        if (recycle == null) {
            recycle = StringBuilder()
        } else {
            recycle.setLength(0)
        }
        recycle.append(strings[0])
        for (i in 1..strings.size - 1) {
            recycle.append(connector)
            recycle.append(strings[i])
        }
        return recycle.toString()
    }
}
