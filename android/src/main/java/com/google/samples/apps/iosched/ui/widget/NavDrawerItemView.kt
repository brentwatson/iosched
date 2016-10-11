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
import android.content.res.ColorStateList
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.samples.apps.iosched.R

/**
 * A compound view for nav drawer items.  This neatly encapsulates states, tinting text and
 * icons and setting a background when in state_activated.
 */
class NavDrawerItemView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ForegroundLinearLayout(context, attrs!!) {

    private var mIconTints: ColorStateList? = null

    init {
        orientation = LinearLayout.HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.navdrawer_item_view, this, true)
        val a = context.obtainStyledAttributes(attrs, R.styleable.NavDrawerItemView)
        if (a.hasValue(R.styleable.NavDrawerItemView_iconTints)) {
            mIconTints = a.getColorStateList(R.styleable.NavDrawerItemView_iconTints)
        }
    }

    fun setContent(@DrawableRes iconResId: Int, @StringRes titleResId: Int) {
        if (iconResId > 0) {
            val icon = DrawableCompat.wrap(ContextCompat.getDrawable(context, iconResId))
            if (mIconTints != null) {
                DrawableCompat.setTintList(icon, mIconTints)
            }
            (findViewById(R.id.icon) as ImageView).setImageDrawable(icon)
        }
        (findViewById(R.id.title) as TextView).setText(titleResId)
    }

}
