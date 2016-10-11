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
package com.google.samples.apps.iosched.gcm

import android.content.Context
import android.text.TextUtils
import com.google.samples.apps.iosched.BuildConfig
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.LogUtils.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.*

/**
 * Helper class used to communicate with the demo server.
 */
object ServerUtilities {
    private val TAG = makeLogTag("GCMs")

    private val PREFERENCES = "com.google.samples.apps.iosched.gcm"
    private val PROPERTY_REGISTERED_TS = "registered_ts"
    private val PROPERTY_REG_ID = "reg_id"
    private val PROPERTY_GCM_KEY = "gcm_key"
    private val MAX_ATTEMPTS = 5
    private val BACKOFF_MILLI_SECONDS = 2000

    private val sRandom = Random()

    private fun checkGcmEnabled(): Boolean {
        if (TextUtils.isEmpty(BuildConfig.GCM_SERVER_URL)) {
            LOGD(TAG, "GCM feature disabled (no URL configured)")
            return false
        } else if (TextUtils.isEmpty(BuildConfig.GCM_API_KEY)) {
            LOGD(TAG, "GCM feature disabled (no API key configured)")
            return false
        } else if (TextUtils.isEmpty(BuildConfig.GCM_SENDER_ID)) {
            LOGD(TAG, "GCM feature disabled (no sender ID configured)")
            return false
        }
        return true
    }

    /**
     * Register this account/device pair within the server.

     * @param context Current context
     * *
     * @param gcmId   The GCM registration ID for this device
     * *
     * @param gcmKey  The GCM key with which to register.
     * *
     * @return whether the registration succeeded or not.
     */
    fun register(context: Context, gcmId: String, gcmKey: String): Boolean {
        if (!checkGcmEnabled()) {
            return false
        }

        LOGI(TAG, "registering device (gcm_id = $gcmId)")
        val serverUrl = BuildConfig.GCM_SERVER_URL + "/register"
        LOGI(TAG, "registering on GCM with GCM key: " + AccountUtils.sanitizeGcmKey(gcmKey))

        val params = HashMap<String, String>()
        params.put("gcm_id", gcmId)
        params.put("gcm_key", gcmKey)
        var backoff = (BACKOFF_MILLI_SECONDS + sRandom.nextInt(1000)).toLong()
        // Once GCM returns a registration id, we need to register it in the
        // demo server. As the server might be down, we will retry it a couple
        // times.
        for (i in 1..MAX_ATTEMPTS) {
            LOGV(TAG, "Attempt #$i to register")
            try {
                post(serverUrl, params, BuildConfig.GCM_API_KEY)
                setRegisteredOnServer(context, true, gcmId, gcmKey)
                return true
            } catch (e: IOException) {
                // Here we are simplifying and retrying on any error; in a real
                // application, it should retry only on unrecoverable errors
                // (like HTTP error code 503).
                LOGE(TAG, "Failed to register on attempt " + i, e)
                if (i == MAX_ATTEMPTS) {
                    break
                }
                try {
                    LOGV(TAG, "Sleeping for $backoff ms before retry")
                    Thread.sleep(backoff)
                } catch (e1: InterruptedException) {
                    // Activity finished before we complete - exit.
                    LOGD(TAG, "Thread interrupted: abort remaining retries!")
                    Thread.currentThread().interrupt()
                    return false
                }

                // increase backoff exponentially
                backoff *= 2
            }

        }
        return false
    }

    /**
     * Unregister this account/device pair within the server.

     * @param context Current context
     * *
     * @param gcmId   The GCM registration ID for this device
     */
    internal fun unregister(context: Context, gcmId: String) {
        if (!checkGcmEnabled()) {
            return
        }

        LOGI(TAG, "unregistering device (gcmId = $gcmId)")
        val serverUrl = BuildConfig.GCM_SERVER_URL + "/unregister"
        val params = HashMap<String, String>()
        params.put("gcm_id", gcmId)
        try {
            post(serverUrl, params, BuildConfig.GCM_API_KEY)
            setRegisteredOnServer(context, false, gcmId, null)
        } catch (e: IOException) {
            // At this point the device is unregistered from GCM, but still
            // registered in the server.
            // We could try to unregister again, but it is not necessary:
            // if the server tries to send a message to the device, it will get
            // a "NotRegistered" error message and should unregister the device.
            LOGD(TAG, "Unable to unregister from application server", e)
        } finally {
            // Regardless of server success, clear local settings_prefs
            setRegisteredOnServer(context, false, null, null)
        }
    }

