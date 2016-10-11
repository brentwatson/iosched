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
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import com.google.samples.apps.iosched.BuildConfig
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGW
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

object WiFiUtils {
    // Preference key and values associated with WiFi AP configuration.
    val PREF_WIFI_AP_CONFIG = "pref_wifi_ap_config"
    val WIFI_CONFIG_DONE = "done"
    val WIFI_CONFIG_REQUESTED = "requested"

    private val TAG = makeLogTag(WiFiUtils::class.java)

    fun installConferenceWiFi(context: Context) {
        // Create conferenceWifiConfig
        val conferenceWifiConfig = conferenceWifiConfig

        // Store conferenceWifiConfig.
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val netId = wifiManager.addNetwork(conferenceWifiConfig)
        if (netId != -1) {
            wifiManager.enableNetwork(netId, false)
            val result = wifiManager.saveConfiguration()
            if (!result) {
                Log.e(TAG, "Unknown error while calling WiFiManager.saveConfiguration()")
                Toast.makeText(context,
                        context.resources.getString(R.string.wifi_install_error_message),
                        Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "Unknown error while calling WiFiManager.addNetwork()")
            Toast.makeText(context,
                    context.resources.getString(R.string.wifi_install_error_message),
                    Toast.LENGTH_SHORT).show()
        }
    }

    fun uninstallConferenceWiFi(context: Context) {
        // Create conferenceConfig
        val conferenceConfig = conferenceWifiConfig

        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val configuredNetworks = wifiManager.configuredNetworks
        for (wifiConfig in configuredNetworks) {
            if (wifiConfig.SSID == conferenceConfig.SSID) {
                LOGW(TAG, "Removing network: " + wifiConfig.networkId)
                wifiManager.removeNetwork(wifiConfig.networkId)
            }
        }
    }

    /**
     * Helper method to decide whether to bypass conference WiFi setup.  Return true if
     * WiFi AP is already configured (WiFi adapter enabled) or WiFi configuration is complete
     * as per shared preference.
     */
    fun shouldBypassWiFiSetup(context: Context): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Is WiFi on?
        if (wifiManager.isWifiEnabled) {
            // Check for existing APs.
            val configs = wifiManager.configuredNetworks
            val conferenceSSID = conferenceWifiConfig.SSID
            for (config in configs) {
                if (conferenceSSID.equals(config.SSID, ignoreCase = true)) return true
            }
        }

        return WIFI_CONFIG_DONE == getWiFiConfigStatus(context)
    }

