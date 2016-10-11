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
import android.content.res.TypedArray
import android.text.TextUtils
import android.util.AttributeSet
import android.widget.TextView

/**
 * A simple [TextView] subclass that uses [TextUtils.ellipsize] to truncate the displayed text. This is used in
 * [com.google.samples.apps.iosched.ui.PlusStreamRowViewBinder] when displaying G+ post text
 * which is converted from HTML to a [android.text.SpannableString] and sometimes causes
 * issues for the built-in TextView ellipsize function.
 */
class EllipsizedTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : TextView(context, attrs, defStyle) {

    private var mMaxLines: Int = 0

    init {

        // Attribute initialization
        val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.maxLines), defStyle, 0)

        mMaxLines = a.getInteger(0, 1)
        a.recycle()
    }

    override fun setText(text: CharSequence, type: TextView.BufferType) {
        val newText = if (width == 0 || mMaxLines > MAX_ELLIPSIZE_LINES)
            text
        else
            TextUtils.ellipsize(text, paint, (width * mMaxLines).toFloat(),
                    TextUtils.TruncateAt.END, false, null)
        super.setText(newText, type)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && oldWidth != width) {
            text = text
        }
    }

    override fun setMaxLines(maxlines: Int) {
        super.setMaxLines(maxlines)
        mMaxLines = maxlines
    }

    companion object {
        private val MAX_ELLIPSIZE_LINES = 100
    }
}
