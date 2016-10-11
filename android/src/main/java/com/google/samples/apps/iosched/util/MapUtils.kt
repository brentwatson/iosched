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

import android.content.Context
import android.support.annotation.DrawableRes
import android.text.TextUtils
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.ui.IconGenerator
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.map.util.MarkerModel
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.LOGE
import com.jakewharton.disklrucache.DiskLruCache
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

object MapUtils {

    private val TILE_PATH = "maptiles"
    private val TAG = LogUtils.makeLogTag(MapUtils::class.java)

    /**
     * Returns the room type for a [com.google.samples.apps.iosched.map.util.MarkerModel]
     * for a given String.
     */
    fun detectMarkerType(markerType: String): Int {
        if (TextUtils.isEmpty(markerType)) {
            return MarkerModel.TYPE_INACTIVE
        }
        val tags = markerType.toUpperCase(Locale.US)
        if (tags.contains("SESSION")) {
            return MarkerModel.TYPE_SESSION
        } else if (tags.contains("PLAIN")) {
            return MarkerModel.TYPE_PLAIN
        } else if (tags.contains("LABEL")) {
            return MarkerModel.TYPE_LABEL
        } else if (tags.contains("CODELAB")) {
            return MarkerModel.TYPE_CODELAB
        } else if (tags.contains("SANDBOX")) {
            return MarkerModel.TYPE_SANDBOX
        } else if (tags.contains("OFFICEHOURS")) {
            return MarkerModel.TYPE_OFFICEHOURS
        } else if (tags.contains("MISC")) {
            return MarkerModel.TYPE_MISC
        } else if (tags.contains("MOSCONE")) {
            return MarkerModel.TYPE_MOSCONE
        } else if (tags.contains("INACTIVE")) {
            return MarkerModel.TYPE_INACTIVE
        }
        return MarkerModel.TYPE_INACTIVE // default
    }

    /**
     * Returns the drawable Id of icon to use for a room type.
     */
    @DrawableRes
    fun getRoomIcon(markerType: Int): Int {
        when (markerType) {
            MarkerModel.TYPE_SESSION -> return R.drawable.ic_map_session
            MarkerModel.TYPE_PLAIN -> return R.drawable.ic_map_pin
            MarkerModel.TYPE_CODELAB -> return R.drawable.ic_map_codelab
            MarkerModel.TYPE_SANDBOX -> return R.drawable.ic_map_sandbox
            MarkerModel.TYPE_OFFICEHOURS -> return R.drawable.ic_map_officehours
            MarkerModel.TYPE_MISC -> return R.drawable.ic_map_misc
            MarkerModel.TYPE_MOSCONE -> return R.drawable.ic_map_moscone
            else -> return R.drawable.ic_map_pin
        }
    }

    /**
     * True if the info details for this room type should only contain a title.
     */
    fun hasInfoTitleOnly(markerType: Int): Boolean {
        return markerType == MarkerModel.TYPE_PLAIN
    }


    /**
     * True if the info details for this room type contain a title and a list of sessions.
     */
    fun hasInfoSessionList(markerType: Int): Boolean {
        return markerType != MarkerModel.TYPE_INACTIVE && markerType != MarkerModel.TYPE_LABEL
                && markerType != MarkerModel.TYPE_CODELAB
    }

    /**
     * True if the info details for this room type contain a title and a list of sessions.
     */
    fun hasInfoFirstDescriptionOnly(markerType: Int): Boolean {
        return markerType == MarkerModel.TYPE_CODELAB
    }


    fun hasInfoSessionListIcons(markerType: Int): Boolean {
        return markerType == MarkerModel.TYPE_SANDBOX
    }

    /**
     * Creates a marker for a session.

     * @param id Id to be embedded as the title
     */
    fun createPinMarker(id: String, position: LatLng): MarkerOptions {
        val icon = BitmapDescriptorFactory.fromResource(R.drawable.map_marker_unselected)
        return MarkerOptions().position(position).title(id).icon(icon).anchor(0.5f, 0.85526f).visible(
                false)
    }

