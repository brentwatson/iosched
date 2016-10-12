package com.google.samples.apps.iosched.explore.data

import android.content.Context
import android.text.Html
import android.text.Spanned
import android.view.View

/**
 * View data describing a message card to be displayed on the Explore I/O screen.
 */
class MessageData {
    /**
     * String resource for the message to be displayed on the card.
     */
    private var mMessageStringResourceId = -1

    /**
     * String resource for the text to be placed on the left aligned button. When RTL is active
     * this indicates the button closer to "start."
     */
    var startButtonStringResourceId = -1

    /**
     * String resource for the text to be placed on the right-aligned button. When RTL is active
     * this indicates the button closer to "end."
     */
    var endButtonStringResourceId = -1

    var iconDrawableId = -1

    /**
     * The click listener to be attached to the left aligned button. When RTL is active this
     * indicates the button closer to "start."
     */
    var startButtonClickListener: View.OnClickListener? = null

    /**
     * The click listener to be attached to the right aligned button. When RTL is active this
     * indicates the button closer to "end."
     */
    var endButtonClickListener: View.OnClickListener? = null

    fun setMessageStringResourceId(messageStringResourceId: Int) {
        mMessageStringResourceId = messageStringResourceId
    }

    fun getMessageString(context: Context): Spanned {
        return Html.fromHtml(context.resources.getString(mMessageStringResourceId))
    }
}
