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

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.util.Property
import android.widget.FrameLayout
import com.google.samples.apps.iosched.R

class DrawShadowFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
    private val mShadowDrawable: Drawable?
    private var mShadowNinePatchDrawable: NinePatchDrawable? = null
    private var mShadowTopOffset: Int = 0
    private var mShadowVisible: Boolean = false
    private var mWidth: Int = 0
    private var mHeight: Int = 0
    private var mAnimator: ObjectAnimator? = null
    private var mAlpha = 1f

    init {
        val a = context.obtainStyledAttributes(attrs,
                R.styleable.DrawShadowFrameLayout, 0, 0)

        mShadowDrawable = a.getDrawable(R.styleable.DrawShadowFrameLayout_shadowDrawable)
        if (mShadowDrawable != null) {
            mShadowDrawable.callback = this
            if (mShadowDrawable is NinePatchDrawable) {
                mShadowNinePatchDrawable = mShadowDrawable as NinePatchDrawable?
            }
        }

        mShadowVisible = a.getBoolean(R.styleable.DrawShadowFrameLayout_shadowVisible, true)
        setWillNotDraw(!mShadowVisible || mShadowDrawable == null)

        a.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mWidth = w
        mHeight = h
        updateShadowBounds()
    }

    private fun updateShadowBounds() {
        mShadowDrawable?.setBounds(0, mShadowTopOffset, mWidth, mHeight)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (mShadowDrawable != null && mShadowVisible) {
            if (mShadowNinePatchDrawable != null) {
                mShadowNinePatchDrawable!!.paint.alpha = (255 * mAlpha).toInt()
            }
            mShadowDrawable.draw(canvas)
        }
    }

    fun setShadowTopOffset(shadowTopOffset: Int) {
        this.mShadowTopOffset = shadowTopOffset
        updateShadowBounds()
        ViewCompat.postInvalidateOnAnimation(this)
    }

    fun setShadowVisible(shadowVisible: Boolean, animate: Boolean) {
        this.mShadowVisible = shadowVisible
        if (mAnimator != null) {
            mAnimator!!.cancel()
            mAnimator = null
        }

        if (animate && mShadowDrawable != null) {
            mAnimator = ObjectAnimator.ofFloat(this, SHADOW_ALPHA,
                    if (shadowVisible) 0f else 1f,
                    if (shadowVisible) 1f else 0f)
            mAnimator!!.duration = 1000
            mAnimator!!.start()
        }

        ViewCompat.postInvalidateOnAnimation(this)
        setWillNotDraw(!mShadowVisible || mShadowDrawable == null)
    }

    companion object {

        private val SHADOW_ALPHA = object : Property<DrawShadowFrameLayout, Float>(Float::class.java, "shadowAlpha") {
            override fun get(dsfl: DrawShadowFrameLayout): Float {
                return dsfl.mAlpha
            }

            override fun set(dsfl: DrawShadowFrameLayout, value: Float?) {
                dsfl.mAlpha = value!!
                ViewCompat.postInvalidateOnAnimation(dsfl)
            }
        }
    }
}
