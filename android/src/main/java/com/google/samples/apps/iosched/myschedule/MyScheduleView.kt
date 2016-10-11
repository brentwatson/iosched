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

package com.google.samples.apps.iosched.myschedule

import android.content.Context
import android.database.DataSetObserver
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

/**
 * My Schedule view. This view is a linear layout showing all schedule items from an adapter.
 * This is different from the MyScheduleFragment, which is a ListFragment based on the adapter.
 * The fundamental difference is that while the ListFragment has built-in scrolling, this
 * view does NOT scroll, it resizes to fit all items. It is suitable for use as part of a
 * larger view where you want the larger view to scroll as one, with this list inside it.
 */
class MyScheduleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : LinearLayout(context, attrs, defStyle) {

    internal var mAdapter: MyScheduleAdapter? = null
    internal var mObserver: DataSetObserver? = null

    init {
        orientation = LinearLayout.VERTICAL
    }

    fun setAdapter(adapter: MyScheduleAdapter?) {
        if (mAdapter != null && mObserver != null) {
            mAdapter!!.unregisterDataSetObserver(mObserver)
            mObserver = null
        }
        mAdapter = adapter
        rebuild()
        if (mAdapter != null) {
            mAdapter!!.registerDataSetObserver(object : DataSetObserver() {
                override fun onChanged() {
                    rebuild()
                }

                override fun onInvalidated() {
                    setAdapter(null)
                }
            })
        }
    }

    private fun setViewAt(i: Int, view: View) {
        if (i < childCount) {
            val viewToReplace = getChildAt(i)
            if (viewToReplace !== view) {
                addView(view, i)
                removeView(viewToReplace)
            }
        } else {
            addView(view)
        }
    }

    fun rebuild() {
        LOGD(TAG, "Rebuilding MyScheduleView.")
        var i: Int
        val count = if (mAdapter == null) 0 else mAdapter!!.count
        LOGD(TAG, "Adapter has $count items.")

        i = 0
        while (i < count) {
            LOGD(TAG, "Setting up view#" + i)
            val recycle = if (i < childCount) getChildAt(i) else null
            LOGD(TAG, "view#$i, recycle=$recycle")
            val view = mAdapter!!.getView(i, recycle, this)
            if (i < childCount) {
                LOGD(TAG, "setting view#" + i)
                setViewAt(i, view)
            } else {
                LOGD(TAG, "adding view #" + i)
                addView(view)
            }
            i++
        }
        while (i < childCount) {
            LOGD(TAG, "removing view #" + i)
            removeViewAt(i)
            i++
        }

        requestLayout()
    }

    companion object {
        private val TAG = makeLogTag("MyScheduleView")
    }
}
