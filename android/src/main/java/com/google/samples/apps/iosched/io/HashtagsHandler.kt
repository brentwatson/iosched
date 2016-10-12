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
import android.graphics.Color
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.samples.apps.iosched.io.model.Hashtag
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.util.*

class HashtagsHandler(context: Context) : JSONHandler(context) {
    private val mHashtags = HashMap<String, Hashtag>()

    override fun process(element: JsonElement) {
        LOGD(TAG, "process")
        for (hashtag in Gson().fromJson(element, Array<Hashtag>::class.java)) {
            mHashtags.put(hashtag.name, hashtag)
        }
    }

    override fun makeContentProviderOperations(list: ArrayList<ContentProviderOperation>) {
        LOGD(TAG, "makeContentProviderOperations")
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Hashtags.CONTENT_URI)
        // Remove all the current entries
        list.add(ContentProviderOperation.newDelete(uri).build())
        // Insert hashtags
        for (hashtag in mHashtags.values) {
            val builder = ContentProviderOperation.newInsert(uri)
            builder.withValue(ScheduleContract.Hashtags.HASHTAG_NAME, hashtag.name)
            builder.withValue(ScheduleContract.Hashtags.HASHTAG_DESCRIPTION, hashtag.description)
            try {
                builder.withValue(ScheduleContract.Hashtags.HASHTAG_COLOR,
                        Color.parseColor(hashtag.color))
            } catch (e: IllegalArgumentException) {
                builder.withValue(ScheduleContract.Hashtags.HASHTAG_COLOR, Color.BLACK)
            }

            builder.withValue(ScheduleContract.Hashtags.HASHTAG_ORDER, hashtag.order)
            list.add(builder.build())
        }
        LOGD(TAG, "Hashtags: " + mHashtags.size)
    }

    companion object {

        private val TAG = makeLogTag(HashtagsHandler::class.java)
    }

}
