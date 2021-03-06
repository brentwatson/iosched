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

data class Speaker (
    var id: String?,
    var publicPlusId: String?,
    var bio: String?,
    var name: String?,
    var company: String?,
    var plusoneUrl: String?,
    var twitterUrl: String?,
    var thumbnailUrl: String?
) {
    val importHashcode: String
        get() {
            val sb = StringBuilder()
            sb.append("id").append(if (id == null) "" else id).append("publicPlusId").append(if (publicPlusId == null) "" else publicPlusId).append("bio").append(if (bio == null) "" else bio).append("name").append(if (name == null) "" else name).append("company").append(if (company == null) "" else company).append("plusoneUrl").append(if (plusoneUrl == null) "" else plusoneUrl).append("twitterUrl").append(if (twitterUrl == null) "" else twitterUrl).append("thumbnailUrl").append(if (thumbnailUrl == null) "" else thumbnailUrl)
            return HashUtils.computeWeakHash(sb.toString())
        }
}
