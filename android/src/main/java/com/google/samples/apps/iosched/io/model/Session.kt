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

package com.google.samples.apps.iosched.io.model

import java.util.*

data class RelatedContent (
    var id: String,
    var name: String
)

data class Session (
    var id: String,
    var url: String,
    var description: String,
    var title: String,
    var tags: Array<String>?,
    var startTimestamp: String,
    var youtubeUrl: String,
    var speakers: Array<String>,
    var endTimestamp: String,
    var hashtag: String,
    var subtype: String,
    var room: String,
    var captionsUrl: String,
    var photoUrl: String,
    var isLivestream: Boolean = false,
    var mainTag: String,
    var color: String,
    var relatedContent: Array<RelatedContent>,
    var groupingOrder: Int = 0,
    val importHashCode: String = "" + Random().nextLong() + ""
) {

    fun makeTagsList(): String {
        var i: Int
        if (tags!!.size == 0) return ""
        val sb = StringBuilder()
        sb.append(tags!![0])
        i = 1
        while (i < tags!!.size) {
            sb.append(",").append(tags!![i])
            i++
        }
        return sb.toString()
    }

    fun hasTag(tag: String): Boolean {
        for (myTag in tags!!) {
            if (myTag == tag) {
                return true
            }
        }
        return false
    }
}


