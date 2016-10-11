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
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.SeekBar

class NumberRatingBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : SeekBar(context, attrs, defStyle) {
    private var mUserSeekBarChangeListener: SeekBar.OnSeekBarChangeListener? = null

    private val mSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            updateSecondaryProgress()
            if (mUserSeekBarChangeListener != null) {
                mUserSeekBarChangeListener!!.onProgressChanged(seekBar, progress, fromUser)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            if (mUserSeekBarChangeListener != null) {
                mUserSeekBarChangeListener!!.onStartTrackingTouch(seekBar)
            }
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            if (mUserSeekBarChangeListener != null) {
                mUserSeekBarChangeListener!!.onStopTrackingTouch(seekBar)
            }
        }
    }

    init {
        super.setOnSeekBarChangeListener(mSeekBarChangeListener)
        updateSecondaryProgress()
        thumb = null
    }

    override fun setOnSeekBarChangeListener(listener: SeekBar.OnSeekBarChangeListener) {
        mUserSeekBarChangeListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A delectable hack.
        event.offsetLocation((width / 5).toFloat(), 0f)
        return super.onTouchEvent(event)
    }

    private fun updateSecondaryProgress() {
        // Another delectable hack.
        secondaryProgress = progress - 1
    }
}
