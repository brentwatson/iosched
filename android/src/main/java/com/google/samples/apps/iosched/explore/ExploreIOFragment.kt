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

package com.google.samples.apps.iosched.explore

import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.explore.data.ItemGroup
import com.google.samples.apps.iosched.explore.data.MessageData
import com.google.samples.apps.iosched.explore.data.SessionData
import com.google.samples.apps.iosched.framework.PresenterFragmentImpl
import com.google.samples.apps.iosched.framework.QueryEnum
import com.google.samples.apps.iosched.framework.UpdatableView
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.settings.ConfMessageCardUtils
import com.google.samples.apps.iosched.settings.ConfMessageCardUtils.ConferencePrefChangeListener
import com.google.samples.apps.iosched.settings.SettingsUtils
import com.google.samples.apps.iosched.ui.widget.CollectionView
import com.google.samples.apps.iosched.ui.widget.CollectionViewCallbacks
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout
import com.google.samples.apps.iosched.util.ImageLoader
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.google.samples.apps.iosched.util.ThrottledContentObserver
import com.google.samples.apps.iosched.util.UIUtils
import com.google.samples.apps.iosched.util.WiFiUtils
import java.util.*

/**
 * Display the Explore I/O cards. There are three styles of cards, which are
 * referred to as Groups by the [CollectionView] implementation.
 *
 *
 *
 *  * The live-streaming session card.
 *  * Time sensitive message cards.
 *  * Session topic cards.
 *
 *
 *
 * Only the final group of cards is dynamically loaded from a
 * [android.content.ContentProvider].
 */
class ExploreIOFragment : Fragment(), UpdatableView<ExploreModel>, CollectionViewCallbacks {

    /**
     * Used to load images asynchronously on a background thread.
     */
    private var mImageLoader: ImageLoader? = null

    /**
     * CollectionView representing the cards displayed to the user.
     */
    private var mCollectionView: CollectionView? = null

    /**
     * Empty view displayed when `mCollectionView` is empty.
     */
    private var mEmptyView: View? = null

    private val mListeners = ArrayList<UpdatableView.UserActionListener>()

    private var mSessionsObserver: ThrottledContentObserver? = null
    private var mTagsObserver: ThrottledContentObserver? = null

    private val mConfMessagesAnswerChangeListener = object : ConferencePrefChangeListener() {
        override fun onPrefChanged(key: String, value: Boolean) {
            fireReloadEvent()
        }
    }

