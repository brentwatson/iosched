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

package com.google.samples.apps.iosched.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.google.samples.apps.iosched.R

/**
 * Extension of FrameLayout that assumes a measured (non-zero) width and sets the
 * height according to the provided aspect ratio.
 */
class AspectRatioView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {
    private var mAspectRatio = 0f

    init {

        val a = context.obtainStyledAttributes(attrs,
                R.styleable.AspectRatioView, defStyle, 0)

        mAspectRatio = a.getFloat(R.styleable.AspectRatioView_aspectRatio, 0f)

        if (mAspectRatio == 0f) {
            throw IllegalArgumentException("You must specify an aspect ratio when using the " + "AspectRatioView.")
        }
        a.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)

        val width: Int
        val height: Int
        if (mAspectRatio != 0f) {
            width = widthSize
            height = (width / mAspectRatio).toInt()
            val exactWidthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val exactHeightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            super.onMeasure(exactWidthSpec, exactHeightSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
