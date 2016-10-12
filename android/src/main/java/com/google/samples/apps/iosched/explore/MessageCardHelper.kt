package com.google.samples.apps.iosched.explore

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.view.View
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.explore.data.MessageData
import com.google.samples.apps.iosched.settings.ConfMessageCardUtils
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.google.samples.apps.iosched.util.WiFiUtils

/**
 * Helper class to create message data view objects representing MessageCards for the Explore I/O
 * stream.
 */
object MessageCardHelper {
    private val TAG = makeLogTag(MessageCardHelper::class.java)

    private val TWITTER_PACKAGE_NAME = "com.twitter.android"
    private val GPLUS_PACKAGE_NAME = "com.google.android.apps.plus"

    /**
     * Return the conference messages opt-in data.
     */
    fun getConferenceOptInMessageData(context: Context): MessageData {
        val messageData = MessageData()
        messageData.startButtonStringResourceId = R.string.explore_io_msgcards_answer_no
        messageData.setMessageStringResourceId(R.string.explore_io_msgcards_ask_opt_in)
        messageData.endButtonStringResourceId = R.string.explore_io_msgcards_answer_yes

        messageData.startButtonClickListener = View.OnClickListener{ view ->
            LOGD(TAG, "Marking conference messages question answered with decline.")
            ConfMessageCardUtils.markAnsweredConfMessageCardsPrompt(view.context, true)
            ConfMessageCardUtils.setConfMessageCardsEnabled(view.context, false)
        }
        messageData.endButtonClickListener = View.OnClickListener { view ->
            LOGD(TAG, "Marking conference messages question answered with affirmation.")
            ConfMessageCardUtils.markAnsweredConfMessageCardsPrompt(view.context, true)
            ConfMessageCardUtils.setConfMessageCardsEnabled(view.context, true)
        }

        return messageData
    }

    /**
     * Return the wifi setup card data.
     */
    fun getWifiSetupMessageData(context: Context): MessageData {
        val messageData = MessageData()
        messageData.startButtonStringResourceId = R.string.explore_io_msgcards_answer_no
        messageData.setMessageStringResourceId(R.string.question_setup_wifi_card_text)
        messageData.endButtonStringResourceId = R.string.explore_io_msgcards_answer_yes
        messageData.iconDrawableId = R.drawable.message_card_wifi

        messageData.startButtonClickListener = View.OnClickListener { view ->
            LOGD(TAG, "Marking wifi setup declined.")

            // Switching like this ensure the value change listener is fired.
            SettingsUtils.markDeclinedWifiSetup(view.context, false)
            SettingsUtils.markDeclinedWifiSetup(view.context, true)
        }
        messageData.endButtonClickListener = View.OnClickListener { view ->
            LOGD(TAG, "Installing conference wifi.")
            WiFiUtils.installConferenceWiFi(view.context)

            // Switching like this ensure the value change listener is fired.
            SettingsUtils.markDeclinedWifiSetup(view.context, true)
            SettingsUtils.markDeclinedWifiSetup(view.context, false)
        }

        return messageData
    }


    /**
     * Return card data representing a message to send to users before registering.
     */
    fun getConferenceCredentialsMessageData(context: Context): MessageData {
        val messageData = MessageData()
        messageData.setMessageStringResourceId(R.string.explore_io_msgcards_conf_creds_card)
        messageData.endButtonStringResourceId = R.string.got_it
        messageData.iconDrawableId = R.drawable.message_card_credentials

        messageData.endButtonClickListener = View.OnClickListener { view ->
            LOGD(TAG, "Marking conference credentials card dismissed.")

            ConfMessageCardUtils.markDismissedConfMessageCard(
                    view.context,
                    ConfMessageCardUtils.ConfMessageCard.CONFERENCE_CREDENTIALS)
        }

        return messageData
    }

    /**
     * Return card data representing a message to allow attendees to provide wifi feedback.
     */
    fun getWifiFeedbackMessageData(context: Context): MessageData {
        val messageData = MessageData()
        messageData.setMessageStringResourceId(R.string.explore_io_msgcards_wifi_feedback)
        messageData.startButtonStringResourceId = R.string.explore_io_msgcards_answer_no
        messageData.endButtonStringResourceId = R.string.explore_io_msgcards_answer_yes
        messageData.iconDrawableId = R.drawable.message_card_wifi

        messageData.startButtonClickListener = View.OnClickListener { view ->
            LOGD(TAG, "Marking conference credentials card dismissed.")
            ConfMessageCardUtils.markDismissedConfMessageCard(
                    view.context,
                    ConfMessageCardUtils.ConfMessageCard.WIFI_FEEDBACK)
        }

        messageData.endButtonClickListener = View.OnClickListener { view ->
            LOGD(TAG, "Providing feedback")
            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(Intent.EXTRA_TEXT, "#io15wifi ")
            sendIntent.type = "text/plain"

            val isGPlusInstalled = isPackageInstalledAndEnabled(view.context,
                    GPLUS_PACKAGE_NAME)
            val isTwitterInstalled = isPackageInstalledAndEnabled(view.context,
                    TWITTER_PACKAGE_NAME)

            if (isGPlusInstalled) {
                sendIntent.`package` = GPLUS_PACKAGE_NAME
            } else if (isTwitterInstalled) {
                sendIntent.`package` = TWITTER_PACKAGE_NAME
            }

            view.context.startActivity(sendIntent)
            // Hide the card for now.
            ConfMessageCardUtils.markShouldShowConfMessageCard(view.context,
                    ConfMessageCardUtils.ConfMessageCard.WIFI_FEEDBACK, false)
        }


        return messageData
    }

    /**
     * Return card data for instructions on where to queue for the Keynote.
     */
    fun getKeynoteAccessMessageData(context: Context): MessageData {
        val messageData = MessageData()
        messageData.setMessageStringResourceId(R.string.explore_io_msgcards_keynote_access_card)
        messageData.endButtonStringResourceId = R.string.got_it
        messageData.iconDrawableId = R.drawable.message_card_keynote

        messageData.endButtonClickListener = View.OnClickListener { view ->
            LOGD(TAG, "Marking keynote access card dismissed.")

            ConfMessageCardUtils.markDismissedConfMessageCard(
                    view.context,
                    ConfMessageCardUtils.ConfMessageCard.KEYNOTE_ACCESS)
        }

        return messageData
    }

    /**
     * Return card data for information about the After Hours party.
     */
    fun getAfterHoursMessageData(context: Context): MessageData {
        val messageData = MessageData()
        messageData.setMessageStringResourceId(R.string.explore_io_msgcards_after_hours_card)
        messageData.endButtonStringResourceId = R.string.got_it
        messageData.iconDrawableId = R.drawable.message_card_after_hours

        messageData.endButtonClickListener = View.OnClickListener { view ->
            LOGD(TAG, "Marking after hours card dismissed.")

            ConfMessageCardUtils.markDismissedConfMessageCard(
                    view.context,
                    ConfMessageCardUtils.ConfMessageCard.AFTER_HOURS)
        }

        return messageData
    }

    /**
     * Return whether a package is installed.
     */
    fun isPackageInstalledAndEnabled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        var info: PackageInfo?
        try {
            info = pm.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            info = null
        }

        return info != null &&
                info.applicationInfo != null &&
                info.applicationInfo.enabled
    }
}
