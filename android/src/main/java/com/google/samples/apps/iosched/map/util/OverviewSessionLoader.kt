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

import android.content.Context
import com.google.samples.apps.iosched.provider.ScheduleContract

/**
 * Loads session information for all sessions scheduled in a particular room after a timestamp.
 */
class OverviewSessionLoader(context: Context, roomId: String, roomTitle: String,
                            roomType: Int, time: Long) : SessionLoader(context, roomId, roomTitle, roomType, ScheduleContract.Sessions.buildSessionsInRoomAfterUri(roomId, time), OverviewSessionLoader.Query.PROJECTION, null, null, OverviewSessionLoader.Query.ORDER) {


    /**
     * Query Paramters for the "Sessions in room after" query that returns a list of sessions
     * that are following a given time in a particular room.
     */
    interface Query {
        companion object {

            val ORDER = ScheduleContract.Sessions.SESSION_START + " ASC"

            val PROJECTION = arrayOf(ScheduleContract.Sessions._ID, ScheduleContract.Sessions.SESSION_ID, ScheduleContract.Sessions.SESSION_TITLE, ScheduleContract.Sessions.SESSION_START, ScheduleContract.Sessions.SESSION_END, ScheduleContract.Sessions.SESSION_TAGS)

            val SESSION_ID = 1
            val SESSION_TITLE = 2
            val SESSION_START = 3
            val SESSION_END = 4
            val SESSION_TAGS = 5
        }
    }
}
