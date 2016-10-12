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

package com.google.samples.apps.iosched.io

import android.content.ContentProviderOperation
import android.content.Context
import com.google.common.base.Charsets
import com.google.gson.JsonElement
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringWriter
import java.util.*

abstract class JSONHandler(context: Context) {

    init {
        mContext = context
    }

    abstract fun makeContentProviderOperations(list: ArrayList<ContentProviderOperation>)

    abstract fun process(element: JsonElement)

    companion object {

        @JvmStatic
        protected var mContext: Context? = null

        @Throws(IOException::class)
        fun parseResource(context: Context, resource: Int): String {
            val `is` = context.resources.openRawResource(resource)
            val writer = StringWriter()
            val buffer = CharArray(1024)
            try {
                val reader = BufferedReader(InputStreamReader(`is`, Charsets.UTF_8))
                var n: Int = Int.MIN_VALUE
                while (n != -1) {
                    n = reader.read(buffer)
                    writer.write(buffer, 0, n)
                }
            } finally {
                `is`.close()
            }

            return writer.toString()
        }
    }
}
