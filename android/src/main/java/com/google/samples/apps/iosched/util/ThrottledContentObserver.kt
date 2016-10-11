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

package com.google.samples.apps.iosched.util

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler

/**
 * A ContentObserver that bundles multiple consecutive changes in a short time period into one.
 * This can be used in place of a regular ContentObserver to protect against getting
 * too many consecutive change events as a result of data changes. This observer will wait
 * a while before firing, so if multiple requests come in in quick succession, they will
 * cause it to fire only once.
 */
class ThrottledContentObserver(callback: ThrottledContentObserver.Callbacks) : ContentObserver(null) {
    internal var mMyHandler: Handler
    internal var mScheduledRun: Runnable? = null
    internal var mCallback: Callbacks? = null

    interface Callbacks {
        fun onThrottledContentObserverFired()
    }

    init {
        mMyHandler = Handler()
        mCallback = callback
    }

    override fun onChange(selfChange: Boolean) {
        if (mScheduledRun != null) {
            mMyHandler.removeCallbacks(mScheduledRun)
        } else {
            mScheduledRun = Runnable {
                if (mCallback != null) {
                    mCallback!!.onThrottledContentObserverFired()
                }
            }
        }
        mMyHandler.postDelayed(mScheduledRun, THROTTLE_DELAY.toLong())
    }

    fun cancelPendingCallback() {
        if (mScheduledRun != null) {
            mMyHandler.removeCallbacks(mScheduledRun)
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri) {
        onChange(selfChange)
    }

    companion object {
        private val THROTTLE_DELAY = 1000
    }
}
