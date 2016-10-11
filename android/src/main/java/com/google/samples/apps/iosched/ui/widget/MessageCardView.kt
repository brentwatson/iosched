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
import android.graphics.Typeface
import android.support.v7.widget.CardView
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.util.LogUtils.LOGW
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

class MessageCardView : CardView, View.OnClickListener {
    private var mTitleView: TextView? = null
    private var mMessageView: TextView? = null
    private var mButtons: Array<Button>? = null
    private var mButtonTags: Array<String>? = null
    private var mListener: OnMessageCardButtonClicked? = null
    private var mRoot: View? = null

    interface OnMessageCardButtonClicked {
        fun onMessageCardButtonClicked(tag: String)
    }

    constructor(context: Context) : super(context, null, 0) {
        initialize(context, null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs, 0) {
        initialize(context, attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize(context, attrs, defStyle)
    }

    private fun initialize(context: Context, attrs: AttributeSet?, defStyle: Int) {
        val inflater = context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mRoot = inflater.inflate(R.layout.message_card, this, true)
        mTitleView = mRoot!!.findViewById(R.id.title) as TextView
        mMessageView = mRoot!!.findViewById(R.id.text) as TextView
        mButtons = arrayOf(mRoot!!.findViewById(R.id.buttonEnd) as Button, mRoot!!.findViewById(R.id.buttonStart) as Button)
        mButtonTags = arrayOf("", "")

        for (button in mButtons!!) {
            button.visibility = View.GONE
            button.setOnClickListener(this)
        }

        val a = context.obtainStyledAttributes(attrs, R.styleable.MessageCard, defStyle, 0)
        val title = a.getString(R.styleable.MessageCard_messageTitle)
        setTitle(title)
        val text = a.getString(R.styleable.MessageCard_messageText)
        if (text != null) {
            setText(text)
        }
        val button1text = a.getString(R.styleable.MessageCard_button1text)
        val button1emphasis = a.getBoolean(R.styleable.MessageCard_button1emphasis, false)
        val button1tag = a.getString(R.styleable.MessageCard_button1tag)
        val button2text = a.getString(R.styleable.MessageCard_button2text)
        val button2emphasis = a.getBoolean(R.styleable.MessageCard_button2emphasis, false)
        val button2tag = a.getString(R.styleable.MessageCard_button2tag)
        val emphasisColor = a.getColor(R.styleable.MessageCard_emphasisColor,
                resources.getColor(R.color.theme_primary))

        if (button1text != null) {
            setButton(0, button1text, button1tag, button1emphasis, 0)
        }
        if (button2text != null) {
            setButton(1, button2text, button2tag, button2emphasis, emphasisColor)
        }

        radius = resources.getDimensionPixelSize(R.dimen.card_corner_radius).toFloat()
        cardElevation = resources.getDimensionPixelSize(R.dimen.card_elevation).toFloat()
        preventCornerOverlap = false
    }

    fun setListener(listener: OnMessageCardButtonClicked) {
        mListener = listener
    }

    fun setButton(index: Int, text: String, tag: String, emphasis: Boolean, emphasisColor: Int) {
        var emphasisColor = emphasisColor
        if (index < 0 || index >= mButtons!!.size) {
            LOGW(TAG, "Invalid button index: " + index)
            return
        }
        mButtons!![index].text = text
        mButtons!![index].visibility = View.VISIBLE
        mButtonTags!![index] = tag
        if (emphasis) {
            if (emphasisColor == 0) {
                emphasisColor = resources.getColor(R.color.theme_primary)
            }
            mButtons!![index].setTextColor(emphasisColor)
            mButtons!![index].setTypeface(null, Typeface.BOLD)
        }
    }

    /**
     * Use sparingly.
     */
    fun setTitle(title: String) {
        if (TextUtils.isEmpty(title)) {
            mTitleView!!.visibility = View.GONE
        } else {
            mTitleView!!.visibility = View.VISIBLE
            mTitleView!!.text = title
        }
    }

    fun setText(text: String) {
        mMessageView!!.text = text
    }

    fun overrideBackground(bgResId: Int) {
        findViewById(R.id.card_root).setBackgroundResource(bgResId)
    }

    override fun onClick(v: View) {
        if (mListener == null) {
            return
        }

        for (i in mButtons!!.indices) {
            if (mButtons!![i] === v) {
                mListener!!.onMessageCardButtonClicked(mButtonTags!![i])
                break
            }
        }
    }

    @JvmOverloads fun dismiss(animate: Boolean = false) {
        if (!animate) {
            visibility = View.GONE
        } else {
            animate().scaleY(0.1f).alpha(0.1f).duration = ANIM_DURATION.toLong()
        }
    }

    fun show() {
        visibility = View.VISIBLE
    }

    companion object {
        private val TAG = makeLogTag(MessageCardView::class.java)
        val ANIM_DURATION = 200
    }
}
