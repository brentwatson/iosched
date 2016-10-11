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

package com.google.samples.apps.iosched.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.TaskStackBuilder

/**
 * Helper 'proxy' activity that simply accepts an activity intent and synthesize a back-stack
 * for it, per Android's design guidelines for navigation from widgets and notifications.
 */
class TaskStackBuilderProxyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val builder = TaskStackBuilder.create(this)
        val proxyIntent = intent
        if (!proxyIntent.hasExtra(EXTRA_INTENTS)) {
            finish()
            return
        }

        for (parcelable in proxyIntent.getParcelableArrayExtra(EXTRA_INTENTS)) {
            builder.addNextIntent(parcelable as Intent)
        }

        builder.startActivities()
        finish()
    }

    companion object {
        private val EXTRA_INTENTS = "com.google.samples.apps.iosched.extra.INTENTS"

        fun getTemplate(context: Context): Intent {
            return Intent(context, TaskStackBuilderProxyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        fun getFillIntent(vararg intents: Intent): Intent {
            return Intent().putExtra(EXTRA_INTENTS, intents)
        }
    }
}
