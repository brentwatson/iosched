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

package com.google.samples.apps.iosched.about


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.TextView

import com.google.samples.apps.iosched.BuildConfig
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout
import com.google.samples.apps.iosched.util.AboutUtils
import com.google.samples.apps.iosched.util.UIUtils

class AboutActivity : BaseActivity() {

    private var rootView: View? = null

    private val mOnClickListener = View.OnClickListener { v ->
        when (v.id) {
            R.id.about_terms -> openUrl(URL_TERMS)
            R.id.about_privacy_policy -> openUrl(URL_PRIVACY_POLICY)
            R.id.about_licenses -> AboutUtils.showOpenSourceLicenses(this@AboutActivity)
            R.id.about_eula -> AboutUtils.showEula(this@AboutActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        rootView = findViewById(R.id.about_container)

        val body = rootView!!.findViewById(R.id.about_main) as TextView
        body.text = Html.fromHtml(getString(R.string.about_main, BuildConfig.VERSION_NAME))
        rootView!!.findViewById(R.id.about_terms).setOnClickListener(mOnClickListener)
        rootView!!.findViewById(R.id.about_privacy_policy).setOnClickListener(mOnClickListener)
        rootView!!.findViewById(R.id.about_licenses).setOnClickListener(mOnClickListener)
        rootView!!.findViewById(R.id.about_eula).setOnClickListener(mOnClickListener)

        overridePendingTransition(0, 0)
    }


    override fun getSelfNavDrawerItem(): Int {
        return BaseActivity.NAVDRAWER_ITEM_ABOUT
    }

    private fun setContentTopClearance(clearance: Int) {
        if (rootView != null) {
            rootView!!.setPadding(rootView!!.paddingLeft, clearance,
                    rootView!!.paddingRight, rootView!!.paddingBottom)
        }
    }

    override fun onResume() {
        super.onResume()
        val actionBarSize = UIUtils.calculateActionBarSize(this)
        val drawShadowFrameLayout = findViewById(R.id.main_content) as DrawShadowFrameLayout
        drawShadowFrameLayout?.setShadowTopOffset(actionBarSize)
        setContentTopClearance(actionBarSize)
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    companion object {

        private val URL_TERMS = "http://m.google.com/utos"
        private val URL_PRIVACY_POLICY = "http://www.google.com/policies/privacy/"
    }

}