    fun isWiFiEnabled(context: Context): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    fun isWiFiApConfigured(context: Context): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val configs = wifiManager.configuredNetworks ?: return false

// Check for existing APs.
        val conferenceSSID = conferenceWifiConfig.SSID
        for (config in configs) {
            if (conferenceSSID.equals(config.SSID, ignoreCase = true)) return true
        }
        return false
    }

    // Stored settings_prefs associated with WiFi AP configuration.
    fun getWiFiConfigStatus(context: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getString(PREF_WIFI_AP_CONFIG, null)
    }

    fun setWiFiConfigStatus(context: Context, status: String) {
        if (WIFI_CONFIG_DONE != status && WIFI_CONFIG_REQUESTED != status)
            throw IllegalArgumentException("Invalid WiFi Config status: " + status)
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putString(PREF_WIFI_AP_CONFIG, status).apply()
    }

    fun installWiFiIfRequested(context: Context): Boolean {
        if (WIFI_CONFIG_REQUESTED == getWiFiConfigStatus(context) && isWiFiEnabled(context)) {
            installConferenceWiFi(context)
            if (isWiFiApConfigured(context)) {
                setWiFiConfigStatus(context, WiFiUtils.WIFI_CONFIG_DONE)
                return true
            }
        }
        return false
    }

    fun showWiFiDialog(activity: Activity) {
        val fm = activity.fragmentManager
        val ft = fm.beginTransaction()
        val prev = fm.findFragmentByTag("dialog_wifi")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)
        WiFiDialog.newInstance(isWiFiEnabled(activity)).show(ft, "dialog_wifi")
    }

    class WiFiDialog : DialogFragment() {

        private var mWiFiEnabled: Boolean = false

        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
            val padding = resources.getDimensionPixelSize(R.dimen.content_padding_normal)
            val wifiTextView = TextView(activity)
            val dialogCallToActionText: Int
            val dialogPositiveButtonText: Int

            mWiFiEnabled = arguments.getBoolean(ARG_WIFI_ENABLED)
            if (mWiFiEnabled) {
                dialogCallToActionText = R.string.calltoaction_wifi_configure
                dialogPositiveButtonText = R.string.wifi_dialog_button_configure
            } else {
                dialogCallToActionText = R.string.calltoaction_wifi_settings
                dialogPositiveButtonText = R.string.wifi_dialog_button_settings
            }
            wifiTextView.text = Html.fromHtml(getString(R.string.description_setup_wifi_body) + getString(dialogCallToActionText))
            wifiTextView.movementMethod = LinkMovementMethod.getInstance()
            wifiTextView.setPadding(padding, padding, padding, padding)
            val context = activity

            return AlertDialog.Builder(context).setTitle(R.string.description_configure_wifi).setView(wifiTextView).setPositiveButton(dialogPositiveButtonText
            ) { dialog, whichButton ->
                // Attempt to configure the Wi-Fi access point.
                if (mWiFiEnabled) {
                    installConferenceWiFi(context)
                    if (WiFiUtils.isWiFiApConfigured(context)) {
                        WiFiUtils.setWiFiConfigStatus(
                                context,
                                WiFiUtils.WIFI_CONFIG_DONE)
                    }
                    // Launch Wi-Fi settings screen for user to enable Wi-Fi.
                } else {
                    WiFiUtils.setWiFiConfigStatus(context,
                            WiFiUtils.WIFI_CONFIG_REQUESTED)
                    val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    wifiIntent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                    startActivity(wifiIntent)
                }
                dialog.dismiss()
            }.create()
        }

        companion object {
            private val ARG_WIFI_ENABLED = "com.google.samples.apps.iosched.ARG_WIFI_ENABLED"

            fun newInstance(wiFiEnabled: Boolean): WiFiDialog {
                val wiFiDialogFragment = WiFiDialog()

                val args = Bundle()
                args.putBoolean(ARG_WIFI_ENABLED, wiFiEnabled)
                wiFiDialogFragment.arguments = args

                return wiFiDialogFragment
            }
        }
    }

    /**
     * Returns whether we should or should not offer to set up wifi. If asCard == true
     * this will decide whether or not to offer wifi setup actively (as a card, for instance).
     * If asCard == false, this will return whether or not to offer wifi setup passively
     * (in the overflow menu, for instance).
     */
    fun shouldOfferToSetupWifi(context: Context, actively: Boolean): Boolean {
        val now = UIUtils.getCurrentTime(context)
        if (now < Config.WIFI_SETUP_OFFER_START) {
            LOGW(TAG, "Too early to offer wifi")
            return false
        }
        if (now > Config.CONFERENCE_END_MILLIS) {
            LOGW(TAG, "Too late to offer wifi")
            return false
        }
        if (!WiFiUtils.isWiFiEnabled(context)) {
            LOGW(TAG, "Wifi isn't enabled")
            return false
        }
        if (!SettingsUtils.isAttendeeAtVenue(context)) {
            LOGW(TAG, "Attendee isn't onsite so wifi wouldn't matter")
            return false
        }
        if (WiFiUtils.isWiFiApConfigured(context)) {
            LOGW(TAG, "Attendee is already setup for wifi.")
            return false
        }
        if (actively && SettingsUtils.hasDeclinedWifiSetup(context)) {
            LOGW(TAG, "Attendee opted out of wifi.")
            return false
        }
        return true
    }

    private // Must be in double quotes to tell system this is an ASCII SSID and passphrase.
    val conferenceWifiConfig: WifiConfiguration
        get() {
            val conferenceConfig = WifiConfiguration()
            conferenceConfig.SSID = String.format("\"%s\"", BuildConfig.WIFI_SSID)
            conferenceConfig.preSharedKey = String.format("\"%s\"", BuildConfig.WIFI_PASSPHRASE)

            return conferenceConfig
        }
}
