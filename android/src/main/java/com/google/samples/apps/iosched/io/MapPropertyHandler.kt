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
import com.google.samples.apps.iosched.io.map.model.MapData
import com.google.samples.apps.iosched.io.map.model.Marker
import com.google.samples.apps.iosched.io.map.model.Tile
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.util.*

class MapPropertyHandler(context: Context) : JSONHandler(context) {

    // maps floor# to tile overlay for that floor
    private val mTileOverlays = HashMap<String, Tile>()

    // maps floor# to a list of markers on that floor
    private val mMarkers = HashMap<String, ArrayList<Marker>>()

    override fun process(element: JsonElement) {
        for (mapData in Gson().fromJson(element, Array<MapData>::class.java)) {
            if (mapData.tiles != null) {
                processTileOverlays(mapData.tiles)
            }
            if (mapData.markers != null) {
                processMarkers(mapData.markers)
            }
        }
    }

    val tileOverlays: Collection<Tile>
        get() = mTileOverlays.values

    private fun processTileOverlays(mapTiles: Map<String, Tile>) {
        for ((key, value) in mapTiles) {
            mTileOverlays.put(key, value)
        }
    }

    private fun processMarkers(markers: Map<String, Array<Marker>>) {
        for (floor in markers.keys) {
            if (!mMarkers.containsKey(floor)) {
                mMarkers.put(floor, ArrayList<Marker>())
            }
            for (marker in markers[floor]!!) {
                mMarkers[floor]!!.add(marker)
            }
        }
    }

    override fun makeContentProviderOperations(list: ArrayList<ContentProviderOperation>) {
        buildMarkers(list)
        buildTiles(list)
    }

    private fun buildMarkers(list: ArrayList<ContentProviderOperation>) {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(ScheduleContract.MapMarkers.CONTENT_URI)

        list.add(ContentProviderOperation.newDelete(uri).build())

        for (floor in mMarkers.keys) {
            for (marker in mMarkers[floor]!!) {
                val builder = ContentProviderOperation.newInsert(uri)
                builder.withValue(ScheduleContract.MapMarkers.MARKER_ID, marker.id)
                builder.withValue(ScheduleContract.MapMarkers.MARKER_FLOOR, floor)
                builder.withValue(ScheduleContract.MapMarkers.MARKER_LABEL, marker.title)
                builder.withValue(ScheduleContract.MapMarkers.MARKER_LATITUDE, marker.lat)
                builder.withValue(ScheduleContract.MapMarkers.MARKER_LONGITUDE, marker.lng)
                builder.withValue(ScheduleContract.MapMarkers.MARKER_TYPE, marker.type)
                list.add(builder.build())
            }
        }
    }

    private fun buildTiles(list: ArrayList<ContentProviderOperation>) {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(ScheduleContract.MapTiles.CONTENT_URI)

        list.add(ContentProviderOperation.newDelete(uri).build())

        for (floor in mTileOverlays.keys) {
            val tileOverlay = mTileOverlays[floor]
            val builder = ContentProviderOperation.newInsert(uri)
            builder.withValue(ScheduleContract.MapTiles.TILE_FLOOR, floor)
            builder.withValue(ScheduleContract.MapTiles.TILE_FILE, tileOverlay!!.filename)
            builder.withValue(ScheduleContract.MapTiles.TILE_URL, tileOverlay.url)
            list.add(builder.build())
        }
    }

    companion object {
        private val TAG = makeLogTag(MapPropertyHandler::class.java)
    }
}
