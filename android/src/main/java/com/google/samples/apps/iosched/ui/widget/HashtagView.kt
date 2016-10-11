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

package com.google.samples.apps.iosched.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.FrameLayout

import com.google.samples.apps.iosched.R

/**
 * A view that draws fancy horizontal lines to frame it's content
 */
class HashtagView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {

    private val mLinesPaint: Paint
    private var mLinePoints: FloatArray? = null

    init {
        mLinesPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mLinesPaint.color = LINE_COLOUR
        mLinesPaint.style = Paint.Style.STROKE
        mLinesPaint.strokeWidth = context.resources.getDimensionPixelSize(
                R.dimen.hashtag_line_height).toFloat()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // calculate points for two horizontal lines spaced at 1/3 & 2/3 of the height, occupying
        // 2/3 of the width (centered).
        val thirdHeight = measuredHeight / 3
        val sixthWidth = measuredWidth / 6
        mLinePoints = floatArrayOf(
                // line 1
                sixthWidth.toFloat(), thirdHeight.toFloat(), (5 * sixthWidth).toFloat(), thirdHeight.toFloat(),
                // line 2
                sixthWidth.toFloat(), (2 * thirdHeight).toFloat(), (5 * sixthWidth).toFloat(), (2 * thirdHeight).toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawLines(mLinePoints!!, mLinesPaint)
    }

    companion object {

        private val LINE_COLOUR = 0x59ffffff
    }
}