    /**
     * Request user data sync.

     * @param context Current context
     */
    fun notifyUserDataChanged(context: Context) {
        if (!checkGcmEnabled()) {
            return
        }

        LOGI(TAG, "Notifying GCM that user data changed")
        val serverUrl = BuildConfig.GCM_SERVER_URL + "/send/self/sync_user"
        try {
            val gcmKey = AccountUtils.getGcmKey(context, AccountUtils.getActiveAccountName(context)!!)
            if (gcmKey != null) {
                post(serverUrl, HashMap<String, String>(), gcmKey)
            }
        } catch (e: IOException) {
            LOGE(TAG, "Unable to notify GCM about user data change", e)
        }

    }

    /**
     * Sets whether the device was successfully registered in the server side.

     * @param context Current context
     * *
     * @param flag    True if registration was successful, false otherwise
     * *
     * @param gcmId    True if registration was successful, false otherwise
     */
    private fun setRegisteredOnServer(context: Context, flag: Boolean, gcmId: String?, gcmKey: String?) {
        val prefs = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE)
        LOGD(TAG, "Setting registered on server status as: " + flag + ", gcmKey="
                + AccountUtils.sanitizeGcmKey(gcmKey))
        val editor = prefs.edit()
        if (flag) {
            editor.putLong(PROPERTY_REGISTERED_TS, Date().time)
            editor.putString(PROPERTY_GCM_KEY, gcmKey ?: "")
            editor.putString(PROPERTY_REG_ID, gcmId)
        } else {
            editor.remove(PROPERTY_REG_ID)
        }
        editor.apply()
    }

    /**
     * Checks whether the device was successfully registered in the server side.

     * @param context Current context
     * *
     * @return True if registration was successful, false otherwise
     */
    fun isRegisteredOnServer(context: Context, gcmKey: String?): Boolean {
        var gcmKey = gcmKey
        val prefs = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE)
        // Find registration threshold
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        val yesterdayTS = cal.timeInMillis
        val regTS = prefs.getLong(PROPERTY_REGISTERED_TS, 0)

        gcmKey = if (gcmKey == null) "" else gcmKey

        if (regTS > yesterdayTS) {
            LOGV(TAG, "GCM registration current. regTS=$regTS yesterdayTS=$yesterdayTS")

            val registeredGcmKey = prefs.getString(PROPERTY_GCM_KEY, "")
            if (registeredGcmKey == gcmKey) {
                LOGD(TAG, "GCM registration is valid and for the correct gcm key: " + AccountUtils.sanitizeGcmKey(registeredGcmKey))
                return true
            }
            LOGD(TAG, "GCM registration is for DIFFERENT gcm key "
                    + AccountUtils.sanitizeGcmKey(registeredGcmKey) + ". We were expecting "
                    + AccountUtils.sanitizeGcmKey(gcmKey))
            return false
        } else {
            LOGV(TAG, "GCM registration expired. regTS=$regTS yesterdayTS=$yesterdayTS")
            return false
        }
    }

    fun getGcmId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return prefs.getString(PROPERTY_REG_ID, null)
    }

    /**
     * Unregister the current GCM ID when we sign-out

     * @param context Current context
     */
    fun onSignOut(context: Context) {
        val gcmId = getGcmId(context)
        if (gcmId != null) {
            unregister(context, gcmId)
        }
    }

    /**
     * Issue a POST request to the server.

     * @param endpoint POST address.
     * *
     * @param params   request parameters.
     * *
     * @throws java.io.IOException propagated from POST.
     */
    @Throws(IOException::class)
    private fun post(endpoint: String, params: MutableMap<String, String>, key: String) {
        val url: URL
        try {
            url = URL(endpoint)
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException("invalid url: " + endpoint)
        }

        params.put("key", key)
        val bodyBuilder = StringBuilder()
        val iterator = params.entries.iterator()
        // constructs the POST body using the parameters
        while (iterator.hasNext()) {
            val param = iterator.next()
            bodyBuilder.append(param.key).append('=').append(param.value)
            if (iterator.hasNext()) {
                bodyBuilder.append('&')
            }
        }
        val body = bodyBuilder.toString()
        LOGV(TAG, "Posting '$body' to $url")
        var conn: HttpURLConnection? = null
        try {
            conn = url.openConnection() as HttpURLConnection
            conn.doOutput = true
            conn.useCaches = false
            conn.setChunkedStreamingMode(0)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded;charset=UTF-8")
            conn.setRequestProperty("Content-Length",
                    Integer.toString(body.length))
            // post the request
            val out = conn.outputStream
            out.write(body.toByteArray())
            out.close()
            // handle the response
            val status = conn.responseCode
            if (status != 200) {
                throw IOException("Post failed with error code " + status)
            }
        } finally {
            if (conn != null) {
                conn.disconnect()
            }
        }
    }
}
