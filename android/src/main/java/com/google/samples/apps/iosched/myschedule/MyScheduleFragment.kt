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

import android.app.Activity
import android.os.Bundle
import android.app.ListFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.google.samples.apps.iosched.R

/**
 * A list fragment that shows items from MySchedule.
 * To use, call setListAdapter(), passing it an instance of your MyScheduleAdapter.
 */
class MyScheduleFragment : ListFragment() {
    private var mContentDescription: String? = null
    private var mRoot: View? = null

    interface Listener {
        fun onFragmentViewCreated(fragment: ListFragment)
        fun onFragmentAttached(fragment: MyScheduleFragment)
        fun onFragmentDetached(fragment: MyScheduleFragment)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle): View? {
        mRoot = inflater.inflate(R.layout.fragment_my_schedule, container, false)
        if (mContentDescription != null) {
            mRoot!!.contentDescription = mContentDescription
        }
        return mRoot
    }

    fun setContentDescription(desc: String) {
        mContentDescription = desc
        if (mRoot != null) {
            mRoot!!.contentDescription = mContentDescription
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (activity is Listener) {
            (activity as Listener).onFragmentViewCreated(this)
        }
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        if (getActivity() is Listener) {
            (getActivity() as Listener).onFragmentAttached(this)
        }
    }

    override fun onDetach() {
        super.onDetach()
        if (activity is Listener) {
            (activity as Listener).onFragmentDetached(this)
        }
    }
}
