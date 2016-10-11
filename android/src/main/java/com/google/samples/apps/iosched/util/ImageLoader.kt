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
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.support.annotation.DrawableRes
import android.widget.ImageView
import com.bumptech.glide.BitmapRequestBuilder
import com.bumptech.glide.BitmapTypeRequest
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelCache
import com.bumptech.glide.load.model.stream.BaseGlideUrlLoader
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestListener
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.util.regex.Pattern

class ImageLoader
/**
 * Construct a standard ImageLoader object.
 */
(context: Context) {

    private val mGlideModelRequest: BitmapTypeRequest<String>
    private val mCenterCrop: CenterCrop

    private var mPlaceHolderResId = -1

    init {
        val imageLoader = VariableWidthImageLoader(context)
        mGlideModelRequest = Glide.with(context).using(imageLoader).from(String::class.java).asBitmap()
        mCenterCrop = CenterCrop(Glide.get(context).bitmapPool)
    }

    /**
     * Construct an ImageLoader with a default placeholder drawable.
     */
    constructor(context: Context, placeHolderResId: Int) : this(context) {
        mPlaceHolderResId = placeHolderResId
    }

    /**
     * Load an image from a url into an ImageView using the default placeholder
     * drawable if available.
     * @param url The web URL of an image.
     * *
     * @param imageView The target ImageView to load the image into.
     * *
     * @param requestListener A listener to monitor the request result.
     * *
     * @param placeholderOverride A drawable to use as a placeholder for this specific image.
     * *                            If this parameter is present, [.mPlaceHolderResId]
     * *                            if ignored for this request.
     */
    @JvmOverloads fun loadImage(url: String, imageView: ImageView, requestListener: RequestListener<String, Bitmap>?,
                                placeholderOverride: Drawable? = null, crop: Boolean = false) {
        val request = beginImageLoad(url, requestListener!!, crop).animate(R.anim.image_fade_in)
        if (placeholderOverride != null) {
            request.placeholder(placeholderOverride)
        } else if (mPlaceHolderResId != -1) {
            request.placeholder(mPlaceHolderResId)
        }
        request.into(imageView)
    }

    fun beginImageLoad(url: String,
                       requestListener: RequestListener<String, Bitmap>, crop: Boolean): BitmapRequestBuilder<*, *> {
        if (crop) {
            return mGlideModelRequest.load(url).listener(requestListener).transform(mCenterCrop)
        } else {
            return mGlideModelRequest.load(url).listener(requestListener)
        }
    }

    /**
     * Load an image from a url into an ImageView using the default placeholder
     * drawable if available.
     * @param url The web URL of an image.
     * *
     * @param imageView The target ImageView to load the image into.
     * *
     * @param crop True to apply a center crop to the image.
     */
    @JvmOverloads fun loadImage(url: String, imageView: ImageView, crop: Boolean = false) {
        loadImage(url, imageView, null, null, crop)
    }

    fun loadImage(context: Context, @DrawableRes drawableResId: Int, imageView: ImageView) {
        Glide.with(context).load(drawableResId).into(imageView)
    }

    private class VariableWidthImageLoader(context: Context) : BaseGlideUrlLoader<String>(context, urlCache) {

        /**
         * If the URL contains a special variable width indicator (eg "__w-200-400-800__")
         * we get the buckets from the URL (200, 400 and 800 in the example) and replace
         * the URL with the best bucket for the requested width (the bucket immediately
         * larger than the requested width).
         */
        override fun getUrl(model: String, width: Int, height: Int): String {
            var model = model
            val m = PATTERN.matcher(model)
            var bestBucket = 0
            if (m.find()) {
                val found = m.group(1).split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (bucketStr in found) {
                    bestBucket = Integer.parseInt(bucketStr)
                    if (bestBucket >= width) {
                        // the best bucket is the first immediately bigger than the requested width
                        break
                    }
                }
                if (bestBucket > 0) {
                    model = m.replaceFirst("w" + bestBucket)
                }
            }
            return model
        }

        companion object {
            private val PATTERN = Pattern.compile("__w-((?:-?\\d+)+)__")
        }
    }

    companion object {
        private val TAG = makeLogTag(ImageLoader::class.java)
        private val urlCache = ModelCache<String, GlideUrl>(150)
    }
}
/**
 * Load an image from a url into an ImageView using the default placeholder
 * drawable if available.
 * @param url The web URL of an image.
 * *
 * @param imageView The target ImageView to load the image into.
 * *
 * @param requestListener A listener to monitor the request result.
 */
/**
 * Load an image from a url into an ImageView using the given placeholder drawable.

 * @param url The web URL of an image.
 * *
 * @param imageView The target ImageView to load the image into.
 * *
 * @param requestListener A listener to monitor the request result.
 * *
 * @param placeholderOverride A placeholder to use in place of the default placholder.
 */
/*crop*/
/**
 * Load an image from a url into the given image view using the default placeholder if
 * available.
 * @param url The web URL of an image.
 * *
 * @param imageView The target ImageView to load the image into.
 */
/*crop*/
