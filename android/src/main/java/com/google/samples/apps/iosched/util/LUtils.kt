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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.AnimatedStateListDrawable
import android.os.Build
import android.os.Handler
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.samples.apps.iosched.R

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class LUtils private constructor(protected var mActivity: AppCompatActivity) {
    private val mHandler = Handler()

    fun startActivityWithTransition(intent: Intent, clickedView: View?,
                                    transitionName: String) {
        val options: ActivityOptions? = null
        if (hasL() && clickedView != null && !TextUtils.isEmpty(transitionName)) {
            //            options = ActivityOptions.makeSceneTransitionAnimation(
            //                    mActivity, clickedView, transitionName);
        }

        mActivity.startActivity(intent, options?.toBundle())
    }

    fun setMediumTypeface(textView: TextView) {
        if (hasL()) {
            if (sMediumTypeface == null) {
                sMediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }

            textView.typeface = sMediumTypeface
        } else {
            textView.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    // On pre-L devices, you can have any status bar color so long as it's black.
    var statusBarColor: Int
        get() {
            if (!hasL()) {
                return Color.BLACK
            }

            return mActivity.window.statusBarColor
        }
        set(color) {
            if (!hasL()) {
                return
            }

            mActivity.window.statusBarColor = color
        }

    fun setOrAnimatePlusCheckIcon(fab: FloatingActionButton, isCheck: Boolean,
                                  allowAnimate: Boolean) {
        if (!hasL()) {
            compatSetOrAnimatePlusCheckIcon(fab, isCheck, allowAnimate)
            return
        }

        var drawable = fab.drawable
        if (drawable !is AnimatedStateListDrawable) {
            val res = mActivity.resources
            drawable = res.getDrawable(R.drawable.add_schedule_fab_icon_anim)
            drawable.setTint(res.getColor(R.color.fab_icon_color))
            fab.setImageDrawable(drawable)
        }

        if (allowAnimate) {
            drawable.state = if (isCheck) STATE_UNCHECKED else STATE_CHECKED
            drawable.jumpToCurrentState()
            drawable.state = if (isCheck) STATE_CHECKED else STATE_UNCHECKED
        } else {
            drawable.state = if (isCheck) STATE_CHECKED else STATE_UNCHECKED
            drawable.jumpToCurrentState()
        }
    }

    fun compatSetOrAnimatePlusCheckIcon(imageView: ImageView, isCheck: Boolean,
                                        allowAnimate: Boolean) {
        val imageResId = if (isCheck)
            R.drawable.add_schedule_button_icon_checked
        else
            R.drawable.add_schedule_button_icon_unchecked

        if (imageView.tag != null) {
            if (imageView.tag is Animator) {
                val anim = imageView.tag as Animator
                anim.end()
                imageView.alpha = 1f
            }
        }

        if (allowAnimate && isCheck) {
            val duration = mActivity.resources.getInteger(
                    android.R.integer.config_shortAnimTime)
            val outAnimator = ObjectAnimator.ofFloat(imageView, View.ALPHA, 0f)
            outAnimator.duration = (duration / 2).toLong()
            outAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    imageView.setImageResource(imageResId)
                }
            })

            val inAnimator = AnimatorSet()
            outAnimator.duration = duration.toLong()
            inAnimator.playTogether(
                    ObjectAnimator.ofFloat(imageView, View.ALPHA, 1f),
                    ObjectAnimator.ofFloat(imageView, View.SCALE_X, 0f, 1f),
                    ObjectAnimator.ofFloat(imageView, View.SCALE_Y, 0f, 1f))

            val set = AnimatorSet()
            set.playSequentially(outAnimator, inAnimator)
            set.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    imageView.tag = null
                }
            })
            imageView.tag = set
            set.start()
        } else {
            mHandler.post { imageView.setImageResource(imageResId) }
        }
    }

    companion object {
        private val STATE_CHECKED = intArrayOf(android.R.attr.state_checked)
        private val STATE_UNCHECKED = intArrayOf()

        private var sMediumTypeface: Typeface? = null

        fun getInstance(activity: AppCompatActivity): LUtils {
            return LUtils(activity)
        }

        private fun hasL(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        }
    }
}
