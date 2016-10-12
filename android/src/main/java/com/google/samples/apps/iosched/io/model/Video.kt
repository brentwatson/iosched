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

import com.google.samples.apps.iosched.util.HashUtils

data class Video (
    var id: String?,
    var year: Int = 0,
    var title: String?,
    var desc: String?,
    var vid: String?,
    var topic: String?,
    var speakers: String?,
    var thumbnailUrl: String?
) {
    val importHashcode: String
        get() {
            val sb = StringBuilder()
            sb.append("id").append(if (id == null) "" else id).append("year").append(year).append("title").append(if (title == null) "" else title).append("desc").append(if (desc == null) "" else desc).append("vid").append(if (vid == null) "" else vid).append("topic").append(if (topic == null) "" else topic).append("speakers").append(if (speakers == null) "" else speakers).append("thumbnailUrl").append(if (thumbnailUrl == null) "" else thumbnailUrl)
            return HashUtils.computeWeakHash(sb.toString())
        }
}

