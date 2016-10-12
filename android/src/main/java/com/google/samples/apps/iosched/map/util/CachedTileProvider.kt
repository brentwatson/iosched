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
package com.google.samples.apps.iosched.map.util

import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.jakewharton.disklrucache.DiskLruCache
import java.io.*

/**
 * Wrapper that provides a disk-based LRU cache for a TileProvider.

 * @see com.jakewharton.disklrucache.DiskLruCache
 */
class CachedTileProvider
/**
 * TileProvider that wraps another TileProvider and caches all Tiles in a DiskLruCache.
 *
 * A [com.jakewharton.disklrucache.DiskLruCache] can be reused across multiple
 * instances.
 * The keyTag is used to annotate entries for this TileProvider, it is recommended to use a
 * unique
 * String for each instance to prevent collisions.

 *
 * NOTE: The supplied [com.jakewharton.disklrucache.DiskLruCache] requires space for
 * 3 entries per cached object.

 * @param keyTag       identifier used to identify tiles for this CachedTileProvider instance
 * *
 * @param tileProvider tiles from this TileProvider will be cached.
 * *
 * @param cache        the cache used to store tiles
 */
(private val mKeyTag: String, private val mTileProvider: TileProvider, private val mCache: DiskLruCache) : TileProvider {

    /**
     * Load a tile.
     * If cached, the data for the tile is read from the underlying cache, otherwise the tile is
     * generated by the [com.google.android.gms.maps.model.TileProvider] and added to the
     * cache.
     */
    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        val key = CachedTileProvider.generateKey(x, y, zoom, mKeyTag)
        var tile = getCachedTile(key)

        if (tile == null) {
            // tile not cached, load from provider and then cache
            tile = mTileProvider.getTile(x, y, zoom)
            if (cacheTile(key, tile)) {
                LOGD(TAG, "Added tile to cache " + key)
            }
        }
        return tile!!
    }

    /**
     * Load a tile from cache.
     * Returns null if there is no corresponding cache entry or it could not be loaded.
     */
    private fun getCachedTile(key: String): Tile? {
        if (mCache.isClosed) {
            return null
        }
        try {
            val snapshot = mCache.get(key) ?: // tile is not in cache
                    return null

            val data = readStreamAsByteArray(snapshot.getInputStream(INDEX_DATA))
            val height = readStreamAsInt(snapshot.getInputStream(INDEX_HEIGHT))
            val width = readStreamAsInt(snapshot.getInputStream(INDEX_WIDTH))
            if (data != null) {
                LOGD(TAG, "Cache hit for tile " + key)
                return Tile(width, height, data)
            }

        } catch (e: IOException) {
            // ignore error
        }

        return null
    }

    private fun cacheTile(key: String, tile: Tile): Boolean {
        if (mCache.isClosed) {
            return false
        }
        try {
            val editor = mCache.edit(key) ?: // editor is not available
                    return false
            writeByteArrayToStream(tile.data, editor.newOutputStream(INDEX_DATA))
            writeIntToStream(tile.height, editor.newOutputStream(INDEX_HEIGHT))
            writeIntToStream(tile.width, editor.newOutputStream(INDEX_WIDTH))
            editor.commit()
            return true
        } catch (e: IOException) {
            // Tile could not be cached
        }

        return false
    }

    @Throws(IOException::class)
    fun closeCache() {
        mCache.close()
    }

    companion object {

        private val TAG = makeLogTag(SVGTileProvider::class.java)

        private val KEY_FORMAT = "%d_%d_%d_%s"

        // Index for cache entry streams
        private val INDEX_DATA = 0
        private val INDEX_HEIGHT = 1
        private val INDEX_WIDTH = 2


        private fun generateKey(x: Int, y: Int, zoom: Int, tag: String): String {
            return String.format(KEY_FORMAT, x, y, zoom, tag)
        }

        @Throws(IOException::class)
        private fun writeByteArrayToStream(data: ByteArray, stream: OutputStream) {
            try {
                stream.write(data)
            } finally {
                stream.close()
            }
        }

        @Throws(IOException::class)
        private fun writeIntToStream(data: Int, stream: OutputStream) {
            val dos = DataOutputStream(stream)
            try {
                dos.writeInt(data)
            } finally {
                try {
                    dos.close()
                } finally {
                    stream.close()
                }
            }
        }

        @Throws(IOException::class)
        private fun readStreamAsByteArray(inputStream: InputStream): ByteArray? {
            val buffer = ByteArrayOutputStream()
            var read = 0
            val data = ByteArray(1024)
            try {
                while (read != -1) {
                    read = inputStream.read(data, 0, data.size)
                    buffer.write(data, 0, read)
                }
            } finally {
                inputStream.close()
            }
            return buffer.toByteArray()
        }


        @Throws(IOException::class)
        private fun readStreamAsInt(inputStream: InputStream): Int {
            val buffer = DataInputStream(inputStream)
            try {
                return buffer.readInt()
            } finally {
                inputStream.close()
            }
        }
    }

}