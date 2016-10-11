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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import com.google.samples.apps.iosched.provider.ScheduleContract

/**
 * Android Beam helper methods.
 */
object BeamUtils {

    /**
     * Sets this activity's Android Beam message to one representing the given session.
     */
    @JvmStatic
    fun setBeamSessionUri(activity: Activity, sessionUri: Uri) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity) ?: // No NFC :-(
                return

        nfcAdapter.setNdefPushMessage(NdefMessage(
                arrayOf(NdefRecord(NdefRecord.TNF_MIME_MEDIA,
                        ScheduleContract.makeContentItemType(
                                ScheduleContract.Sessions.CONTENT_TYPE_ID).toByteArray(),
                        ByteArray(0),
                        sessionUri.toString().toByteArray()))), activity)
    }

    /**
     * Checks to see if the activity's intent ([android.app.Activity.getIntent]) is
     * an NFC intent that the app recognizes. If it is, then parse the NFC message and set the
     * activity's intent (using [Activity.setIntent]) to something
     * the app can recognize (i.e. a normal [Intent.ACTION_VIEW] intent).
     */
    @JvmStatic
    fun tryUpdateIntentFromBeam(activity: Activity) {
        val originalIntent = activity.intent
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == originalIntent.action) {
            val rawMsgs = originalIntent.getParcelableArrayExtra(
                    NfcAdapter.EXTRA_NDEF_MESSAGES)
            val msg = rawMsgs[0] as NdefMessage
            // Record 0 contains the MIME type, record 1 is the AAR, if present.
            // In iosched, AARs are not present.
            val mimeRecord = msg.records[0]
            if (ScheduleContract.makeContentItemType(
                    ScheduleContract.Sessions.CONTENT_TYPE_ID) == String(mimeRecord.type)) {
                // Re-set the activity's intent to one that represents session details.
                val sessionDetailIntent = Intent(Intent.ACTION_VIEW,
                        Uri.parse(String(mimeRecord.payload)))
                activity.intent = sessionDetailIntent
            }
        }
    }
}
