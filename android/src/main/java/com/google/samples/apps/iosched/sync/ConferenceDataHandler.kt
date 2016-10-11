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

import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
import android.os.RemoteException
import android.preference.PreferenceManager
import android.text.TextUtils
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.samples.apps.iosched.io.*
import com.google.samples.apps.iosched.io.map.model.Tile
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.util.IOUtils
import com.google.samples.apps.iosched.util.LogUtils.*
import com.google.samples.apps.iosched.util.MapUtils
import com.larvalabs.svgandroid.SVGBuilder
import com.larvalabs.svgandroid.SVGParseException
import com.turbomanage.httpclient.BasicHttpClient
import com.turbomanage.httpclient.ConsoleRequestLogger
import com.turbomanage.httpclient.HttpResponse
import java.io.FileInputStream
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.util.*

/**
 * Helper class that parses conference data and imports them into the app's
 * Content Provider.
 */
class ConferenceDataHandler(ctx: Context) {

    var mContext: Context? = null

    var mRoomsHandler: RoomsHandler? = null
    var mBlocksHandler: BlocksHandler? = null
    var mTagsHandler: TagsHandler? = null
    var mSpeakersHandler: SpeakersHandler? = null
    var mSessionsHandler: SessionsHandler? = null
    var mSearchSuggestHandler: SearchSuggestHandler? = null
    var mMapPropertyHandler: MapPropertyHandler? = null
    var mHashtagsHandler: HashtagsHandler? = null
    var mVideosHandler: VideosHandler? = null

    // Convenience map that maps the key name to its corresponding handler (e.g.
    // "blocks" to mBlocksHandler (to avoid very tedious if-elses)
    var mHandlerForKey = HashMap<String, JSONHandler>()

    // Tally of total content provider operations we carried out (for statistical purposes)
    var contentProviderOperationsDone = 0

    init {
        mContext = ctx
    }

