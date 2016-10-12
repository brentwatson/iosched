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

package com.google.samples.apps.iosched.io

import android.content.ContentProviderOperation
import android.content.Context
import android.database.Cursor
import android.provider.BaseColumns
import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.io.model.Video
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.util.LogUtils.*
import java.util.*

class VideosHandler(context: Context) : JSONHandler(context) {
    private val mVideos = HashMap<String, Video>()

    override fun process(element: JsonElement) {
        for (video in Gson().fromJson(element, Array<Video>::class.java)) {
            if (TextUtils.isEmpty(video.id)) {
                LOGW(TAG, "Video without valid ID. Using VID instead: " + video.vid)
                video.id = video.vid
            }
            mVideos.put(video.id!!, video)
        }
    }

    override fun makeContentProviderOperations(list: ArrayList<ContentProviderOperation>) {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Videos.CONTENT_URI)
        val videoHashcodes = loadVideoHashcodes()
        val videosToKeep = HashSet<String>()
        val isIncrementalUpdate = videoHashcodes != null && videoHashcodes.size > 0

        if (isIncrementalUpdate) {
            LOGD(TAG, "Doing incremental update for videos.")
        } else {
            LOGD(TAG, "Doing FULL (non incremental) update for videos.")
            list.add(ContentProviderOperation.newDelete(uri).build())
        }

        var updatedVideos = 0
        for (video in mVideos.values) {
            val hashCode = video.importHashcode
            videosToKeep.add(video.id!!)

            // add video, if necessary
            if (!isIncrementalUpdate || !videoHashcodes!!.containsKey(video.id!!) ||
                    videoHashcodes[video.id!!] != hashCode) {
                ++updatedVideos
                val isNew = !isIncrementalUpdate || !videoHashcodes!!.containsKey(video.id!!)
                buildVideo(isNew, video, list)
            }
        }

        var deletedVideos = 0
        if (isIncrementalUpdate) {
            for (videoId in videoHashcodes!!.keys) {
                if (!videosToKeep.contains(videoId)) {
                    buildDeleteOperation(videoId, list)
                    ++deletedVideos
                }
            }
        }

        LOGD(TAG, "Videos: " + (if (isIncrementalUpdate) "INCREMENTAL" else "FULL") + " update. " +
                updatedVideos + " to update, " + deletedVideos + " to delete. New total: " +
                mVideos.size)
    }

    private fun buildVideo(isInsert: Boolean, video: Video,
                           list: ArrayList<ContentProviderOperation>) {
        val allVideosUri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Videos.CONTENT_URI)
        val thisVideoUri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Videos.buildVideoUri(video.id))

        val builder: ContentProviderOperation.Builder
        if (isInsert) {
            builder = ContentProviderOperation.newInsert(allVideosUri)
        } else {
            builder = ContentProviderOperation.newUpdate(thisVideoUri)
        }

        if (TextUtils.isEmpty(video.vid)) {
            LOGW(TAG, "Ignoring video with missing video ID.")
            return
        }

        var thumbUrl = video.thumbnailUrl
        if (TextUtils.isEmpty(thumbUrl)) {
            // Oops, missing thumbnail URL. Let's improvise.
            // NOTE: this method of obtaining a thumbnail URL from the video ID
            // is unofficial and might not work in the future; that's why we use
            // it only as a fallback in case we don't get a thumbnail URL in the incoming data.
            thumbUrl = String.format(Locale.US, Config.VIDEO_LIBRARY_FALLBACK_THUMB_URL_FMT, video.vid)
            LOGW(TAG, "Video with missing thumbnail URL: " + video.vid
                    + ". Using fallback: " + thumbUrl)
        }

        list.add(builder.withValue(ScheduleContract.Videos.VIDEO_ID, video.id).withValue(ScheduleContract.Videos.VIDEO_YEAR, video.year).withValue(ScheduleContract.Videos.VIDEO_TITLE, video.title!!.trim { it <= ' ' }).withValue(ScheduleContract.Videos.VIDEO_DESC, video.desc).withValue(ScheduleContract.Videos.VIDEO_VID, video.vid).withValue(ScheduleContract.Videos.VIDEO_TOPIC, video.topic).withValue(ScheduleContract.Videos.VIDEO_SPEAKERS, video.speakers).withValue(ScheduleContract.Videos.VIDEO_THUMBNAIL_URL, thumbUrl).withValue(ScheduleContract.Videos.VIDEO_IMPORT_HASHCODE,
                video.importHashcode).build())
    }

    private fun buildDeleteOperation(videoId: String, list: ArrayList<ContentProviderOperation>) {
        val videoUri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Videos.buildVideoUri(videoId))
        list.add(ContentProviderOperation.newDelete(videoUri).build())
    }

    private fun loadVideoHashcodes(): HashMap<String, String>? {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Videos.CONTENT_URI)
        var cursor: Cursor? = null
        try {
            cursor = JSONHandler.Companion.mContext!!.contentResolver.query(uri, VideoHashcodeQuery.PROJECTION,
                    null, null, null)
            if (cursor == null) {
                LOGE(TAG, "Error querying video hashcodes (got null cursor)")
                return null
            }
            if (cursor.count < 1) {
                LOGE(TAG, "Error querying video hashcodes (no records returned)")
                return null
            }
            val result = HashMap<String, String>()
            if (cursor.moveToFirst()) {
                do {
                    val videoId = cursor.getString(VideoHashcodeQuery.VIDEO_ID)
                    val hashcode = cursor.getString(VideoHashcodeQuery.VIDEO_IMPORT_HASHCODE)
                    result.put(videoId, hashcode ?: "")
                } while (cursor.moveToNext())
            }
            return result
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
    }

    private interface VideoHashcodeQuery {
        companion object {
            val PROJECTION = arrayOf(BaseColumns._ID, ScheduleContract.Videos.VIDEO_ID, ScheduleContract.Videos.VIDEO_IMPORT_HASHCODE)
            val _ID = 0
            val VIDEO_ID = 1
            val VIDEO_IMPORT_HASHCODE = 2
        }
    }

    companion object {
        private val TAG = makeLogTag(VideosHandler::class.java)
    }
}
