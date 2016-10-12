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

import android.content.ContentProviderOperation
import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.samples.apps.iosched.io.model.Room
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.util.*

class RoomsHandler(context: Context) : JSONHandler(context) {

    // map from room ID to Room model object
    private val mRooms = HashMap<String, Room>()

    override fun process(element: JsonElement) {
        for (room in Gson().fromJson(element, Array<Room>::class.java)) {
            mRooms.put(room.id, room)
        }
    }

    override fun makeContentProviderOperations(list: ArrayList<ContentProviderOperation>) {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Rooms.CONTENT_URI)

        // The list of rooms is not large, so for simplicity we delete all of them and repopulate
        list.add(ContentProviderOperation.newDelete(uri).build())
        for (room in mRooms.values) {
            val builder = ContentProviderOperation.newInsert(uri)
            builder.withValue(ScheduleContract.Rooms.ROOM_ID, room.id)
            builder.withValue(ScheduleContract.Rooms.ROOM_NAME, room.name)
            builder.withValue(ScheduleContract.Rooms.ROOM_FLOOR, room.floor)
            list.add(builder.build())
        }
    }

    companion object {
        private val TAG = makeLogTag(RoomsHandler::class.java)
    }
}
