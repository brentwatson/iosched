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

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.text.TextUtils
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableNotifiedException
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.Scopes
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.util.LogUtils.*
import java.io.IOException
import java.util.*

/**
 * Account and login utilities. This class manages a local shared preferences object
 * that stores which account is currently active, and can store associated information
 * such as Google+ profile info (name, image URL, cover URL) and also the auth token
 * associated with the account.
 */
object AccountUtils {
    private val TAG = makeLogTag(AccountUtils::class.java)

    private val PREF_ACTIVE_ACCOUNT = "chosen_account"

    // these names are are prefixes; the account is appended to them
    private val PREFIX_PREF_AUTH_TOKEN = "auth_token_"
    private val PREFIX_PREF_PLUS_PROFILE_ID = "plus_profile_id_"
    private val PREFIX_PREF_PLUS_NAME = "plus_name_"
    private val PREFIX_PREF_PLUS_IMAGE_URL = "plus_image_url_"
    private val PREFIX_PREF_PLUS_COVER_URL = "plus_cover_url_"
    private val PREFIX_PREF_GCM_KEY = "gcm_key_"

    val AUTH_SCOPES = arrayOf(Scopes.PLUS_LOGIN, Scopes.DRIVE_APPFOLDER, "https://www.googleapis.com/auth/userinfo.email")

    internal val AUTH_TOKEN_TYPE: String

    init {
        val sb = StringBuilder()
        sb.append("oauth2:")
        for (scope in AUTH_SCOPES) {
            sb.append(scope)
            sb.append(" ")
        }
        AUTH_TOKEN_TYPE = sb.toString()
    }

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    /**
     * Specify whether the app has an active account set.

     * @param context Context used to lookup [SharedPreferences] the value is stored with.
     */
    fun hasActiveAccount(context: Context): Boolean {
        return !TextUtils.isEmpty(getActiveAccountName(context))
    }

    /**
     * Return the accountName the app is using as the active Google Account.

     * @param context Context used to lookup [SharedPreferences] the value is stored with.
     */
    fun getActiveAccountName(context: Context): String? {
        val sp = getSharedPreferences(context)
        return sp.getString(PREF_ACTIVE_ACCOUNT, null)
    }

    /**
     * Return the `Account` the app is using as the active Google Account.

     * @param context Context used to lookup [SharedPreferences] the value is stored with.
     */
    fun getActiveAccount(context: Context): Account? {
        val account = getActiveAccountName(context)
        if (account != null) {
            return Account(account, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE)
        } else {
            return null
        }
    }

    fun setActiveAccount(context: Context, accountName: String?): Boolean {
        LOGD(TAG, "Set active account to: " + accountName)
        val sp = getSharedPreferences(context)
        sp.edit().putString(PREF_ACTIVE_ACCOUNT, accountName).apply()
        return true
    }

    private fun makeAccountSpecificPrefKey(ctx: Context, prefix: String): String? {
        return if (hasActiveAccount(ctx))
            makeAccountSpecificPrefKey(getActiveAccountName(ctx)!!, prefix)
        else
            null
    }

    private fun makeAccountSpecificPrefKey(accountName: String, prefix: String): String {
        return prefix + accountName
    }

    fun getAuthToken(context: Context): String? {
        val sp = getSharedPreferences(context)
        return if (hasActiveAccount(context))
            sp.getString(makeAccountSpecificPrefKey(context, PREFIX_PREF_AUTH_TOKEN), null)
        else
            null
    }

    fun setAuthToken(context: Context, accountName: String, authToken: String) {
        LOGI(TAG, "Auth token of length "
                + (if (TextUtils.isEmpty(authToken)) 0 else authToken.length) + " for "
                + accountName)
        val sp = getSharedPreferences(context)
        sp.edit().putString(makeAccountSpecificPrefKey(accountName, PREFIX_PREF_AUTH_TOKEN),
                authToken).apply()
        LOGV(TAG, "Auth Token: " + authToken)
    }

    fun setAuthToken(context: Context, authToken: String?) {
        if (hasActiveAccount(context)) {
            setAuthToken(context, getActiveAccountName(context)!!, authToken!!)
        } else {
            LOGE(TAG, "Can't set auth token because there is no chosen account!")
        }
    }

    internal fun invalidateAuthToken(context: Context) {
        GoogleAuthUtil.invalidateToken(context, getAuthToken(context))
        setAuthToken(context, null)
    }

    fun setPlusProfileId(context: Context, accountName: String, profileId: String) {
        val sp = getSharedPreferences(context)
        sp.edit().putString(makeAccountSpecificPrefKey(accountName, PREFIX_PREF_PLUS_PROFILE_ID),
                profileId).apply()
    }

    fun getPlusProfileId(context: Context): String? {
        val sp = getSharedPreferences(context)
        return if (hasActiveAccount(context))
            sp.getString(makeAccountSpecificPrefKey(context,
                    PREFIX_PREF_PLUS_PROFILE_ID), null)
        else
            null
    }

    fun hasPlusInfo(context: Context, accountName: String): Boolean {
        val sp = getSharedPreferences(context)
        return !TextUtils.isEmpty(sp.getString(makeAccountSpecificPrefKey(accountName,
                PREFIX_PREF_PLUS_PROFILE_ID), null))
    }

