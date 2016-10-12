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

import android.app.SearchManager
import android.content.ContentProviderOperation
import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.util.*

class SearchSuggestHandler(context: Context) : JSONHandler(context) {
    internal var mSuggestions = HashSet<String>()

    override fun process(element: JsonElement) {
        for (word in Gson().fromJson(element, Array<String>::class.java)) {
            mSuggestions.add(word)
        }
    }

    override fun makeContentProviderOperations(list: ArrayList<ContentProviderOperation>) {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.SearchSuggest.CONTENT_URI)

        list.add(ContentProviderOperation.newDelete(uri).build())
        for (word in mSuggestions) {
            list.add(ContentProviderOperation.newInsert(uri).withValue(SearchManager.SUGGEST_COLUMN_TEXT_1, word).build())
        }
    }

    companion object {
        private val TAG = makeLogTag(SpeakersHandler::class.java)
    }
}
