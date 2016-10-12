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

import android.content.AsyncTaskLoader
import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.util.MapUtils
import java.util.*

/**
 * Background task that queries the content provider and prepares a list of [MarkerModel]s
 * wrapped in a [com.google.samples.apps.iosched.map.util.MarkerLoadingTask.MarkerEntry]
 * that can be used to create Markers.
 */
class MarkerLoadingTask(context: Context) : AsyncTaskLoader<List<MarkerLoadingTask.MarkerEntry>>(context) {

    override fun loadInBackground(): List<MarkerEntry> {
        var list: MutableList<MarkerEntry>? = null

        // Create a URI to get a cursor of all map markers
        val uri = ScheduleContract.MapMarkers.buildMarkerUri()
        val cursor = context.contentResolver.query(uri, MarkerQuery.PROJECTION,
                null, null, null)

        // Create a MarkerModel for each entry
        val count = cursor!!.count
        if (cursor != null) {

            list = ArrayList<MarkerEntry>(count)
            val labelIconGenerator = MapUtils.getLabelIconGenerator(context)
            cursor.moveToFirst()

            while (!cursor.isAfterLast) {
                // get data
                val id = cursor.getString(MarkerQuery.MARKER_ID)
                val floor = cursor.getInt(MarkerQuery.MARKER_FLOOR)
                val lat = cursor.getFloat(MarkerQuery.MARKER_LATITUDE)
                val lon = cursor.getFloat(MarkerQuery.MARKER_LONGITUDE)
                val type = MapUtils.detectMarkerType(cursor.getString(MarkerQuery.MARKER_TYPE))
                val label = cursor.getString(MarkerQuery.MARKER_LABEL)

                val position = LatLng(lat.toDouble(), lon.toDouble())
                var marker: MarkerOptions? = null
                if (type == MarkerModel.TYPE_LABEL) {
                    // Label markers contain the label as its icon
                    marker = MapUtils.createLabelMarker(labelIconGenerator, id, position, label)
                } else if (type != MarkerModel.TYPE_INACTIVE) {
                    // All other markers (that are not inactive) contain a pin icon
                    marker = MapUtils.createPinMarker(id, position)
                }

                val model = MarkerModel(id, floor, type, label, null)
                val entry = MarkerEntry(model, marker!!)

                list.add(entry)

                cursor.moveToNext()
            }
            cursor.close()
        }

        return list!!
    }


    private interface MarkerQuery {
        companion object {

            val PROJECTION = arrayOf(ScheduleContract.MapMarkers.MARKER_ID, ScheduleContract.MapMarkers.MARKER_FLOOR, ScheduleContract.MapMarkers.MARKER_LATITUDE, ScheduleContract.MapMarkers.MARKER_LONGITUDE, ScheduleContract.MapMarkers.MARKER_TYPE, ScheduleContract.MapMarkers.MARKER_LABEL)

            val MARKER_ID = 0
            val MARKER_FLOOR = 1
            val MARKER_LATITUDE = 2
            val MARKER_LONGITUDE = 3
            val MARKER_TYPE = 4
            val MARKER_LABEL = 5
        }
    }

    inner class MarkerEntry(var model: MarkerModel, var options: MarkerOptions)
}