    private val mSettingsChangeListener = OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (SettingsUtils.PREF_DECLINED_WIFI_SETUP == key) {
            fireReloadEvent()
        }
    }

    override fun displayData(model: ExploreModel, query: QueryEnum) {
        // Only display data when the tag metadata is available.
        if (model.tagTitles != null) {
            updateCollectionView(model)
        }
    }

    override fun displayErrorMessage(query: QueryEnum) {
    }

    override fun getContext(): Context {
        return activity
    }

    override fun addListener(toAdd: UpdatableView.UserActionListener) {
        mListeners.add(toAdd)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        val root = inflater.inflate(R.layout.explore_io_frag, container, false)
        mCollectionView = root.findViewById(R.id.explore_collection_view) as CollectionView
        mEmptyView = root.findViewById(android.R.id.empty)
        activity.overridePendingTransition(0, 0)

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mImageLoader = ImageLoader(activity, R.drawable.io_logo)
    }

    private fun setContentTopClearance(clearance: Int) {
        if (mCollectionView != null) {
            mCollectionView!!.setContentTopClearance(clearance)
        }
    }

    override fun onResume() {
        super.onResume()
        activity.invalidateOptionsMenu()

        // configure fragment's top clearance to take our overlaid controls (Action Bar
        // and spinner box) into account.
        val actionBarSize = UIUtils.calculateActionBarSize(activity)
        val drawShadowFrameLayout = activity.findViewById(R.id.main_content) as DrawShadowFrameLayout
        drawShadowFrameLayout?.setShadowTopOffset(actionBarSize)
        setContentTopClearance(actionBarSize)
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        // Register preference change listeners
        ConfMessageCardUtils.registerPreferencesChangeListener(context,
                mConfMessagesAnswerChangeListener)
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.registerOnSharedPreferenceChangeListener(mSettingsChangeListener)

        // Register content observers
        mSessionsObserver = ThrottledContentObserver(object : ThrottledContentObserver.Callbacks {
            override fun onThrottledContentObserverFired() {
                fireReloadEvent()
                fireReloadTagsEvent()
            }
        })
        mTagsObserver = ThrottledContentObserver(object : ThrottledContentObserver.Callbacks {
            override fun onThrottledContentObserverFired() {
                fireReloadTagsEvent()
            }
        })

    }

    override fun onDetach() {
        super.onDetach()
        if (mConfMessagesAnswerChangeListener != null) {
            ConfMessageCardUtils.unregisterPreferencesChangeListener(context,
                    mConfMessagesAnswerChangeListener)
        }
        if (mSettingsChangeListener != null) {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.unregisterOnSharedPreferenceChangeListener(mSettingsChangeListener)
        }
        activity.contentResolver.unregisterContentObserver(mSessionsObserver!!)
        activity.contentResolver.unregisterContentObserver(mTagsObserver!!)
    }

    /**
     * Update the CollectionView with a new [CollectionView.Inventory] of cards to display.
     */
    private fun updateCollectionView(model: ExploreModel) {
        LOGD(TAG, "Updating collection view.")

        val inventory = CollectionView.Inventory()
        var inventoryGroup: CollectionView.InventoryGroup

        // BEGIN Add Message Cards.
        // Message cards are only used for onsite attendees.
        if (SettingsUtils.isAttendeeAtVenue(context)) {
            // Users are required to opt in or out of whether they want conference message cards.
            if (!ConfMessageCardUtils.hasAnsweredConfMessageCardsPrompt(context)) {
                // User has not answered whether they want to opt in.
                // Build a opt-in/out card.
                inventoryGroup = CollectionView.InventoryGroup(GROUP_ID_MESSAGE_CARDS)
                val conferenceMessageOptIn = MessageCardHelper.getConferenceOptInMessageData(context)
                inventoryGroup.addItemWithTag(conferenceMessageOptIn)
                inventoryGroup.setDisplayCols(1)
                inventory.addGroup(inventoryGroup)
            } else if (ConfMessageCardUtils.isConfMessageCardsEnabled(context)) {
                ConfMessageCardUtils.enableActiveCards(context)

                // Note that for these special cards, we'll never show more than one at a time to
                // prevent overloading the user with messages. We want each new message to be
                // notable.
                if (shouldShowCard(ConfMessageCardUtils.ConfMessageCard.CONFERENCE_CREDENTIALS)) {
                    inventoryGroup = CollectionView.InventoryGroup(GROUP_ID_MESSAGE_CARDS)
                    val conferenceMessageOptIn = MessageCardHelper.getConferenceCredentialsMessageData(context)
                    inventoryGroup.addItemWithTag(conferenceMessageOptIn)
                    inventoryGroup.setDisplayCols(1)
                    inventory.addGroup(inventoryGroup)
                } else if (shouldShowCard(ConfMessageCardUtils.ConfMessageCard.KEYNOTE_ACCESS)) {
                    inventoryGroup = CollectionView.InventoryGroup(GROUP_ID_MESSAGE_CARDS)
                    val conferenceMessageOptIn = MessageCardHelper.getKeynoteAccessMessageData(context)
                    inventoryGroup.addItemWithTag(conferenceMessageOptIn)
                    inventoryGroup.setDisplayCols(1)
                    inventory.addGroup(inventoryGroup)
                } else if (shouldShowCard(ConfMessageCardUtils.ConfMessageCard.AFTER_HOURS)) {
                    inventoryGroup = CollectionView.InventoryGroup(GROUP_ID_MESSAGE_CARDS)
                    val conferenceMessageOptIn = MessageCardHelper.getAfterHoursMessageData(context)
                    inventoryGroup.addItemWithTag(conferenceMessageOptIn)
                    inventoryGroup.setDisplayCols(1)
                    inventory.addGroup(inventoryGroup)
                } else if (shouldShowCard(ConfMessageCardUtils.ConfMessageCard.WIFI_FEEDBACK)) {
                    if (WiFiUtils.isWiFiEnabled(context) && WiFiUtils.isWiFiApConfigured(context)) {
                        inventoryGroup = CollectionView.InventoryGroup(GROUP_ID_MESSAGE_CARDS)
                        val conferenceMessageOptIn = MessageCardHelper.getWifiFeedbackMessageData(context)
                        inventoryGroup.addItemWithTag(conferenceMessageOptIn)
                        inventoryGroup.setDisplayCols(1)
                        inventory.addGroup(inventoryGroup)
                    }
                }
            }
            // Check whether a wifi setup card should be offered.
            if (WiFiUtils.shouldOfferToSetupWifi(context, true)) {
                // Build card asking users whether they want to enable wifi.
                inventoryGroup = CollectionView.InventoryGroup(GROUP_ID_MESSAGE_CARDS)
                val conferenceMessageOptIn = MessageCardHelper.getWifiSetupMessageData(context)
                inventoryGroup.addItemWithTag(conferenceMessageOptIn)
                inventoryGroup.setDisplayCols(1)
                inventory.addGroup(inventoryGroup)
            }
        }
        // END Add Message Cards.


        // Add Keynote card.
        val keynoteData = model.keynoteData
        if (keynoteData != null) {
            LOGD(TAG, "Keynote Live stream data found: " + model.keynoteData)
            inventoryGroup = CollectionView.InventoryGroup(GROUP_ID_KEYNOTE_STREAM_CARD)
            inventoryGroup.addItemWithTag(keynoteData)
            inventory.addGroup(inventoryGroup)
        }

        // Add Live Stream card.
        val liveStreamData = model.liveStreamData
        if (liveStreamData != null && liveStreamData.sessions.size > 0) {
            LOGD(TAG, "Live session data found: " + liveStreamData)
            inventoryGroup = CollectionView.InventoryGroup(GROUP_ID_LIVE_STREAM_CARD)
            liveStreamData.title = resources.getString(R.string.live_now)
            inventoryGroup.addItemWithTag(liveStreamData)
            inventory.addGroup(inventoryGroup)
        }

        LOGD(TAG, "Inventory item count:" + inventory.groupCount + " " + inventory.totalItemCount)

        val themeGroups = ArrayList<CollectionView.InventoryGroup>()
        val topicGroups = ArrayList<CollectionView.InventoryGroup>()

        for (topic in model.topics) {
            LOGD(TAG, topic.title + ": " + topic.sessions.size)
            if (topic.sessions.size > 0) {
                inventoryGroup = CollectionView.InventoryGroup(GROUP_ID_TOPIC_CARDS)
                inventoryGroup.addItemWithTag(topic)
                topic.title = getTranslatedTitle(topic.title!!, model)
                topicGroups.add(inventoryGroup)
            }
        }

        for (theme in model.themes) {
            LOGD(TAG, theme.title + ": " + theme.sessions.size)
            if (theme.sessions.size > 0) {
                inventoryGroup = CollectionView.InventoryGroup(GROUP_ID_THEME_CARDS)
                inventoryGroup.addItemWithTag(theme)
                theme.title = getTranslatedTitle(theme.title!!, model)
                themeGroups.add(inventoryGroup)
            }
        }

        // We want to evenly disperse the topics between the themes. So we'll divide the topic count
        // by theme count to get the number of themes to display between topics.
        var topicsPerTheme = topicGroups.size
        if (themeGroups.size > 0) {
            topicsPerTheme = topicGroups.size / themeGroups.size
        }
        val themeIterator = themeGroups.iterator()
        var currentTopicNum = 0
        for (topicGroup in topicGroups) {
            inventory.addGroup(topicGroup)
            currentTopicNum++
            if (currentTopicNum == topicsPerTheme) {
                if (themeIterator.hasNext()) {
                    inventory.addGroup(themeIterator.next())
                }
                currentTopicNum = 0
            }
        }
        // Append any leftovers.
        while (themeIterator.hasNext()) {
            inventory.addGroup(themeIterator.next())
        }

        val state = mCollectionView!!.onSaveInstanceState()
        mCollectionView!!.setCollectionAdapter(this)
        mCollectionView!!.updateInventory(inventory, false)
        if (state != null) {
            mCollectionView!!.onRestoreInstanceState(state)
        }

        // Show empty view if there were no Group cards.
        mEmptyView!!.visibility = if (inventory.groupCount < 1) View.VISIBLE else View.GONE
    }

    private fun getTranslatedTitle(title: String, model: ExploreModel): String {
        if (model.tagTitles!![title] != null) {
            return model.tagTitles!![title]!!
        } else {
            return title
        }
    }

    override fun newCollectionHeaderView(context: Context, groupId: Int, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.explore_io_card_header_with_button, parent, false)
    }

    override fun bindCollectionHeaderView(context: Context, view: View, groupId: Int,
                                          headerLabel: String, headerTag: Any) {
    }

    override fun newCollectionItemView(context: Context, groupId: Int, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(context)

        // First inflate the card container.
        val containerLayoutId: Int
        when (groupId) {
            GROUP_ID_TOPIC_CARDS, GROUP_ID_THEME_CARDS, GROUP_ID_LIVE_STREAM_CARD -> containerLayoutId = R.layout.explore_io_topic_theme_livestream_card_container
            else -> containerLayoutId = R.layout.explore_io_card_container
        }
        val containerView = inflater.inflate(containerLayoutId, parent, false) as ViewGroup
        // Explicitly tell Accessibility to ignore the entire containerView since we add specific
        // individual content descriptions on child Views.
        UIUtils.setAccessibilityIgnore(containerView)

        val containerContents = containerView.findViewById(
                R.id.explore_io_card_container_contents) as ViewGroup

        // Now inflate the header within the container cards.
        var headerLayoutId = -1
        when (groupId) {
            GROUP_ID_THEME_CARDS, GROUP_ID_TOPIC_CARDS, GROUP_ID_LIVE_STREAM_CARD -> headerLayoutId = R.layout.explore_io_card_header_with_button
        }
        // Inflate the specified number of items.
        if (headerLayoutId > -1) {
            inflater.inflate(headerLayoutId, containerContents, true)
        }

        // Now inflate the items within the container cards.
        var itemLayoutId = -1
        var numItems = 1
        when (groupId) {
            GROUP_ID_KEYNOTE_STREAM_CARD -> {
                itemLayoutId = R.layout.explore_io_keynote_stream_item
                numItems = 1
            }
            GROUP_ID_THEME_CARDS -> {
                itemLayoutId = R.layout.explore_io_topic_theme_livestream_item
                numItems = ExploreModel.getThemeSessionLimit(getContext())
            }
            GROUP_ID_TOPIC_CARDS -> {
                itemLayoutId = R.layout.explore_io_topic_theme_livestream_item
                numItems = ExploreModel.getTopicSessionLimit(getContext())
            }
            GROUP_ID_LIVE_STREAM_CARD -> {
                itemLayoutId = R.layout.explore_io_topic_theme_livestream_item
                numItems = 3
            }
            GROUP_ID_MESSAGE_CARDS -> {
                itemLayoutId = R.layout.explore_io_message_card_item
                numItems = 1
            }
        }
        // Inflate the specified number of items.
        if (itemLayoutId > -1) {
            for (itemIndex in 0..numItems - 1) {
                inflater.inflate(itemLayoutId, containerContents, true)
            }
        }
        return containerView
    }

    override fun bindCollectionItemView(context: Context, view: View, groupId: Int,
                                        indexInGroup: Int, dataIndex: Int, tag: Any) {
        if (GROUP_ID_KEYNOTE_STREAM_CARD == groupId || GROUP_ID_MESSAGE_CARDS == groupId) {
            // These two group id types don't have child views.
            populateSubItemInfo(context, view, groupId, tag)
            // Set the object's data into the view's tag so that the click listener on the view can
            // extract it and use the data to handle a click.
            val clickableView = view.findViewById(R.id.explore_io_clickable_item)
            if (clickableView != null) {
                clickableView.tag = tag
            }
        } else {
            // These group ids have children who are child items.
            val viewWithChildrenSubItems = view.findViewById(
                    R.id.explore_io_card_container_contents) as ViewGroup
            val itemGroup = tag as ItemGroup

            // Set Header tag and title.
            viewWithChildrenSubItems.getChildAt(0).tag = tag
            val titleTextView = view.findViewById(android.R.id.title) as TextView
            val headerView = view.findViewById(R.id.explore_io_card_header_layout)
            if (headerView != null) {
                headerView.contentDescription = getString(R.string.more_items_button_desc_with_label_a11y,
                        itemGroup.title)
            }

            // Set the tag on the moreButton so it can be accessed by the click listener.
            val moreButton = view.findViewById(android.R.id.button1)
            if (moreButton != null) {
                moreButton.tag = tag
            }
            if (titleTextView != null) {
                titleTextView.text = itemGroup.title
            }

            // Skipping first child b/c it is a header view.
            for (viewChildIndex in 1..viewWithChildrenSubItems.childCount - 1) {
                val childView = viewWithChildrenSubItems.getChildAt(viewChildIndex)

                val sessionIndex = viewChildIndex - 1
                val sessionSize = itemGroup.sessions.size
                if (childView != null && sessionIndex < sessionSize) {
                    childView.visibility = View.VISIBLE
                    val sessionData = itemGroup.sessions[sessionIndex]
                    childView.tag = sessionData
                    populateSubItemInfo(context, childView, groupId, sessionData)
                } else if (childView != null) {
                    childView.visibility = View.GONE
                }
            }
        }

    }

    private fun populateSubItemInfo(context: Context, view: View, groupId: Int, tag: Any) {
        // Locate the views that may be used to configure the item being bound to this view.
        // Not all elements are used in all views so some will be null.
        val titleView = view.findViewById(R.id.title) as TextView
        val descriptionView = view.findViewById(R.id.description) as TextView
        val startButton = view.findViewById(R.id.buttonStart) as Button
        val endButton = view.findViewById(R.id.buttonEnd) as Button
        val iconView = view.findViewById(R.id.icon) as ImageView

        // Load item elements common to THEME and TOPIC group cards.
        if (tag is SessionData) {
            titleView.text = tag.sessionName
            if (!TextUtils.isEmpty(tag.imageUrl)) {
                val imageView = view.findViewById(R.id.thumbnail) as ImageView
                mImageLoader!!.loadImage(tag.imageUrl!!, imageView)
            }
            val inScheduleIndicator = view.findViewById(R.id.indicator_in_schedule) as ImageView
            if (inScheduleIndicator != null) {  // check not keynote
                inScheduleIndicator.visibility = if (tag.isInSchedule) View.VISIBLE else View.GONE
            }
            if (!TextUtils.isEmpty(tag.details)) {
                descriptionView.text = tag.details
            }
        }

        // Bind message data if this item is meant to be bound as a message card.
        if (GROUP_ID_MESSAGE_CARDS == groupId) {
            val messageData = tag as MessageData
            descriptionView.text = messageData.getMessageString(context)
            if (messageData.endButtonStringResourceId != -1) {
                endButton.setText(messageData.endButtonStringResourceId)
            } else {
                endButton.visibility = View.GONE
            }
            if (messageData.startButtonStringResourceId != -1) {
                startButton.setText(messageData.startButtonStringResourceId)
            } else {
                startButton.visibility = View.GONE
            }
            if (messageData.iconDrawableId > 0) {
                iconView.visibility = View.VISIBLE
                iconView.setImageResource(messageData.iconDrawableId)
            } else {
                iconView.visibility = View.GONE
            }
            if (messageData.startButtonClickListener != null) {
                startButton.setOnClickListener(messageData.startButtonClickListener)
            }
            if (messageData.endButtonClickListener != null) {
                endButton.setOnClickListener(messageData.endButtonClickListener)
            }
        }
    }

    /**
     * Let all UserActionListener know that the video list has been reloaded and that therefore we
     * need to display another random set of sessions.
     */
    private fun fireReloadEvent() {
        if (!isAdded) {
            return
        }
        for (h1 in mListeners) {
            val args = Bundle()
            args.putInt(PresenterFragmentImpl.KEY_RUN_QUERY_ID,
                    ExploreModel.ExploreQueryEnum.SESSIONS.id)
            h1.onUserAction(ExploreModel.ExploreUserActionEnum.RELOAD, args)
        }
    }

    private fun fireReloadTagsEvent() {
        if (!isAdded) {
            return
        }
        for (h1 in mListeners) {
            val args = Bundle()
            args.putInt(PresenterFragmentImpl.KEY_RUN_QUERY_ID,
                    ExploreModel.ExploreQueryEnum.TAGS.id)
            h1.onUserAction(ExploreModel.ExploreUserActionEnum.RELOAD, args)
        }
    }

    override fun getDataUri(query: QueryEnum): Uri {
        if (query === ExploreModel.ExploreQueryEnum.SESSIONS) {
            return ScheduleContract.Sessions.CONTENT_URI
        }
        return Uri.EMPTY
    }

    private fun shouldShowCard(card: ConfMessageCardUtils.ConfMessageCard): Boolean {

        val shouldShow = ConfMessageCardUtils.shouldShowConfMessageCard(context, card)
        val hasDismissed = ConfMessageCardUtils.hasDismissedConfMessageCard(context,
                card)
        return shouldShow && !hasDismissed
    }

    companion object {

        private val TAG = makeLogTag(ExploreIOFragment::class.java)

        private val GROUP_ID_KEYNOTE_STREAM_CARD = 10

        private val GROUP_ID_LIVE_STREAM_CARD = 15

        private val GROUP_ID_MESSAGE_CARDS = 20

        private val GROUP_ID_TOPIC_CARDS = 30

        private val GROUP_ID_THEME_CARDS = 40
    }
}
