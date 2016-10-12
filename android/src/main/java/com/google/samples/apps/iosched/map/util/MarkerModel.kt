/*
 * Copyright 2015 Google Inc. All rights reserved.
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

package com.google.samples.apps.iosched.map.util


import com.google.android.gms.maps.model.Marker

/**
 * A structure to store information about a Marker.
 */
class MarkerModel(var id: String, var floor: Int, var type: Int, var label: String, var marker: Marker?) {
    companion object {

        // Marker types
        val TYPE_INACTIVE = 0
        val TYPE_SESSION = 1
        val TYPE_PLAIN = 2
        val TYPE_LABEL = 3
        val TYPE_CODELAB = 4
        val TYPE_SANDBOX = 5
        val TYPE_OFFICEHOURS = 6
        val TYPE_MISC = 7
        val TYPE_MOSCONE = 8
    }
}