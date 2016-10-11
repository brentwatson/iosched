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

package com.google.samples.apps.iosched.social


import android.content.Context
import android.net.Uri

import com.google.samples.apps.iosched.R

/**
 * The data source for [com.google.samples.apps.iosched.social.SocialFragment]. The data
 * is static and not fetched through a query.
 */
class SocialModel(private val mContext: Context) {

    /**
     * Hard-coded labels and links to select social media targets.
     */
    enum class SocialLinksEnum private constructor(val tag: String, val target: String) {
        GPLUS_IO15("#io15", PLUS_SEARCH_TARGET),
        TWITTER_IO15("io15", TWITTER_HASHTAG_TARGET),
        GPLUS_DEVS("+googledevelopers", PLUS_DIRECT_TARGET),
        TWITTER_DEVS("googledevs", TWITTER_TARGET),
        GPLUS_EXTENDED("#io15extended", PLUS_SEARCH_TARGET),
        TWITTER_EXTENDED("io15extended", TWITTER_HASHTAG_TARGET),
        GPLUS_REQUEST("#io15request", PLUS_SEARCH_TARGET),
        TWITTER_REQUEST("io15request", TWITTER_HASHTAG_TARGET);

        val uri: Uri
            get() = Uri.parse(target + Uri.encode(tag))
    }

    /**
     * Returns the content description for a social link.
     */
    fun getContentDescription(socialValue: SocialLinksEnum): String {
        when (socialValue) {
            SocialModel.SocialLinksEnum.GPLUS_IO15 -> return mContext.resources.getString(
                    R.string.social_io15_gplus_content_description)
            SocialModel.SocialLinksEnum.TWITTER_IO15 -> return mContext.resources.getString(
                    R.string.social_io15_twitter_content_description)
            SocialModel.SocialLinksEnum.GPLUS_EXTENDED -> return mContext.resources.getString(
                    R.string.social_extended_gplus_content_description)
            SocialModel.SocialLinksEnum.TWITTER_EXTENDED -> return mContext.resources.getString(
                    R.string.social_extended_twitter_content_description)
            SocialModel.SocialLinksEnum.GPLUS_REQUEST -> return mContext.resources.getString(
                    R.string.social_request_gplus_content_description)
            SocialModel.SocialLinksEnum.TWITTER_REQUEST -> return mContext.resources.getString(
                    R.string.social_request_twitter_content_description)
            else -> return ""
        }
    }

    companion object {

        val PLUS_DIRECT_TARGET = "https://plus.google.com/"
        val PLUS_SEARCH_TARGET = "https://plus.google.com/s/"
        val TWITTER_HASHTAG_TARGET = "https://twitter.com/hashtag/"
        val TWITTER_TARGET = "https://twitter.com/"
    }

}
