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

import java.io.IOException

/**
 * General [IOException] that indicates a problem occurred while parsing or applying a [ ].
 */
class HandlerException : IOException {

    constructor() : super() {
    }

    constructor(message: String) : super(message) {
    }

    constructor(message: String, cause: Throwable) : super(message) {
        (this as java.lang.Throwable).initCause(cause)
    }

    override fun toString(): String {
        if (cause != null) {
            return (this as java.lang.Throwable).localizedMessage + ": " + cause
        } else {
            return (this as java.lang.Throwable).localizedMessage
        }
    }
}
