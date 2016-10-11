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

package com.google.samples.apps.iosched.sync

import android.content.Context
import android.text.TextUtils
import com.google.gson.Gson
import com.google.samples.apps.iosched.BuildConfig
import com.google.samples.apps.iosched.io.model.DataManifest
import com.google.samples.apps.iosched.util.HashUtils
import com.google.samples.apps.iosched.util.IOUtils
import com.google.samples.apps.iosched.util.LogUtils.*
import com.google.samples.apps.iosched.util.TimeUtils
import com.turbomanage.httpclient.BasicHttpClient
import com.turbomanage.httpclient.ConsoleRequestLogger
import com.turbomanage.httpclient.HttpResponse
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.*

/**
 * Helper class that fetches conference data from the remote server.
 */
class RemoteConferenceDataFetcher(context: Context) {

    private var mContext: Context? = null

    // URL of the remote manifest file
    private var mManifestUrl: String? = null

    // timestamp of the manifest file on the server
    // Returns the timestamp of the data downloaded from the server
    var serverDataTimestamp: String? = null
        private set

    // the set of cache files we have used -- we use this for cache cleanup.
    private val mCacheFilesToKeep = HashSet<String>()

    // total # of bytes downloaded (approximate)
    var totalBytesDownloaded: Long = 0
        private set

    // total # of bytes read from cache hits (approximate)
    var totalBytesReadFromCache: Long = 0
        private set

    init {
        mContext = context
        mManifestUrl = manifestUrl
    }

