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
package com.google.samples.apps.iosched.sync.userdata.gms

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Status
import com.google.android.gms.drive.*
import com.google.android.gms.drive.query.*
import com.google.samples.apps.iosched.util.IOUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.io.IOException
import java.util.*

/**
 * A helper class for creating, fetching and editing Drive AppData files
 */
class DriveHelper
/**
 * Construct the helper with a [GoogleApiClient] that is connected.

 * @param apiClient The [GoogleApiClient] that is either connected or unconnected.
 */
(private val mGoogleApiClient: GoogleApiClient) {

    /**
     * Connect the [GoogleApiClient] if not already connected.
     * Note that this assumes you're already running in a background thread
     * and issues a `GoogleApiClient#blockingConnect()` call to connect.

     * @return ConnectionResult or null if already connected.
     */
    fun connectIfNecessary(): ConnectionResult? {
        if (!mGoogleApiClient.isConnected) {
            return mGoogleApiClient.blockingConnect()
        } else {
            return null
        }
    }

    /**
     * This is essential to ensure that the Google Play services cache is up-to-date.
     * Call [com.google.android.gms.drive.DriveApi.requestSync]

     * @return [com.google.android.gms.common.api.Status]
     */
    fun requestSync(): Status {
        return Drive.DriveApi.requestSync(mGoogleApiClient).await()
    }

    /**
     * Get or create the [DriveFile] named with `fileName` with
     * the specific `mimeType`.

     * @return Return the `DriveId` of the fetched or created file.
     */
    fun getOrCreateFile(fileName: String, mimeType: String): DriveId {
        LOGD(TAG, "getOrCreateFile $fileName mimeType $mimeType")
        val file = getDriveFile(fileName, mimeType)
        LOGD(TAG, "getDriveFile  returned " + file!!)
        if (file == null) {
            return createEmptyDriveFile(fileName, mimeType)
        } else {
            return file
        }
    }

    /**
     * Save the `DriveFile` with the specific driveId.

     * @param id [DriveId] of the file.
     * *
     * @param content The content to be saved in the `DriveFile`.
     * *
     * @return Return value indicates whether the save was successful.
     */
    @Throws(IOException::class)
    fun saveDriveFile(id: DriveId, content: String): Boolean {
        val theFile = Drive.DriveApi.getFile(mGoogleApiClient, id)
        val result = theFile.open(mGoogleApiClient, DriveFile.MODE_WRITE_ONLY, null).await()

        try {
            IOUtils.writeToStream(content, result.driveContents.outputStream)
            // Update the last viewed.
            val changeSet = MetadataChangeSet.Builder().setLastViewedByMeDate(Date()).build()
            return result.driveContents.commit(mGoogleApiClient, changeSet).await().isSuccess
        } catch (io: IOException) {
            result.driveContents.discard(mGoogleApiClient)
            throw io
        }

    }

    @Throws(IOException::class)
    fun getContentsFromDrive(id: DriveId): String? {
        val theFile = Drive.DriveApi.getFile(mGoogleApiClient, id)
        val result = theFile.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, null).await()
        val driveContents = result.driveContents
        try {
            if (driveContents != null) {
                return IOUtils.readAsString(driveContents.inputStream)
            }
        } finally {
            driveContents?.discard(mGoogleApiClient)
        }
        return null
    }

    /**
     * Create an empty file with the given `fileName` and `mimeType`.

     * @return [DriveId] of the specific file.
     */
    private fun createEmptyDriveFile(fileName: String, mimeType: String): DriveId {
        val result = Drive.DriveApi.newDriveContents(mGoogleApiClient).await()

        val changeSet = MetadataChangeSet.Builder().setTitle(fileName).setMimeType(mimeType).setStarred(true).build()

        // Create a new file with the given changeSet in the AppData folder.
        val driveFileResult = Drive.DriveApi.getAppFolder(mGoogleApiClient).createFile(mGoogleApiClient, changeSet, result.driveContents).await()
        return driveFileResult.driveFile.driveId
    }

    /**
     * Search for a file with the specific name and mimeType
     * @return driveId for the file it if exists.
     */
    private fun getDriveFile(fileName: String, mimeType: String): DriveId? {
        // Find the named file with the specific Mime type.
        val query = Query.Builder().addFilter(Filters.and(
                Filters.eq(SearchableField.TITLE, fileName),
                Filters.eq(SearchableField.MIME_TYPE, mimeType))).setSortOrder(SortOrder.Builder().addSortDescending(SortableField.MODIFIED_DATE).build()).build()

        var buffer: MetadataBuffer? = null
        try {
            buffer = Drive.DriveApi.getAppFolder(mGoogleApiClient).queryChildren(mGoogleApiClient, query).await().metadataBuffer

            if (buffer != null && buffer.count > 0) {
                LOGD(TAG, "got buffer " + buffer.count)
                return buffer.get(0).driveId
            }
            return null
        } finally {
            if (buffer != null) {
                buffer.close()
            }
        }
    }

    companion object {

        private val TAG = makeLogTag(DriveHelper::class.java)
    }
}
