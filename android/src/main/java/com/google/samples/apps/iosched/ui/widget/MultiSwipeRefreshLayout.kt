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
import android.graphics.drawable.Drawable
import android.support.v4.widget.SwipeRefreshLayout
import android.util.AttributeSet
import com.google.samples.apps.iosched.R

class MultiSwipeRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : SwipeRefreshLayout(context, attrs) {
    private var mCanChildScrollUpCallback: CanChildScrollUpCallback? = null

    private val mForegroundDrawable: Drawable?

    init {
        val a = context.obtainStyledAttributes(attrs,
                R.styleable.MultiSwipeRefreshLayout, 0, 0)

        mForegroundDrawable = a.getDrawable(R.styleable.MultiSwipeRefreshLayout_foreground)
        if (mForegroundDrawable != null) {
            mForegroundDrawable.callback = this
            setWillNotDraw(false)
        }

        a.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mForegroundDrawable?.setBounds(0, 0, w, h)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        mForegroundDrawable?.draw(canvas)
    }

    fun setCanChildScrollUpCallback(canChildScrollUpCallback: CanChildScrollUpCallback) {
        mCanChildScrollUpCallback = canChildScrollUpCallback
    }

    interface CanChildScrollUpCallback {
        fun canSwipeRefreshChildScrollUp(): Boolean
    }

    override fun canChildScrollUp(): Boolean {
        if (mCanChildScrollUpCallback != null) {
            return mCanChildScrollUpCallback!!.canSwipeRefreshChildScrollUp()
        }
        return super.canChildScrollUp()
    }
}