    fun hasToken(context: Context, accountName: String): Boolean {
        val sp = getSharedPreferences(context)
        return !TextUtils.isEmpty(sp.getString(makeAccountSpecificPrefKey(accountName,
                PREFIX_PREF_AUTH_TOKEN), null))
    }

    fun setPlusName(context: Context, accountName: String, name: String) {
        val sp = getSharedPreferences(context)
        sp.edit().putString(makeAccountSpecificPrefKey(accountName, PREFIX_PREF_PLUS_NAME),
                name).apply()
    }

    fun getPlusName(context: Context): String? {
        val sp = getSharedPreferences(context)
        return if (hasActiveAccount(context))
            sp.getString(makeAccountSpecificPrefKey(context,
                    PREFIX_PREF_PLUS_NAME), null)
        else
            null
    }

    fun setPlusImageUrl(context: Context, accountName: String, imageUrl: String) {
        val sp = getSharedPreferences(context)
        sp.edit().putString(makeAccountSpecificPrefKey(accountName, PREFIX_PREF_PLUS_IMAGE_URL),
                imageUrl).apply()
    }

    fun getPlusImageUrl(context: Context): String? {
        val sp = getSharedPreferences(context)
        return if (hasActiveAccount(context))
            sp.getString(makeAccountSpecificPrefKey(context,
                    PREFIX_PREF_PLUS_IMAGE_URL), null)
        else
            null
    }

    fun getPlusImageUrl(context: Context, accountName: String): String? {
        val sp = getSharedPreferences(context)
        return if (hasActiveAccount(context))
            sp.getString(makeAccountSpecificPrefKey(accountName,
                    PREFIX_PREF_PLUS_IMAGE_URL), null)
        else
            null
    }

    fun refreshAuthToken(mContext: Context) {
        invalidateAuthToken(mContext)
        tryAuthenticateWithErrorNotification(mContext, ScheduleContract.CONTENT_AUTHORITY)
    }

    fun setPlusCoverUrl(context: Context, accountName: String, coverPhotoUrl: String) {
        val sp = getSharedPreferences(context)
        sp.edit().putString(makeAccountSpecificPrefKey(accountName, PREFIX_PREF_PLUS_COVER_URL),
                coverPhotoUrl).apply()
    }

    fun getPlusCoverUrl(context: Context): String? {
        val sp = getSharedPreferences(context)
        return if (hasActiveAccount(context))
            sp.getString(makeAccountSpecificPrefKey(context,
                    PREFIX_PREF_PLUS_COVER_URL), null)
        else
            null
    }

    internal fun tryAuthenticateWithErrorNotification(context: Context, syncAuthority: String) {
        try {
            val accountName = getActiveAccountName(context)
            if (accountName != null) {
                LOGI(TAG, "Requesting new auth token (with notification)")
                val token = GoogleAuthUtil.getTokenWithNotification(context, accountName, AUTH_TOKEN_TYPE,
                        null, syncAuthority, null)
                setAuthToken(context, token)
            } else {
                LOGE(TAG, "Can't try authentication because no account is chosen.")
            }

        } catch (e: UserRecoverableNotifiedException) {
            // Notification has already been pushed.
            LOGW(TAG, "User recoverable exception. Check notification.", e)
        } catch (e: GoogleAuthException) {
            // This is likely unrecoverable.
            LOGE(TAG, "Unrecoverable authentication exception: " + e.message, e)
        } catch (e: IOException) {
            LOGE(TAG, "transient error encountered: " + e.message)
        }

    }

    fun setGcmKey(context: Context, accountName: String, gcmKey: String) {
        val sp = getSharedPreferences(context)
        sp.edit().putString(makeAccountSpecificPrefKey(accountName, PREFIX_PREF_GCM_KEY),
                gcmKey).apply()
        LOGD(TAG, "GCM key of account " + accountName + " set to: " + sanitizeGcmKey(gcmKey))
    }

    fun getGcmKey(context: Context, accountName: String): String {
        val sp = getSharedPreferences(context)
        var gcmKey: String = sp.getString(makeAccountSpecificPrefKey(accountName,
                PREFIX_PREF_GCM_KEY), null)

        // if there is no current GCM key, generate a new random one
        if (TextUtils.isEmpty(gcmKey)) {
            gcmKey = UUID.randomUUID().toString()
            LOGD(TAG, "No GCM key on account " + accountName + ". Generating random one: "
                    + sanitizeGcmKey(gcmKey))
            setGcmKey(context, accountName, gcmKey)
        }

        return gcmKey
    }

    fun sanitizeGcmKey(key: String?): String {
        if (key == null) {
            return "(null)"
        } else if (key.length > 8) {
            return key.substring(0, 4) + "........" + key.substring(key.length - 4)
        } else {
            return "........"
        }
    }

    /**
     * Enforce an active Google Account by checking to see if an active account is already set. If
     * it is not set then use the [AccountPicker] to have the user select an account.

     * @param activity The context to be used for starting an activity.
     * *
     * @param activityResultCode The result to be used to start the [AccountPicker].
     * *
     * @return Returns whether the user already has an active account registered.
     */
    fun enforceActiveGoogleAccount(activity: Activity, activityResultCode: Int): Boolean {
        if (hasActiveAccount(activity)) {
            return true
        } else {
            val intent = AccountPicker.newChooseAccountIntent(null, null,
                    arrayOf(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE),
                    true, null, null, null, null)
            activity.startActivityForResult(intent, activityResultCode)
            return false
        }
    }
}
