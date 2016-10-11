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
package com.google.samples.apps.iosched.debug.actions

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.IntentSender
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.*
import com.google.android.gms.drive.Drive
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.sync.userdata.gms.DriveHelper
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.io.IOException

/**
 * Simple debug activity that lists all files currently in Google Drive AppFolder only
 * with their content.
 */
class ViewFilesInAppFolderActivity : BaseActivity(), ConnectionCallbacks, OnConnectionFailedListener {

    private var mLogArea: TextView? = null
    private var mProgressDialog: ProgressDialog? = null

    private var mGoogleApiClient: GoogleApiClient? = null
    private var mFetchDataTask: FetchDataTask? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_action_showdrivefiles)
        mLogArea = findViewById(R.id.logArea) as TextView

        mProgressDialog = ProgressDialog(this)
        mGoogleApiClient = Builder(this).addApi(Drive.API).addScope(Drive.SCOPE_APPFOLDER).addConnectionCallbacks(this).addOnConnectionFailedListener(this).setAccountName(AccountUtils.getActiveAccountName(this)).build()
    }

    public override fun onStart() {
        super.onStart()
        mGoogleApiClient!!.connect()
    }

    override fun onStop() {
        super.onStop()
        if (mFetchDataTask != null) {
            mFetchDataTask!!.cancel(true)
        }
        mGoogleApiClient!!.disconnect()
    }

    override fun onConnected(bundle: Bundle) {
        if (mFetchDataTask != null) {
            mFetchDataTask!!.cancel(true)
        }
        mFetchDataTask = FetchDataTask()
        mFetchDataTask!!.execute()
    }

    override fun onConnectionSuspended(status: Int) {

    }

    override fun onConnectionFailed(result: ConnectionResult) {
        if (!result.hasResolution()) {
            GooglePlayServicesUtil.getErrorDialog(result.errorCode, this, 0).show()
            return
        }
        try {
            result.startResolutionForResult(this, RESOLVE_CONNECTION_REQUEST_CODE)
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "Exception while starting resolution activity", e)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
            RESOLVE_CONNECTION_REQUEST_CODE -> if (resultCode == Activity.RESULT_OK) {
                mGoogleApiClient!!.connect()
            }
        }
    }

    private inner class FetchDataTask : AsyncTask<Void, Void, String>() {

        override fun onPreExecute() {
            if (!mProgressDialog!!.isShowing) {
                mProgressDialog!!.show()
            }
        }

        override fun doInBackground(vararg params: Void): String {
            val helper = DriveHelper(mGoogleApiClient!!)
            val result = StringBuilder()
            val buffer = Drive.DriveApi.getAppFolder(mGoogleApiClient).listChildren(mGoogleApiClient).await().metadataBuffer
            try {
                result.append("found ").append(buffer.count).append(" file(s):").append(EOL).append("----------").append(EOL)

                for (m in buffer) {
                    val id = m.driveId
                    result.append("Name: ").append(m.title).append(EOL)
                    result.append("MimeType: ").append(m.mimeType).append(EOL)
                    result.append(id.encodeToString()).append(EOL)
                    result.append("LastModified: ").append(m.modifiedDate.time).append(EOL)
                    val content = helper.getContentsFromDrive(id)
                    result.append("--------").append(EOL).append(content).append(EOL)
                    result.append("--------")
                }
            } catch (io: IOException) {
                result.append("Exception fetching content").append(EOL)
            } finally {
                buffer.close()
            }
            return result.toString()
        }

        override fun onPostExecute(content: String) {
            if (mProgressDialog!!.isShowing) {
                mProgressDialog!!.dismiss()
            }
            mLogArea!!.text = content
        }
    }

    companion object {

        private val TAG = makeLogTag(ViewFilesInAppFolderActivity::class.java)
        private val RESOLVE_CONNECTION_REQUEST_CODE = 1001
        private val EOL = "\n"
    }
}