    /**
     * Parses the conference data in the given objects and imports the data into the
     * content provider. The format of the data is documented at https://code.google.com/p/iosched.

     * @param dataBodies The collection of JSON objects to parse and import.
     * *
     * @param dataTimestamp The timestamp of the data. This should be in RFC1123 format.
     * *
     * @param downloadsAllowed Whether or not we are supposed to download data from the internet if needed.
     * *
     * @throws IOException If there is a problem parsing the data.
     */
    @Throws(IOException::class)
    fun applyConferenceData(dataBodies: Array<String>, dataTimestamp: String, downloadsAllowed: Boolean) {
        LOGD(TAG, "Applying data from " + dataBodies.size + " files, timestamp " + dataTimestamp)

        // create handlers for each data type
        mRoomsHandler = RoomsHandler(mContext)
        mBlocksHandler = BlocksHandler(mContext)
        mTagsHandler = TagsHandler(mContext)
        mSpeakersHandler = SpeakersHandler(mContext)
        mSessionsHandler = SessionsHandler(mContext)
        mSearchSuggestHandler = SearchSuggestHandler(mContext)
        mMapPropertyHandler = MapPropertyHandler(mContext)
        mHashtagsHandler = HashtagsHandler(mContext)
        mVideosHandler = VideosHandler(mContext)

        mHandlerForKey.put(DATA_KEY_ROOMS, mRoomsHandler!!)
        mHandlerForKey.put(DATA_KEY_BLOCKS, mBlocksHandler!!)
        mHandlerForKey.put(DATA_KEY_TAGS, mTagsHandler!!)
        mHandlerForKey.put(DATA_KEY_SPEAKERS, mSpeakersHandler!!)
        mHandlerForKey.put(DATA_KEY_SESSIONS, mSessionsHandler!!)
        mHandlerForKey.put(DATA_KEY_SEARCH_SUGGESTIONS, mSearchSuggestHandler!!)
        mHandlerForKey.put(DATA_KEY_MAP, mMapPropertyHandler!!)
        mHandlerForKey.put(DATA_KEY_HASHTAGS, mHashtagsHandler!!)
        mHandlerForKey.put(DATA_KEY_VIDEOS, mVideosHandler!!)

        // process the jsons. This will call each of the handlers when appropriate to deal
        // with the objects we see in the data.
        LOGD(TAG, "Processing " + dataBodies.size + " JSON objects.")
        for (i in dataBodies.indices) {
            LOGD(TAG, "Processing json object #" + (i + 1) + " of " + dataBodies.size)
            processDataBody(dataBodies[i])
        }

        // the sessions handler needs to know the tag and speaker maps to process sessions
        mSessionsHandler!!.setTagMap(mTagsHandler!!.tagMap)
        mSessionsHandler!!.setSpeakerMap(mSpeakersHandler!!.speakerMap)

        // produce the necessary content provider operations
        val batch = ArrayList<ContentProviderOperation>()
        for (key in DATA_KEYS_IN_ORDER) {
            LOGD(TAG, "Building content provider operations for: " + key)
            mHandlerForKey[key]?.makeContentProviderOperations(batch)
            LOGD(TAG, "Content provider operations so far: " + batch.size)
        }
        LOGD(TAG, "Total content provider operations: " + batch.size)

        // download or process local map tile overlay files (SVG files)
        LOGD(TAG, "Processing map overlay files")
        processMapOverlayFiles(mMapPropertyHandler!!.tileOverlays, downloadsAllowed)

        // finally, push the changes into the Content Provider
        LOGD(TAG, "Applying " + batch.size + " content provider operations.")
        try {
            val operations = batch.size
            if (operations > 0) {
                mContext!!.contentResolver.applyBatch(ScheduleContract.CONTENT_AUTHORITY, batch)
            }
            LOGD(TAG, "Successfully applied $operations content provider operations.")
            contentProviderOperationsDone += operations
        } catch (ex: RemoteException) {
            LOGE(TAG, "RemoteException while applying content provider operations.")
            throw RuntimeException("Error executing content provider batch operation", ex)
        } catch (ex: OperationApplicationException) {
            LOGE(TAG, "OperationApplicationException while applying content provider operations.")
            throw RuntimeException("Error executing content provider batch operation", ex)
        }

        // notify all top-level paths
        LOGD(TAG, "Notifying changes on all top-level paths on Content Resolver.")
        val resolver = mContext!!.contentResolver
        for (path in ScheduleContract.TOP_LEVEL_PATHS) {
            val uri = ScheduleContract.BASE_CONTENT_URI.buildUpon().appendPath(path).build()
            resolver.notifyChange(uri, null)
        }


        // update our data timestamp
        //dataTimestamp = dataTimestamp
        LOGD(TAG, "Done applying conference data.")
    }

    /**
     * Processes a conference data body and calls the appropriate data type handlers
     * to process each of the objects represented therein.

     * @param dataBody The body of data to process
     * *
     * @throws IOException If there is an error parsing the data.
     */
    @Throws(IOException::class)
    private fun processDataBody(dataBody: String) {
        val reader = JsonReader(StringReader(dataBody))
        val parser = JsonParser()
        try {
            reader.isLenient = true // To err is human

            // the whole file is a single JSON object
            reader.beginObject()

            while (reader.hasNext()) {
                // the key is "rooms", "speakers", "tracks", etc.
                val key = reader.nextName()
                if (mHandlerForKey.containsKey(key)) {
                    // pass the value to the corresponding handler
                    mHandlerForKey[key]?.process(parser.parse(reader))
                } else {
                    LOGW(TAG, "Skipping unknown key in conference data json: " + key)
                    reader.skipValue()
                }
            }
            reader.endObject()
        } finally {
            reader.close()
        }
    }

