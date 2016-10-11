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

package com.google.samples.apps.iosched.social

import android.annotation.TargetApi
import android.app.Fragment
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.social.SocialModel.SocialLinksEnum
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout
import com.google.samples.apps.iosched.util.UIUtils

/**
 * Displays links for navigating to social media channels.
 */
class SocialFragment : Fragment() {
    internal var mModel: SocialModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        return inflater.inflate(R.layout.social_frag, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mModel = SocialModel(activity)
        initViewListeners()
    }

    /**
     * Sets up listeners for social media panels.
     */
    private fun initViewListeners() {
        val io15Panel = activity.findViewById(R.id.io15_panel) as ViewGroup
        setUpSocialIcons(io15Panel, SocialLinksEnum.GPLUS_IO15, SocialLinksEnum.TWITTER_IO15)

        val socialGplusDevs = activity.findViewById(R.id.social_gplus_devs) as TextView
        socialGplusDevs.setOnClickListener {
            UIUtils.fireSocialIntent(
                    activity,
                    SocialLinksEnum.GPLUS_DEVS.uri,
                    UIUtils.GOOGLE_PLUS_PACKAGE_NAME)
        }

        val socialTwitterDevs = activity.findViewById(
                R.id.social_twitter_devs) as TextView
        socialTwitterDevs.setOnClickListener {
            UIUtils.fireSocialIntent(
                    activity,
                    SocialLinksEnum.TWITTER_DEVS.uri,
                    UIUtils.TWITTER_PACKAGE_NAME)
        }

        val extendedPanel = activity.findViewById(R.id.extended_panel) as ViewGroup
        setUpSocialIcons(extendedPanel, SocialLinksEnum.GPLUS_EXTENDED,
                SocialLinksEnum.TWITTER_EXTENDED)

        val requestPanel = activity.findViewById(R.id.request_panel) as ViewGroup
        // Make the "Request" panel visible only a few days before I/O.
        if (UIUtils.getCurrentTime(activity) < Config.SHOW_IO15_REQUEST_SOCIAL_PANEL_TIME) {
            requestPanel.visibility = View.GONE
        } else {
            setUpSocialIcons(requestPanel, SocialLinksEnum.GPLUS_REQUEST,
                    SocialLinksEnum.TWITTER_REQUEST)
            requestPanel.visibility = View.VISIBLE
        }
        setupLogoAnim()
    }

    /**
     * Adds listeners to a panel to open the G+ and Twitter apps via an intent.
     */
    private fun setUpSocialIcons(panel: View, gPlusValue: SocialLinksEnum,
                                 twitterValue: SocialLinksEnum) {

        val twitterIconBox = panel.findViewById(R.id.twitter_icon_box)
        twitterIconBox.contentDescription = mModel?.getContentDescription(twitterValue)

        twitterIconBox.setOnClickListener {
            UIUtils.fireSocialIntent(
                    activity,
                    twitterValue.uri,
                    UIUtils.TWITTER_PACKAGE_NAME)
        }

        val gPlusIconBox = panel.findViewById(R.id.gplus_icon_box)
        gPlusIconBox.contentDescription = mModel?.getContentDescription(gPlusValue)

        gPlusIconBox.setOnClickListener {
            UIUtils.fireSocialIntent(
                    activity,
                    gPlusValue.uri,
                    UIUtils.GOOGLE_PLUS_PACKAGE_NAME)
        }
    }

    private fun setContentTopClearance(clearance: Int) {
        if (view != null) {
            view!!.setPadding(view!!.paddingLeft, clearance,
                    view!!.paddingRight, view!!.paddingBottom)
        }
    }


    override fun onResume() {
        super.onResume()
        activity.invalidateOptionsMenu()

        // Configure the fragment's top clearance to take our overlaid controls (Action Bar
        // and spinner box) into account.
        val actionBarSize = UIUtils.calculateActionBarSize(activity)
        val drawShadowFrameLayout = activity.findViewById(R.id.main_content) as DrawShadowFrameLayout
        drawShadowFrameLayout?.setShadowTopOffset(actionBarSize)
        setContentTopClearance(actionBarSize)
    }

    @TargetApi(21)
    private fun setupLogoAnim() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val iv = activity.findViewById(R.id.io_logo) as ImageView
            val logoAnim = activity.getDrawable(
                    R.drawable.io_logo_social_anim) as AnimatedVectorDrawable
            iv.setImageDrawable(logoAnim)
            logoAnim.start()
            iv.setOnClickListener { logoAnim.start() }
        }
    }
}
