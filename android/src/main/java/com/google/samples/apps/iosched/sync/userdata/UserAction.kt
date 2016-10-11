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
package com.google.samples.apps.iosched.sync.userdata

class UserAction {
    enum class TYPE {
        ADD_STAR, REMOVE_STAR, VIEW_VIDEO, SUBMIT_FEEDBACK
    }

    constructor() {
    }

    constructor(type: TYPE, sessionId: String) {
        this.type = type
        this.sessionId = sessionId
    }

    var type: TYPE? = null
    var sessionId: String? = null
    var videoId: String? = null
    var accountName: String? = null
    var requiresSync: Boolean = false

}