    /**
     * Synchronise the map overlay files either from the local assets (if available) or from a remote url.

     * @param collection Set of tiles containing a local filename and remote url.
     * *
     * @throws IOException
     */
    @Throws(IOException::class, SVGParseException::class)
    private fun processMapOverlayFiles(collection: Collection<Tile>, downloadAllowed: Boolean) {
        // clear the tile cache on disk if any tiles have been updated
        var shouldClearCache = false
        // keep track of used files, unused files are removed
        val usedTiles = ArrayList<String>()
        for (tile in collection) {
            val filename = tile.filename
            val url = tile.url

            usedTiles.add(filename)

            if (!MapUtils.hasTile(mContext!!, filename)) {
                shouldClearCache = true
                // copy or download the tile if it is not stored yet
                if (MapUtils.hasTileAsset(mContext!!, filename)) {
                    // file already exists as an asset, copy it
                    MapUtils.copyTileAsset(mContext!!, filename)
                } else if (downloadAllowed && !TextUtils.isEmpty(url)) {
                    try {
                        // download the file only if downloads are allowed and url is not empty
                        val tileFile = MapUtils.getTileFile(mContext!!, filename)
                        val httpClient = BasicHttpClient()
                        httpClient.setRequestLogger(mQuietLogger)
                        val httpResponse = httpClient.get(url, null)
                        IOUtils.writeToFile(httpResponse.body, tileFile)

                        // ensure the file is valid SVG
                        val `is` = FileInputStream(tileFile)
                        val svg = SVGBuilder().readFromInputStream(`is`).build()
                        `is`.close()
                    } catch (ex: IOException) {
                        LOGE(TAG, "FAILED downloading map overlay tile " + url +
                                ": " + ex.message, ex)
                    } catch (ex: SVGParseException) {
                        LOGE(TAG, "FAILED parsing map overlay tile " + url +
                                ": " + ex.message, ex)
                    }

                } else {
                    LOGD(TAG, "Skipping download of map overlay tile" + " (since downloadsAllowed=false)")
                }
            }
        }

        if (shouldClearCache) {
            MapUtils.clearDiskCache(mContext!!)
        }

        MapUtils.removeUnusedTiles(mContext!!, usedTiles)
    }

    // Returns the timestamp of the data we have in the content provider.
    // Sets the timestamp of the data we have in the content provider.
    var dataTimestamp: String
        get() = PreferenceManager.getDefaultSharedPreferences(mContext).getString(
                SP_KEY_DATA_TIMESTAMP, DEFAULT_TIMESTAMP)
        set(timestamp) {
            LOGD(TAG, "Setting data timestamp to: " + timestamp)
            PreferenceManager.getDefaultSharedPreferences(mContext).edit().putString(
                    SP_KEY_DATA_TIMESTAMP, timestamp).commit()
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

        // Shared settings_prefs key under which we store the timestamp that corresponds to
        // the data we currently have in our content provider.
        private val SP_KEY_DATA_TIMESTAMP = "data_timestamp"

        // symbolic timestamp to use when we are missing timestamp data (which means our data is
        // really old or nonexistent)
        private val DEFAULT_TIMESTAMP = "Sat, 1 Jan 2000 00:00:00 GMT"

        private val DATA_KEY_ROOMS = "rooms"
        private val DATA_KEY_BLOCKS = "blocks"
        private val DATA_KEY_TAGS = "tags"
        private val DATA_KEY_SPEAKERS = "speakers"
        private val DATA_KEY_SESSIONS = "sessions"
        private val DATA_KEY_SEARCH_SUGGESTIONS = "search_suggestions"
        private val DATA_KEY_MAP = "map"
        private val DATA_KEY_HASHTAGS = "hashtags"
        private val DATA_KEY_VIDEOS = "video_library"

        private val DATA_KEYS_IN_ORDER = arrayOf(DATA_KEY_ROOMS, DATA_KEY_BLOCKS, DATA_KEY_TAGS, DATA_KEY_SPEAKERS, DATA_KEY_SESSIONS, DATA_KEY_SEARCH_SUGGESTIONS, DATA_KEY_MAP, DATA_KEY_HASHTAGS, DATA_KEY_VIDEOS)

        // Reset the timestamp of the data we have in the content provider
        fun resetDataTimestamp(context: Context) {
            LOGD(TAG, "Resetting data timestamp to default (to invalidate our synced data)")
            PreferenceManager.getDefaultSharedPreferences(context).edit().remove(
                    SP_KEY_DATA_TIMESTAMP).commit()
        }
    }

}
