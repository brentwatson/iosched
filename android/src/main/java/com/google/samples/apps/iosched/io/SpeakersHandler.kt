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
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.samples.apps.iosched.io.model.Speaker
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.util.LogUtils.*
import java.util.*

class SpeakersHandler(context: Context) : JSONHandler(context) {
    val speakerMap = HashMap<String, Speaker>()

    override fun process(element: JsonElement) {
        for (speaker in Gson().fromJson(element, Array<Speaker>::class.java)) {
            speakerMap.put(speaker.id, speaker)
        }
    }

    override fun makeContentProviderOperations(list: ArrayList<ContentProviderOperation>) {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Speakers.CONTENT_URI)
        val speakerHashcodes = loadSpeakerHashcodes()
        val speakersToKeep = HashSet<String>()
        val isIncrementalUpdate = speakerHashcodes != null && speakerHashcodes.size > 0

        if (isIncrementalUpdate) {
            LOGD(TAG, "Doing incremental update for speakers.")
        } else {
            LOGD(TAG, "Doing FULL (non incremental) update for speakers.")
            list.add(ContentProviderOperation.newDelete(uri).build())
        }

        var updatedSpeakers = 0
        for (speaker in speakerMap.values) {
            val hashCode = speaker.importHashcode
            speakersToKeep.add(speaker.id)

            // add speaker, if necessary
            if (!isIncrementalUpdate || !speakerHashcodes!!.containsKey(speaker.id) ||
                    speakerHashcodes[speaker.id] != hashCode) {
                ++updatedSpeakers
                val isNew = !isIncrementalUpdate || !speakerHashcodes!!.containsKey(speaker.id)
                buildSpeaker(isNew, speaker, list)
            }
        }

        var deletedSpeakers = 0
        if (isIncrementalUpdate) {
            for (speakerId in speakerHashcodes!!.keys) {
                if (!speakersToKeep.contains(speakerId)) {
                    buildDeleteOperation(speakerId, list)
                    ++deletedSpeakers
                }
            }
        }

        LOGD(TAG, "Speakers: " + (if (isIncrementalUpdate) "INCREMENTAL" else "FULL") + " update. " +
                updatedSpeakers + " to update, " + deletedSpeakers + " to delete. New total: " +
                speakerMap.size)
    }

    private fun buildSpeaker(isInsert: Boolean, speaker: Speaker,
                             list: ArrayList<ContentProviderOperation>) {
        val allSpeakersUri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Speakers.CONTENT_URI)
        val thisSpeakerUri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Speakers.buildSpeakerUri(speaker.id))

        val builder: ContentProviderOperation.Builder
        if (isInsert) {
            builder = ContentProviderOperation.newInsert(allSpeakersUri)
        } else {
            builder = ContentProviderOperation.newUpdate(thisSpeakerUri)
        }

        list.add(builder.withValue(ScheduleContract.SyncColumns.UPDATED, System.currentTimeMillis()).withValue(ScheduleContract.Speakers.SPEAKER_ID, speaker.id).withValue(ScheduleContract.Speakers.SPEAKER_NAME, speaker.name).withValue(ScheduleContract.Speakers.SPEAKER_ABSTRACT, speaker.bio).withValue(ScheduleContract.Speakers.SPEAKER_COMPANY, speaker.company).withValue(ScheduleContract.Speakers.SPEAKER_IMAGE_URL, speaker.thumbnailUrl).withValue(ScheduleContract.Speakers.SPEAKER_PLUSONE_URL, speaker.plusoneUrl).withValue(ScheduleContract.Speakers.SPEAKER_TWITTER_URL, speaker.twitterUrl).withValue(ScheduleContract.Speakers.SPEAKER_IMPORT_HASHCODE,
                speaker.importHashcode).build())
    }

    private fun buildDeleteOperation(speakerId: String, list: ArrayList<ContentProviderOperation>) {
        val speakerUri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Speakers.buildSpeakerUri(speakerId))
        list.add(ContentProviderOperation.newDelete(speakerUri).build())
    }

    private fun loadSpeakerHashcodes(): HashMap<String, String>? {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Speakers.CONTENT_URI)
        var cursor: Cursor? = null
        try {
            cursor = JSONHandler.Companion.mContext!!.contentResolver.query(uri, SpeakerHashcodeQuery.PROJECTION,
                    null, null, null)
            if (cursor == null) {
                LOGE(TAG, "Error querying speaker hashcodes (got null cursor)")
                return null
            }
            if (cursor.count < 1) {
                LOGE(TAG, "Error querying speaker hashcodes (no records returned)")
                return null
            }
            val result = HashMap<String, String>()
            if (cursor.moveToFirst()) {
                do {
                    val speakerId = cursor.getString(SpeakerHashcodeQuery.SPEAKER_ID)
                    val hashcode = cursor.getString(SpeakerHashcodeQuery.SPEAKER_IMPORT_HASHCODE)
                    result.put(speakerId, hashcode ?: "")
                } while (cursor.moveToNext())
            }
            return result
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
    }

    private interface SpeakerHashcodeQuery {
        companion object {
            val PROJECTION = arrayOf(BaseColumns._ID, ScheduleContract.Speakers.SPEAKER_ID, ScheduleContract.Speakers.SPEAKER_IMPORT_HASHCODE)
            val _ID = 0
            val SPEAKER_ID = 1
            val SPEAKER_IMPORT_HASHCODE = 2
        }
    }

    companion object {
        private val TAG = makeLogTag(SpeakersHandler::class.java)
    }
}
