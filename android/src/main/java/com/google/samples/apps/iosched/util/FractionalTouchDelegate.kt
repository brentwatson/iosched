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

import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View

/**
 * [TouchDelegate] that gates [MotionEvent] instances by comparing
 * then against fractional dimensions of the source view.
 *
 *
 * This is particularly useful when you want to define a rectangle in terms of
 * the source dimensions, but when those dimensions might change due to pending
 * or future layout passes.
 *
 *
 * One example is catching touches that occur in the top-right quadrant of
 * `sourceParent`, and relaying them to `targetChild`. This could be
 * done with: `
 * FractionalTouchDelegate.setupDelegate(sourceParent, targetChild, new RectF(0.5f, 0f, 1f, 0.5f));
` *
 */
class FractionalTouchDelegate(private val mSource: View, private val mTarget: View, private val mSourceFraction: RectF) : TouchDelegate(Rect(0, 0, 0, 0), mTarget) {

    private val mScrap = Rect()

    /** Cached full dimensions of [.mSource].  */
    private val mSourceFull = Rect()
    /** Cached projection of [.mSourceFraction] onto [.mSource].  */
    private val mSourcePartial = Rect()

    private var mDelegateTargeted: Boolean = false

    /**
     * Consider updating [.mSourcePartial] when [.mSource]
     * dimensions have changed.
     */
    private fun updateSourcePartial() {
        mSource.getHitRect(mScrap)
        if (mScrap != mSourceFull) {
            // Copy over and calculate fractional rectangle
            mSourceFull.set(mScrap)

            val width = mSourceFull.width()
            val height = mSourceFull.height()

            mSourcePartial.left = (mSourceFraction.left * width).toInt()
            mSourcePartial.top = (mSourceFraction.top * height).toInt()
            mSourcePartial.right = (mSourceFraction.right * width).toInt()
            mSourcePartial.bottom = (mSourceFraction.bottom * height).toInt()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        updateSourcePartial()

        // The logic below is mostly copied from the parent class, since we
        // can't update private mBounds variable.

        // http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob;
        // f=core/java/android/view/TouchDelegate.java;hb=eclair#l98

        val sourcePartial = mSourcePartial
        val target = mTarget

        val x = event.x.toInt()
        val y = event.y.toInt()

        var sendToDelegate = false
        var hit = true
        var handled = false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> if (sourcePartial.contains(x, y)) {
                mDelegateTargeted = true
                sendToDelegate = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_MOVE -> {
                sendToDelegate = mDelegateTargeted
                if (sendToDelegate) {
                    if (!sourcePartial.contains(x, y)) {
                        hit = false
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                sendToDelegate = mDelegateTargeted
                mDelegateTargeted = false
            }
        }

        if (sendToDelegate) {
            if (hit) {
                event.setLocation((target.width / 2).toFloat(), (target.height / 2).toFloat())
            } else {
                event.setLocation(-1f, -1f)
            }
            handled = target.dispatchTouchEvent(event)
        }
        return handled
    }

    companion object {

        /**
         * Helper to create and setup a [FractionalTouchDelegate] between the
         * given [View].

         * @param source Larger source [View], usually a parent, that will be
         * *            assigned [View.setTouchDelegate].
         * *
         * @param target Smaller target [View] which will receive
         * *            [MotionEvent] that land in requested fractional area.
         * *
         * @param sourceFraction Fractional area projected onto source [View]
         * *            which determines when [MotionEvent] will be passed to
         * *            target [View].
         */
        fun setupDelegate(source: View, target: View, sourceFraction: RectF) {
            source.touchDelegate = FractionalTouchDelegate(source, target, sourceFraction)
        }
    }
}