    /**
     * Creates a new IconGenerator for labels on the map.
     */
    fun getLabelIconGenerator(c: Context): IconGenerator {
        val iconFactory = IconGenerator(c)
        iconFactory.setTextAppearance(R.style.MapLabel)
        iconFactory.setBackground(null)

        return iconFactory
    }

    /**
     * Creates a marker for a label.

     * @param iconFactory Reusable IconFactory
     * *
     * @param id          Id to be embedded as the title
     * *
     * @param label       Text to be shown on the label
     */
    fun createLabelMarker(iconFactory: IconGenerator, id: String,
                          position: LatLng, label: String): MarkerOptions {
        val icon = BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(label))

        return MarkerOptions().position(position).title(id).icon(icon).anchor(0.5f, 0.5f).visible(false)
    }

    /**
     * Creates a marker for Moscone Center.
     */
    fun createMosconeMarker(position: LatLng): MarkerOptions {
        val title = "MOSCONE"

        val icon = BitmapDescriptorFactory.fromResource(R.drawable.map_marker_moscone)

        return MarkerOptions().position(position).title(title).icon(icon).visible(false)
    }

    private var mapTileAssets: Array<String>? = null

    /**
     * Returns true if the given tile file exists as a local asset.
     */
    fun hasTileAsset(context: Context, filename: String): Boolean {

        //cache the list of available files
        if (mapTileAssets == null) {
            try {
                mapTileAssets = context.assets.list("maptiles")
            } catch (e: IOException) {
                // no assets
                mapTileAssets = arrayOfNulls<String>(0) as Array<String>
            }

        }

        // search for given filename
        for (s in mapTileAssets!!) {
            if (s == filename) {
                return true
            }
        }
        return false
    }

    /**
     * Copy the file from the assets to the map tiles directory if it was
     * shipped with the APK.
     */
    fun copyTileAsset(context: Context, filename: String): Boolean {
        if (!hasTileAsset(context, filename)) {
            // file does not exist as asset
            return false
        }

        // copy file from asset to internal storage
        try {
            val `is` = context.assets.open(TILE_PATH + File.separator + filename)
            val f = getTileFile(context, filename)
            val os = FileOutputStream(f)

            val buffer = ByteArray(1024)
            var dataSize: Int = Int.MIN_VALUE
            while (dataSize > 0) {
                dataSize = `is`.read(buffer)
                os.write(buffer, 0, dataSize)
            }
            os.close()
        } catch (e: IOException) {
            return false
        }

        return true
    }

    /**
     * Return a [File] pointing to the storage location for map tiles.
     */
    fun getTileFile(context: Context, filename: String): File {
        val folder = File(context.filesDir, TILE_PATH)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return File(folder, filename)
    }


    fun removeUnusedTiles(mContext: Context, usedTiles: ArrayList<String>) {
        // remove all files are stored in the tile path but are not used
        val folder = File(mContext.filesDir, TILE_PATH)
        val unused = folder.listFiles { dir, filename -> !usedTiles.contains(filename) }

        if (unused != null) {
            for (f in unused) {
                f.delete()
            }
        }
    }

    fun hasTile(mContext: Context, filename: String): Boolean {
        return getTileFile(mContext, filename).exists()
    }

    private val MAX_DISK_CACHE_BYTES = 1024 * 1024 * 2 // 2MB

    fun openDiskCache(c: Context): DiskLruCache? {
        val cacheDir = File(c.cacheDir, "tiles")
        try {
            return DiskLruCache.open(cacheDir, 1, 3, MAX_DISK_CACHE_BYTES.toLong())
        } catch (e: IOException) {
            LOGE(TAG, "Couldn't open disk cache.")

        }

        return null
    }

    fun clearDiskCache(c: Context) {
        val cache = openDiskCache(c)
        if (cache != null) {
            try {
                LOGD(TAG, "Clearing map tile disk cache")
                cache.delete()
                cache.close()
            } catch (e: IOException) {
                // ignore
            }

        }
    }
}
