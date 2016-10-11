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
package com.google.samples.apps.iosched.debug.actions

import android.content.Context
import android.os.AsyncTask
import com.google.common.base.Charsets
import com.google.samples.apps.iosched.debug.DebugAction
import com.google.samples.apps.iosched.sync.userdata.util.UserDataHelper

/**
 * Simple DebugAction that displays the local user data of a current user.
 */
class DisplayUserDataDebugAction : DebugAction {

    override fun run(context: Context, callback: DebugAction.Callback) {
        object : AsyncTask<Context, Void, UserDataHelper.UserData>() {
            override fun doInBackground(vararg contexts: Context): UserDataHelper.UserData {
                return UserDataHelper.getLocalUserData(contexts[0])
            }

            override fun onPostExecute(userData: UserDataHelper.UserData) {
                callback.done(true, "Found User Data: " + String(
                        UserDataHelper.toByteArray(userData), Charsets.UTF_8))
            }
        }.execute(context)
    }

    override val label: String
        get() = "show local user data"

}