    /**
     * Fetches data from the remote server.

     * @param refTimestamp The timestamp of the data to use as a reference; if the remote data
     * *                     is not newer than this timestamp, no data will be downloaded and
     * *                     this method will return null.
     * *
     * *
     * @return The data downloaded, or null if there is no data to download
     * *
     * @throws IOException if an error occurred during download.
     */
    @Throws(IOException::class)
    fun fetchConferenceDataIfNewer(refTimestamp: String): Array<String>? {
        if (TextUtils.isEmpty(mManifestUrl)) {
            LOGW(TAG, "Manifest URL is empty (remote sync disabled!).")
            return null
        }

        val httpClient = BasicHttpClient()
        httpClient.setRequestLogger(mQuietLogger)

        // Only download if data is newer than refTimestamp
        // Cloud Storage is very picky with the If-Modified-Since format. If it's in a wrong
        // format, it refuses to serve the file, returning 400 HTTP error. So, if the
        // refTimestamp is in a wrong format, we simply ignore it. But pay attention to this
        // warning in the log, because it might mean unnecessary data is being downloaded.
        if (!TextUtils.isEmpty(refTimestamp)) {
            if (TimeUtils.isValidFormatForIfModifiedSinceHeader(refTimestamp)) {
                httpClient.addHeader("If-Modified-Since", refTimestamp)
            } else {
                LOGW(TAG, "Could not set If-Modified-Since HTTP header. Potentially downloading " +
                        "unnecessary data. Invalid format of refTimestamp argument: " + refTimestamp)
            }
        }

        val response = httpClient.get(mManifestUrl, null)
        if (response == null) {
            LOGE(TAG, "Request for manifest returned null response.")
            throw IOException("Request for data manifest returned null response.")
        }

        val status = response.status
        if (status == HttpURLConnection.HTTP_OK) {
            LOGD(TAG, "Server returned HTTP_OK, so new data is available.")
            serverDataTimestamp = getLastModified(response)
            LOGD(TAG, "Server timestamp for new data is: " + serverDataTimestamp!!)
            val body = response.bodyAsString
            if (TextUtils.isEmpty(body)) {
                LOGE(TAG, "Request for manifest returned empty data.")
                throw IOException("Error fetching conference data manifest: no data.")
            }
            LOGD(TAG, "Manifest $mManifestUrl read, contents: $body")
            totalBytesDownloaded += body.toByteArray().size.toLong()
            return processManifest(body)
        } else if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
            // data on the server is not newer than our data
            LOGD(TAG, "HTTP_NOT_MODIFIED: data has not changed since " + refTimestamp)
            return null
        } else {
            LOGE(TAG, "Error fetching conference data: HTTP status " + status)
            throw IOException("Error fetching conference data: HTTP status " + status)
        }
    }

    /**
     * Returns the remote manifest file's URL. This is stored as a resource in the app,
     * but can be overriden by a file in the filesystem for debug purposes.
     * @return The URL of the remote manifest file.
     */
    private // check for an override file
    val manifestUrl: String
        get() {

            val manifestUrl = BuildConfig.SERVER_MANIFEST_ENDPOINT
            val urlOverrideFile = File(mContext!!.filesDir, URL_OVERRIDE_FILE_NAME)
            if (urlOverrideFile.exists()) {
                try {
                    val overrideUrl = IOUtils.readFileAsString(urlOverrideFile).trim { it <= ' ' }
                    LOGW(TAG, "Debug URL override active: " + overrideUrl)
                    return overrideUrl
                } catch (ex: IOException) {
                    return manifestUrl
                }

            } else {
                return manifestUrl
            }
        }

    /**
     * Fetches a file from the cache/network, from an absolute or relative URL. If the
     * file is available in our cache, we read it from there; if not, we will
     * download it from the network and cache it.

     * @param url The URL to fetch the file from. The URL may be absolute or relative; if
     * *            relative, it will be considered to be relative to the manifest URL.
     * *
     * @return The contents of the file.
     * *
     * @throws IOException If an error occurs.
     */
    @Throws(IOException::class)
    private fun fetchFile(url: String): String? {
        var url = url
        // If this is a relative url, consider it relative to the manifest URL
        if (!url.contains("://")) {
            if (TextUtils.isEmpty(mManifestUrl) || !mManifestUrl!!.contains("/")) {
                LOGE(TAG, "Could not build relative URL based on manifest URL.")
                return null
            }
            val i = mManifestUrl?.lastIndexOf('/')
            url = mManifestUrl?.substring(0, i!!) + "/" + url
        }

        LOGD(TAG, "Attempting to fetch: " + sanitizeUrl(url))

        // Check if we have it in our cache first
        var body: String? = null
        try {
            body = loadFromCache(url)
            if (!TextUtils.isEmpty(body)) {
                // cache hit
                totalBytesReadFromCache += body!!.toByteArray().size.toLong()
                mCacheFilesToKeep.add(getCacheKey(url))
                return body
            }
        } catch (ex: IOException) {
            ex.printStackTrace()
            LOGE(TAG, "IOException getting file from cache.")
            // proceed anyway to attempt to download it from the network
        }

        val client = BasicHttpClient()
        client.setRequestLogger(mQuietLogger)

        // We don't have the file on cache, so download it
        LOGD(TAG, "Cache miss. Downloading from network: " + sanitizeUrl(url))
        val response = client.get(url, null) ?: throw IOException("Request for URL " + sanitizeUrl(url) + " returned null response.")

        LOGD(TAG, "HTTP response " + response.status)
        if (response.status == HttpURLConnection.HTTP_OK) {
            body = response.bodyAsString
            if (TextUtils.isEmpty(body)) {
                throw IOException("Got empty response when attempting to fetch " +
                        sanitizeUrl(url) + url)
            }
            LOGD(TAG, "Successfully downloaded from network: " + sanitizeUrl(url))
            totalBytesDownloaded += body!!.toByteArray().size.toLong()
            writeToCache(url, body)
            mCacheFilesToKeep.add(getCacheKey(url))
            return body
        } else {
            LOGE(TAG, "Failed to fetch from network: " + sanitizeUrl(url))
            throw IOException("Request for URL " + sanitizeUrl(url) +
                    " failed with HTTP error " + response.status)
        }
    }

    /**
     * Returns the cache file where we store our cache of the response of the given URL.
     * @param url The URL for which to return the cache file.
     * *
     * @return The cache file.
     */
    private fun getCacheFile(url: String): File {
        val cacheKey = getCacheKey(url)
        return File(mContext!!.cacheDir.absolutePath + File.separator + CACHE_DIR + File.separator +
                cacheKey)
    }

    // Creates the cache directory, if it doesn't exist yet
    @Throws(IOException::class)
    private fun createCacheDir() {
        val dir = File(mContext!!.cacheDir.absolutePath + File.separator + CACHE_DIR)
        if (!dir.exists() && !dir.mkdir()) {
            throw IOException("Failed to mkdir: " + dir)
        }
    }


    /**
     * Loads our cached content corresponding to the given URL.
     * @param url The URL for which to load the cached response.
     * *
     * @return The cached response corresponding to the URL; or null if the given URL
     * * does not exist in our cache.
     * *
     * @throws IOException If there is an error reading the cache.
     */
    @Throws(IOException::class)
    private fun loadFromCache(url: String): String? {
        val cacheKey = getCacheKey(url)
        val cacheFile = getCacheFile(url)
        if (cacheFile.exists()) {
            LOGD(TAG, "Cache hit " + cacheKey + " for " + sanitizeUrl(url))
            return IOUtils.readFileAsString(cacheFile)
        } else {
            LOGD(TAG, "Cache miss " + cacheKey + " for " + sanitizeUrl(url))
            return null
        }
    }

    /**
     * Writes a file to the cache.
     * @param url The URL from which the contents were retrieved.
     * *
     * @param body The contents retrieved from the given URL.
     * *
     * @throws IOException If there is a problem writing the file.
     */
    @Throws(IOException::class)
    private fun writeToCache(url: String, body: String) {
        val cacheKey = getCacheKey(url)
        val cacheFile = getCacheFile(url)
        createCacheDir()
        IOUtils.writeToFile(body, cacheFile)
        LOGD(TAG, "Wrote to cache " + cacheKey + " --> " + sanitizeUrl(url))
    }

    /**
     * Returns the cache key to be used to store the given URL. The cache key is the
     * file name under which the contents of the URL are stored.
     * @param url The URL.
     * *
     * @return The cache key (guaranteed to be a valid filename)
     */
    private fun getCacheKey(url: String): String {
        return HashUtils.computeWeakHash(url.trim { it <= ' ' }) + String.format("%04x", url.length)
    }

    // Sanitize a URL for logging purposes (only the last component is left visible).
    private fun sanitizeUrl(url: String): String {
        val i = url.lastIndexOf('/')
        if (i >= 0 && i < url.length) {
            return url.substring(0, i).replace("[A-za-z]".toRegex(), "*") + url.substring(i)
        } else
            return url.replace("[A-za-z]".toRegex(), "*")
    }

    /**
     * Process the data manifest and download data files referenced from it.
     * @param manifestJson The JSON of the manifest file.
     * *
     * @return The contents of the set of files referenced from the manifest, or null
     * * if none could be retrieved.
     * *
     * @throws IOException If an error occurs while retrieving information.
     */
    @Throws(IOException::class)
    private fun processManifest(manifestJson: String): Array<String>? {
        LOGD(TAG, "Processing data manifest, length " + manifestJson.length)

        val manifest = Gson().fromJson(manifestJson, DataManifest::class.java)
        if (manifest.format == null || manifest.format != MANIFEST_FORMAT) {
            LOGE(TAG, "Manifest has invalid format spec: " + manifest.format)
            throw IOException("Invalid format spec on manifest:" + manifest.format)
        }

        if (manifest.data_files == null || manifest.data_files.size == 0) {
            LOGW(TAG, "Manifest does not list any files. Nothing done.")
            return null
        }

        LOGD(TAG, "Manifest lists " + manifest.data_files.size + " data files.")
        val jsons = arrayOfNulls<String>(manifest.data_files.size)
        for (i in manifest.data_files.indices) {
            val url = manifest.data_files[i]
            LOGD(TAG, "Processing data file: " + sanitizeUrl(url))
            jsons[i] = fetchFile(url)
            if (TextUtils.isEmpty(jsons[i])) {
                LOGE(TAG, "Failed to fetch data file: " + sanitizeUrl(url))
                throw IOException("Failed to fetch data file " + sanitizeUrl(url))
            }
        }

        LOGD(TAG, "Got " + jsons.size + " data files.")
        cleanUpCache()
        return jsons as Array<String>
    }

    // Delete unnecessary files from our cache
    private fun cleanUpCache() {
        LOGD(TAG, "Starting cache cleanup, " + mCacheFilesToKeep.size + " URLs to keep.")
        val dir = File(mContext!!.cacheDir.absolutePath + File.separator + CACHE_DIR)
        if (!dir.exists()) {
            LOGD(TAG, "Cleanup complete (there is no cache).")
            return
        }

        var deleted = 0
        var kept = 0
        for (file in dir.listFiles()) {
            if (mCacheFilesToKeep.contains(file.getName())) {
                LOGD(TAG, "Cache cleanup: KEEEPING " + file.getName())
                ++kept
            } else {
                LOGD(TAG, "Cache cleanup: DELETING " + file.getName())
                file.delete()
                ++deleted
            }
        }

        LOGD(TAG, "End of cache cleanup. $kept files kept, $deleted deleted.")
    }

    private fun getLastModified(resp: HttpResponse): String {
        if (!resp.headers.containsKey("Last-Modified")) {
            return ""
        }

        val s = resp.headers["Last-Modified"]
        return if (s!!.isEmpty()) "" else s.get(0)
    }

    /**
     * A type of ConsoleRequestLogger that does not log requests and responses.
     */
    private val mQuietLogger = object : ConsoleRequestLogger() {
        @Throws(IOException::class)
        override fun logRequest(uc: HttpURLConnection, content: Any?) {
        }

        override fun logResponse(res: HttpResponse?) {
        }
    }

    companion object {
        private val TAG = makeLogTag(SyncHelper::class.java)

        // The directory under which we cache our downloaded files
        private val CACHE_DIR = "data_cache"

        // name of URL override file used for debug purposes
        private val URL_OVERRIDE_FILE_NAME = "iosched_manifest_url_override.txt"

        private val MANIFEST_FORMAT = "iosched-json-v1"
    }


}
