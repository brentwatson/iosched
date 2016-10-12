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
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.google.samples.apps.iosched.util.MapUtils
import com.jakewharton.disklrucache.DiskLruCache
import java.io.IOException
import java.util.*

/**
 * Background task that queries the content provider and prepares a list of
 * [com.google.android.gms.maps.model.TileOverlay]s
 * for addition to the map.
 * A tile overlay is always tied to a floor in Moscone and is loaded directly from an SVG file.
 * A [DiskLruCache] is used to create a [CachedTileProvider] for each overlay.
 *
 * Note: The CachedTileProvider **must** be closed when the encapsulating map is stopped.
 * (See
 * [CachedTileProvider.closeCache]
 */
class TileLoadingTask(context: Context, private val mDPI: Float) : AsyncTaskLoader<List<TileLoadingTask.TileEntry>>(context) {


    override fun loadInBackground(): List<TileEntry> {
        var list: MutableList<TileEntry>? = null
        // Create a URI to get a cursor for all map tile entries.
        val uri = ScheduleContract.MapTiles.buildUri()
        val cursor = context.contentResolver.query(uri,
                OverlayQuery.PROJECTION, null, null, null)

        if (cursor != null) {
            // Create a TileProvider for each entry in the cursor
            val count = cursor.count

            // Initialise the tile cache that is reused for all TileProviders.
            // Note that the cache *MUST* be closed when the encapsulating Fragment is stopped.
            val tileCache = MapUtils.openDiskCache(context)

            list = ArrayList<TileEntry>(count)
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val floor = cursor.getInt(OverlayQuery.TILE_FLOOR)
                val file = cursor.getString(OverlayQuery.TILE_FILE)

                val f = MapUtils.getTileFile(context.applicationContext, file)
                if (f == null || !f.exists()) {
                    // Skip the file if it is invalid or does not exist.
                    break
                }

                val provider: CachedTileProvider
                try {
                    val svgProvider = SVGTileProvider(f, mDPI)
                    // Wrap the SVGTileProvider in a CachedTileProvider for caching on disk.
                    provider = CachedTileProvider(Integer.toString(floor), svgProvider,
                            tileCache!!)
                } catch (e: IOException) {
                    LOGD(TAG, "Could not create Tile Provider.")
                    break
                }

                list.add(TileEntry(floor, provider))
                cursor.moveToNext()
            }

            cursor.close()
        }

        return list!!
    }


    private interface OverlayQuery {
        companion object {

            val PROJECTION = arrayOf(ScheduleContract.MapTiles.TILE_FLOOR, ScheduleContract.MapTiles.TILE_FILE)

            val TILE_FLOOR = 0
            val TILE_FILE = 1
        }
    }

    inner class TileEntry internal constructor(var floor: Int, var provider: CachedTileProvider)

    companion object {

        private val TAG = makeLogTag(TileLoadingTask::class.java)
    }

}
