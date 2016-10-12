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

import android.graphics.*
import android.util.Log
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.larvalabs.svgandroid.SVGBuilder
import java.io.*
import java.util.concurrent.ConcurrentLinkedQueue

class SVGTileProvider @Throws(IOException::class)
constructor(file: File, dpi: Float) : TileProvider {

    private val mPool: TileGeneratorPool

    private val mBaseMatrix: Matrix

    private val mScale: Int
    private val mDimension: Int

    /**
     * NOTE: must use a synchronize block when using [android.graphics.Picture.draw]
     */
    private val mSvgPicture: Picture

    init {
        mScale = Math.round(dpi + .3f) // Make it look nice on N7 (1.3 dpi)
        mDimension = BASE_TILE_SIZE * mScale

        mPool = TileGeneratorPool(POOL_MAX_SIZE)

        val svg = SVGBuilder().readFromInputStream(FileInputStream(file)).build()
        mSvgPicture = svg.picture
        val limits = svg.limits

        mBaseMatrix = Matrix()
        mBaseMatrix.setPolyToPoly(
                floatArrayOf(0f, 0f, limits.width(), 0f, limits.width(), limits.height()), 0,
                floatArrayOf(40.95635986328125f, 98.94217824936158f, 40.95730018615723f, 98.94123077396628f, 40.95791244506836f, 98.94186019897214f), 0, 3)
    }

    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        val tileGenerator = mPool.get()
        val tileData = tileGenerator.getTileImageData(x, y, zoom)
        mPool.restore(tileGenerator)
        return Tile(mDimension, mDimension, tileData)
    }

    private inner class TileGeneratorPool constructor(private val mMaxSize: Int) {
        private val mPool = ConcurrentLinkedQueue<TileGenerator>()

        fun get(): TileGenerator {
            val i = mPool.poll() ?: return TileGenerator()
            return i
        }

        fun restore(tileGenerator: TileGenerator) {
            if (mPool.size < mMaxSize && mPool.offer(tileGenerator)) {
                return
            }
            // pool is too big or returning to pool failed, so just try to clean
            // up.
            tileGenerator.cleanUp()
        }
    }

    inner class TileGenerator {
        private var mBitmap: Bitmap? = null
        private var mStream: ByteArrayOutputStream? = null

        init {
            mBitmap = Bitmap.createBitmap(mDimension, mDimension, Bitmap.Config.ARGB_8888)
            mStream = ByteArrayOutputStream(mDimension * mDimension * 4)
        }

        fun getTileImageData(x: Int, y: Int, zoom: Int): ByteArray {
            mStream!!.reset()

            val matrix = Matrix(mBaseMatrix)
            val scale = (Math.pow(2.0, zoom.toDouble()) * mScale).toFloat()
            matrix.postScale(scale, scale)
            matrix.postTranslate((-x * mDimension).toFloat(), (-y * mDimension).toFloat())

            mBitmap!!.eraseColor(Color.TRANSPARENT)
            val c = Canvas(mBitmap!!)
            c.matrix = matrix

            // NOTE: Picture is not thread-safe.
            synchronized(mSvgPicture) {
                mSvgPicture.draw(c)
            }

            val stream = BufferedOutputStream(mStream!!)
            mBitmap!!.compress(Bitmap.CompressFormat.PNG, 0, stream)
            try {
                stream.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error while closing tile byte stream.")
                e.printStackTrace()
            }

            return mStream!!.toByteArray()
        }

        /**
         * Attempt to free memory and remove references.
         */
        fun cleanUp() {
            mBitmap!!.recycle()
            mBitmap = null
            try {
                mStream!!.close()
            } catch (e: IOException) {
                // ignore
            }

            mStream = null
        }
    }

    companion object {
        private val TAG = makeLogTag(SVGTileProvider::class.java)

        private val POOL_MAX_SIZE = 5
        private val BASE_TILE_SIZE = 256
    }
}
