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

package com.google.samples.apps.iosched.session

import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.explore.ExploreSessionsActivity
import com.google.samples.apps.iosched.framework.QueryEnum
import com.google.samples.apps.iosched.framework.UpdatableView
import com.google.samples.apps.iosched.framework.UserActionEnum
import com.google.samples.apps.iosched.model.TagMetadata
import com.google.samples.apps.iosched.session.SessionDetailModel.SessionDetailQueryEnum
import com.google.samples.apps.iosched.session.SessionDetailModel.SessionDetailUserActionEnum
import com.google.samples.apps.iosched.ui.widget.CheckableFloatingActionButton
import com.google.samples.apps.iosched.ui.widget.MessageCardView
import com.google.samples.apps.iosched.ui.widget.ObservableScrollView
import com.google.samples.apps.iosched.util.*
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import java.util.*

/**
 * Displays the details about a session. The user can add/remove a session from the schedule, watch
 * a live stream if available, watch the session on YouTube, view the map, share the session, and
 * submit feedback.
 */
class SessionDetailFragment : Fragment(), ObservableScrollView.Callbacks, UpdatableView<SessionDetailModel> {

    private var mAddScheduleButtonContainer: View? = null
    private var mAddScheduleButton: CheckableFloatingActionButton? = null

    private var mAddScheduleButtonContainerHeightPixels: Int = 0

    private var mScrollViewChild: View? = null

    private var mTitle: TextView? = null

    private var mSubtitle: TextView? = null

    private var mScrollView: ObservableScrollView? = null

    private var mAbstract: TextView? = null

    private var mPlusOneIcon: ImageView? = null

    private var mTwitterIcon: ImageView? = null

    private var mLiveStreamVideocamIconAndText: TextView? = null

    private var mLiveStreamPlayIconAndText: TextView? = null

    private var mTags: LinearLayout? = null

    private var mTagsContainer: ViewGroup? = null

    private var mRequirements: TextView? = null

    private var mHeaderBox: View? = null

    private var mDetailsContainer: View? = null

    private var mPhotoHeightPixels: Int = 0

    private var mHeaderHeightPixels: Int = 0

    private var mHasPhoto: Boolean = false

    private var mPhotoViewContainer: View? = null

    private var mPhotoView: ImageView? = null

    private var mMaxHeaderElevation: Float = 0.toFloat()

    private var mFABElevation: Float = 0.toFloat()

    private var mSpeakersImageLoader: ImageLoader? = null
    private var mNoPlaceholderImageLoader: ImageLoader? = null

    private var mTimeHintUpdaterRunnable: Runnable? = null

    private val mDeferredUiOperations = ArrayList<Runnable>()

    private var mLUtils: LUtils? = null

    private var mHandler: Handler? = null

    private var mAnalyticsScreenViewHasFired: Boolean = false

    internal var mListeners: MutableList<UpdatableView.UserActionListener> = ArrayList()

    override fun addListener(toAdd: UpdatableView.UserActionListener) {
        mListeners.add(toAdd)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        mAnalyticsScreenViewHasFired = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        return inflater.inflate(R.layout.session_detail_frag, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mLUtils = LUtils.getInstance(activity as AppCompatActivity)
        mHandler = Handler()
        initViews()
        initViewListeners()
    }


    override fun onResume() {
        super.onResume()

        if (mTimeHintUpdaterRunnable != null) {
            mHandler!!.postDelayed(mTimeHintUpdaterRunnable,
                    SessionDetailConstants.TIME_HINT_UPDATE_INTERVAL.toLong())
        }
    }

    override fun onPause() {
        super.onPause()
        mHandler!!.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mScrollView == null) {
            return
        }

        val vto = mScrollView!!.viewTreeObserver
        if (vto.isAlive) {
            vto.removeGlobalOnLayoutListener(mGlobalLayoutListener)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.session_detail, menu)
        tryExecuteDeferredUiOperations()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_map_room -> {
                sendUserAction(SessionDetailUserActionEnum.SHOW_MAP, null)
                return true
            }
            R.id.menu_share -> {
                sendUserAction(SessionDetailUserActionEnum.SHOW_SHARE, null)
                return true
            }
        }
        return false
    }

    private fun sendUserAction(action: UserActionEnum, args: Bundle?) {
        for (l in mListeners) {
            l.onUserAction(action, args)
        }
    }

    private fun initViews() {
        mFABElevation = resources.getDimensionPixelSize(R.dimen.fab_elevation).toFloat()
        mMaxHeaderElevation = resources.getDimensionPixelSize(
                R.dimen.session_detail_max_header_elevation).toFloat()

        mScrollView = activity.findViewById(R.id.scroll_view) as ObservableScrollView
        mScrollView!!.addCallbacks(this)
        val vto = mScrollView!!.viewTreeObserver
        if (vto.isAlive) {
            vto.addOnGlobalLayoutListener(mGlobalLayoutListener)
        }

        mScrollViewChild = activity.findViewById(R.id.scroll_view_child)
        mScrollViewChild!!.visibility = View.INVISIBLE

        mDetailsContainer = activity.findViewById(R.id.details_container)
        mHeaderBox = activity.findViewById(R.id.header_session)
        mTitle = activity.findViewById(R.id.session_title) as TextView
        mSubtitle = activity.findViewById(R.id.session_subtitle) as TextView
        mPhotoViewContainer = activity.findViewById(R.id.session_photo_container)
        mPhotoView = activity.findViewById(R.id.session_photo) as ImageView

        mAbstract = activity.findViewById(R.id.session_abstract) as TextView

        mPlusOneIcon = activity.findViewById(R.id.gplus_icon_box) as ImageView
        mTwitterIcon = activity.findViewById(R.id.twitter_icon_box) as ImageView

        //Find view that shows a Videocam icon if the session is being live streamed.
        mLiveStreamVideocamIconAndText = activity.findViewById(
                R.id.live_stream_videocam_icon_and_text) as TextView

        // Find view that shows a play button and some text for the user to watch the session live stream.
        mLiveStreamPlayIconAndText = activity.findViewById(
                R.id.live_stream_play_icon_and_text) as TextView

        mRequirements = activity.findViewById(R.id.session_requirements) as TextView
        mTags = activity.findViewById(R.id.session_tags) as LinearLayout
        mTagsContainer = activity.findViewById(R.id.session_tags_container) as ViewGroup

        ViewCompat.setTransitionName(mPhotoView, SessionDetailConstants.TRANSITION_NAME_PHOTO)

        mAddScheduleButtonContainer = activity.findViewById(R.id.add_schedule_button_container)
        mAddScheduleButton = activity.findViewById(R.id.add_schedule_button) as CheckableFloatingActionButton

        mNoPlaceholderImageLoader = ImageLoader(context)
        mSpeakersImageLoader = ImageLoader(context, R.drawable.person_image_empty)
    }

    private val mGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        mAddScheduleButtonContainerHeightPixels = mAddScheduleButtonContainer!!.height
        recomputePhotoAndScrollingMetrics()
    }

    private fun recomputePhotoAndScrollingMetrics() {
        mHeaderHeightPixels = mHeaderBox!!.height

        mPhotoHeightPixels = 0
        if (mHasPhoto) {
            mPhotoHeightPixels = (mPhotoView!!.width / PHOTO_ASPECT_RATIO).toInt()
            mPhotoHeightPixels = Math.min(mPhotoHeightPixels, mScrollView!!.height * 2 / 3)
        }

        val lp: ViewGroup.LayoutParams
        lp = mPhotoViewContainer!!.layoutParams
        if (lp.height != mPhotoHeightPixels) {
            lp.height = mPhotoHeightPixels
            mPhotoViewContainer!!.layoutParams = lp
        }

        val mlp = mDetailsContainer!!.layoutParams as ViewGroup.MarginLayoutParams
        if (mlp.topMargin != mHeaderHeightPixels + mPhotoHeightPixels) {
            mlp.topMargin = mHeaderHeightPixels + mPhotoHeightPixels
            mDetailsContainer!!.layoutParams = mlp
        }

        onScrollChanged(0, 0) // trigger scroll handling
    }

    override fun displayData(data: SessionDetailModel, query: QueryEnum) {
        if (SessionDetailQueryEnum.SESSIONS === query) {
            displaySessionData(data)
        } else if (SessionDetailQueryEnum.FEEDBACK === query) {
            displayFeedbackData(data)
        } else if (SessionDetailQueryEnum.SPEAKERS === query) {
            displaySpeakersData(data)
        } else if (SessionDetailQueryEnum.TAG_METADATA === query) {
            displayTags(data)
        }
    }

    private fun initViewListeners() {
        mAddScheduleButton!!.setOnClickListener { view ->
            val starred = !(view as CheckableFloatingActionButton).isChecked
            showStarred(starred, true)
            if (starred) {
                sendUserAction(SessionDetailUserActionEnum.STAR, null)
            } else {
                sendUserAction(SessionDetailUserActionEnum.UNSTAR, null)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mAddScheduleButton!!.announceForAccessibility(if (starred)
                    getString(R.string.session_details_a11y_session_added)
                else
                    getString(R.string.session_details_a11y_session_removed))
            }
        }
    }


    private fun showStarred(starred: Boolean, allowAnimate: Boolean) {
        mAddScheduleButton!!.setChecked(starred, allowAnimate)

        mLUtils!!.setOrAnimatePlusCheckIcon(mAddScheduleButton!!, starred, allowAnimate)
        mAddScheduleButton!!.contentDescription = getString(if (starred)
            R.string.remove_from_schedule_desc
        else
            R.string.add_to_schedule_desc)
    }

    override fun displayErrorMessage(query: QueryEnum) {
    }

    override fun getDataUri(query: QueryEnum): Uri? {
        if (SessionDetailQueryEnum.SESSIONS === query) {
            return (activity as SessionDetailActivity).sessionUri
        } else {
            return null
        }
    }


    override fun getContext(): Context {
        return activity
    }

    override fun onScrollChanged(deltaX: Int, deltaY: Int) {
        // Reposition the header bar -- it's normally anchored to the top of the content,
        // but locks to the top of the screen on scroll
        val scrollY = mScrollView!!.scrollY

        val newTop = Math.max(mPhotoHeightPixels, scrollY).toFloat()
        mHeaderBox!!.translationY = newTop
        mAddScheduleButtonContainer!!.translationY = newTop + mHeaderHeightPixels - mAddScheduleButtonContainerHeightPixels / 2

        var gapFillProgress = 1f
        if (mPhotoHeightPixels != 0) {
            gapFillProgress = Math.min(Math.max(UIUtils.getProgress(scrollY,
                    0,
                    mPhotoHeightPixels), 0f), 1f)
        }

        ViewCompat.setElevation(mHeaderBox, gapFillProgress * mMaxHeaderElevation)
        ViewCompat.setElevation(mAddScheduleButtonContainer, gapFillProgress * mMaxHeaderElevation + mFABElevation)
        ViewCompat.setElevation(mAddScheduleButton, gapFillProgress * mMaxHeaderElevation + mFABElevation)

        // Move background photo (parallax effect)
        mPhotoViewContainer!!.translationY = scrollY * 0.5f
    }

    private fun displaySessionData(data: SessionDetailModel) {
        mTitle!!.text = data.sessionTitle
        mSubtitle!!.text = data.sessionSubtitle

        mPhotoViewContainer!!.setBackgroundColor(UIUtils.scaleSessionColorToDefaultBG(data.sessionColor))

        if (data.hasPhotoUrl()) {
            mHasPhoto = true
            mNoPlaceholderImageLoader!!.loadImage(data.photoUrl!!, mPhotoView!!, object : RequestListener<String, Bitmap> {
                override fun onException(e: Exception, model: String, target: Target<Bitmap>,
                                         isFirstResource: Boolean): Boolean {
                    mHasPhoto = false
                    recomputePhotoAndScrollingMetrics()
                    return false
                }

                override fun onResourceReady(resource: Bitmap, model: String, target: Target<Bitmap>,
                                             isFromMemoryCache: Boolean, isFirstResource: Boolean): Boolean {
                    // Trigger image transition
                    recomputePhotoAndScrollingMetrics()
                    return false
                }
            })
            recomputePhotoAndScrollingMetrics()
        } else {
            mHasPhoto = false
            recomputePhotoAndScrollingMetrics()
        }

        tryExecuteDeferredUiOperations()

        // Handle Keynote as a special case, where the user cannot remove it
        // from the schedule (it is auto added to schedule on sync)
        mAddScheduleButton!!.visibility = if (AccountUtils.hasActiveAccount(context) && !data.isKeynote)
            View.VISIBLE
        else
            View.INVISIBLE

        displayTags(data)

        if (!data.isKeynote) {
            showStarredDeferred(data.isInSchedule, false)
        }

        if (!TextUtils.isEmpty(data.sessionAbstract)) {
            UIUtils.setTextMaybeHtml(mAbstract!!, data.sessionAbstract!!)
            mAbstract!!.visibility = View.VISIBLE
        } else {
            mAbstract!!.visibility = View.GONE
        }

        // Build requirements section
        val requirementsBlock = activity.findViewById(R.id.session_requirements_block)
        val sessionRequirements = data.requirements
        if (!TextUtils.isEmpty(sessionRequirements)) {
            UIUtils.setTextMaybeHtml(mRequirements!!, sessionRequirements!!)
            requirementsBlock.visibility = View.VISIBLE
        } else {
            requirementsBlock.visibility = View.GONE
        }

        val relatedVideosBlock = activity.findViewById(
                R.id.related_videos_block) as ViewGroup
        relatedVideosBlock.visibility = View.GONE

        updateEmptyView(data)

        updateTimeBasedUi(data)

        if (data.liveStreamVideoWatched) {
            mPhotoView!!.setColorFilter(context.resources.getColor(
                    R.color.video_scrim_watched))
            mLiveStreamPlayIconAndText!!.text = getString(R.string.session_replay)
        }

        if (data.hasLiveStream()) {
            mLiveStreamPlayIconAndText!!.setOnClickListener {
                val videoId = YouTubeUtils.getVideoIdFromSessionData(data.youTubeUrl!!,
                        data.liveStreamId!!)
                YouTubeUtils.showYouTubeVideo(videoId!!, activity)
            }
        }

        fireAnalyticsScreenView(data.sessionTitle!!)

        mHandler!!.post {
            onScrollChanged(0, 0) // trigger scroll handling
            mScrollViewChild!!.visibility = View.VISIBLE
            //mAbstract.setTextIsSelectable(true);
        }

        mTimeHintUpdaterRunnable = Runnable {
            if (activity == null) {
                // Do not post a delayed message if the activity is detached.
                return@Runnable
            }
            updateTimeBasedUi(data)
            mHandler!!.postDelayed(mTimeHintUpdaterRunnable,
                    SessionDetailConstants.TIME_HINT_UPDATE_INTERVAL.toLong())
        }
        mHandler!!.postDelayed(mTimeHintUpdaterRunnable,
                SessionDetailConstants.TIME_HINT_UPDATE_INTERVAL.toLong())
    }

    /**
     * Sends a screen view to Google Analytics, if a screenview hasn't already been sent
     * since the fragment was loaded.  This prevents background syncs from causing superflous
     * screen views.

     * @param sessionTitle The name of the session being tracked.
     */
    private fun fireAnalyticsScreenView(sessionTitle: String) {
        if (!mAnalyticsScreenViewHasFired) {
            // ANALYTICS SCREEN: View the Session Details page for a specific session.
            // Contains: The session title.
            AnalyticsHelper.sendScreenView("Session: " + sessionTitle)
            mAnalyticsScreenViewHasFired = true
        }
    }

    private fun displayFeedbackData(data: SessionDetailModel) {
        if (data.hasFeedback()) {
            val giveFeedbackCardView = activity.findViewById(R.id.give_feedback_card) as MessageCardView
            if (giveFeedbackCardView != null) {
                giveFeedbackCardView.visibility = View.GONE
            }
        }
        LOGD(TAG, "User " + (if (data.hasFeedback()) "already gave" else "has not given")
                + " feedback for session.")
    }

    private fun displaySpeakersData(data: SessionDetailModel) {
        val speakersGroup = activity.findViewById(R.id.session_speakers_block) as ViewGroup

        // Remove all existing speakers (everything but first child, which is the header)
        for (i in speakersGroup.childCount - 1 downTo 1) {
            speakersGroup.removeViewAt(i)
        }

        val inflater = activity.layoutInflater

        var hasSpeakers = false

        val speakers = data.speakers

        for (speaker in speakers) {

            var speakerHeader = speaker.name
            if (!TextUtils.isEmpty(speaker.company)) {
                speakerHeader += ", " + speaker.company
            }

            val speakerView = inflater.inflate(R.layout.speaker_detail, speakersGroup, false)
            val speakerHeaderView = speakerView.findViewById(R.id.speaker_header) as TextView
            val speakerImageView = speakerView.findViewById(R.id.speaker_image) as ImageView
            val speakerAbstractView = speakerView.findViewById(R.id.speaker_abstract) as TextView
            val plusOneIcon = speakerView.findViewById(R.id.gplus_icon_box) as ImageView
            val twitterIcon = speakerView.findViewById(
                    R.id.twitter_icon_box) as ImageView

            setUpSpeakerSocialIcon(speaker, twitterIcon, speaker.twitterUrl,
                    UIUtils.TWITTER_COMMON_NAME, UIUtils.TWITTER_PACKAGE_NAME)

            setUpSpeakerSocialIcon(speaker, plusOneIcon, speaker.plusoneUrl,
                    UIUtils.GOOGLE_PLUS_COMMON_NAME, UIUtils.GOOGLE_PLUS_PACKAGE_NAME)

            // A speaker may have both a Twitter and GPlus page, only a Twitter page or only a
            // GPlus page, or neither. By default, align the Twitter icon to the right and the GPlus
            // icon to its left. If only a single icon is displayed, align it to the right.
            determineSocialIconPlacement(plusOneIcon, twitterIcon)

            if (!TextUtils.isEmpty(speaker.imageUrl) && mSpeakersImageLoader != null) {
                mSpeakersImageLoader!!.loadImage(speaker.imageUrl, speakerImageView)
            }

            speakerHeaderView.text = speakerHeader
            speakerImageView.contentDescription = getString(R.string.speaker_googleplus_profile, speakerHeader)
            UIUtils.setTextMaybeHtml(speakerAbstractView, speaker.abstract)

            if (!TextUtils.isEmpty(speaker.url)) {
                speakerImageView.isEnabled = true
                speakerImageView.setOnClickListener {
                    val speakerProfileIntent = Intent(Intent.ACTION_VIEW,
                            Uri.parse(speaker.url))
                    speakerProfileIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                    UIUtils.preferPackageForIntent(activity,
                            speakerProfileIntent,
                            UIUtils.GOOGLE_PLUS_PACKAGE_NAME)
                    startActivity(speakerProfileIntent)
                }
            } else {
                speakerImageView.isEnabled = false
                speakerImageView.setOnClickListener(null)
            }

            speakersGroup.addView(speakerView)
            hasSpeakers = true
        }

        speakersGroup.visibility = if (hasSpeakers) View.VISIBLE else View.GONE
        updateEmptyView(data)
    }

    /**
     * Determines visibility of a social icon, sets up a click listener to allow the user to
     * navigate to the social network associated with the icon, and sets up a content description
     * for the icon.
     */
    private fun setUpSpeakerSocialIcon(speaker: SessionDetailModel.Speaker,
                                       socialIcon: ImageView, socialUrl: String?,
                                       socialNetworkName: String, packageName: String) {
        if (socialUrl == null || socialUrl.isEmpty()) {
            socialIcon.visibility = View.GONE
        } else {
            socialIcon.contentDescription = getString(
                    R.string.speaker_social_page,
                    socialNetworkName,
                    speaker.name)
            socialIcon.setOnClickListener {
                UIUtils.fireSocialIntent(
                        activity,
                        Uri.parse(socialUrl),
                        packageName)
            }
        }
    }

    /**
     * Aligns the Twitter icon the parent bottom right. Aligns the G+ icon to the left of the
     * Twitter icon if it is present. Otherwise, aligns the G+ icon to the parent bottom right.
     */
    private fun determineSocialIconPlacement(plusOneIcon: ImageView, twitterIcon: ImageView) {
        if (plusOneIcon.visibility == View.VISIBLE) {
            // Set the dimensions of the G+ button.
            val socialIconDimension = resources.getDimensionPixelSize(
                    R.dimen.social_icon_box_size)
            val params = RelativeLayout.LayoutParams(
                    socialIconDimension, socialIconDimension)
            params.addRule(RelativeLayout.BELOW, R.id.speaker_abstract)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)

            if (twitterIcon.visibility == View.VISIBLE) {
                params.addRule(RelativeLayout.LEFT_OF, R.id.twitter_icon_box)
            } else {
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            }
            plusOneIcon.layoutParams = params
        }
    }

    private fun updateEmptyView(data: SessionDetailModel) {
        activity.findViewById(android.R.id.empty).visibility = if (data.sessionTitle != null && data.speakers.size == 0
                && !data.hasSummaryContent())
            View.VISIBLE
        else
            View.GONE
    }


    private fun updateTimeBasedUi(data: SessionDetailModel) {
        // Show "Live streamed" for all live-streamed sessions that aren't currently going on.
        mLiveStreamVideocamIconAndText!!.visibility = if (data.hasLiveStream() && !data.isSessionOngoing)
            View.VISIBLE
        else
            View.GONE

        if (data.hasLiveStream() && data.hasSessionStarted()) {
            // Show the play button and text only once the session starts.
            mLiveStreamVideocamIconAndText!!.visibility = View.VISIBLE

            if (data.isSessionOngoing) {
                mLiveStreamPlayIconAndText!!.text = getString(R.string.session_watch_live)
            } else {
                mLiveStreamPlayIconAndText!!.text = getString(R.string.session_watch)
                // TODO: implement Replay.
            }
        } else {
            mLiveStreamPlayIconAndText!!.visibility = View.GONE
        }

        // If the session is done, hide the FAB, and show the "Give feedback" card.
        if (data.isSessionReadyForFeedback) {
            mAddScheduleButton!!.visibility = View.INVISIBLE
            if (!data.hasFeedback() && data.isInScheduleWhenSessionFirstLoaded &&
                    !sDismissedFeedbackCard.contains(data.sessionId)) {
                showGiveFeedbackCard(data)
            }
        }

        var timeHint = ""

        if (TimeUtils.hasConferenceEnded(context)) {
            // No time hint to display.
            timeHint = ""
        } else if (data.hasSessionEnded()) {
            timeHint = getString(R.string.time_hint_session_ended)
        } else if (data.isSessionOngoing) {
            val minutesAgo = data.minutesSinceSessionStarted()
            if (minutesAgo > 1) {
                timeHint = getString(R.string.time_hint_started_min, minutesAgo)
            } else {
                timeHint = getString(R.string.time_hint_started_just)
            }
        } else {
            val minutesUntilStart = data.minutesUntilSessionStarts()
            if (minutesUntilStart > 0 && minutesUntilStart <= SessionDetailConstants.HINT_TIME_BEFORE_SESSION_MIN) {
                if (minutesUntilStart > 1) {
                    timeHint = getString(R.string.time_hint_about_to_start_min, minutesUntilStart)
                } else {
                    timeHint = getString(R.string.time_hint_about_to_start_shortly,
                            minutesUntilStart)
                }
            }
        }

        val timeHintView = activity.findViewById(R.id.time_hint) as TextView

        if (!TextUtils.isEmpty(timeHint)) {
            timeHintView.visibility = View.VISIBLE
            timeHintView.text = timeHint
        } else {
            timeHintView.visibility = View.GONE
        }
    }

    private fun displayTags(data: SessionDetailModel) {
        if (data.tagMetadata == null || data.tagsString == null) {
            mTagsContainer!!.visibility = View.GONE
            return
        }

        if (TextUtils.isEmpty(data.tagsString)) {
            mTagsContainer!!.visibility = View.GONE
        } else {
            mTagsContainer!!.visibility = View.VISIBLE
            mTags!!.removeAllViews()
            val inflater = LayoutInflater.from(context)
            val tagIds = data.tagsString!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            val tags = ArrayList<TagMetadata.Tag>()
            for (tagId in tagIds) {
                if (Config.Tags.SESSIONS == tagId || Config.Tags.SPECIAL_KEYNOTE == tagId) {
                    continue
                }

                val tag = data.tagMetadata!!.getTag(tagId) ?: continue

                tags.add(tag)
            }

            if (tags.size == 0) {
                mTagsContainer!!.visibility = View.GONE
                return
            }

            Collections.sort(tags, TagMetadata.TAG_DISPLAY_ORDER_COMPARATOR)

            for (tag in tags) {
                val chipView = inflater.inflate(
                        R.layout.include_session_tag_chip, mTags, false) as TextView
                chipView.text = tag.name
                chipView.contentDescription = getString(R.string.talkback_button, tag.name)
                chipView.setOnClickListener {
                    val intent = Intent(context, ExploreSessionsActivity::class.java).putExtra(ExploreSessionsActivity.EXTRA_FILTER_TAG, tag.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                }

                mTags!!.addView(chipView)
            }
        }
    }

    private fun showGiveFeedbackCard(data: SessionDetailModel) {
        val messageCardView = activity.findViewById(
                R.id.give_feedback_card) as MessageCardView
        messageCardView.show()
        messageCardView.setListener(object : MessageCardView.OnMessageCardButtonClicked {
            override fun onMessageCardButtonClicked(tag: String) {
                if ("GIVE_FEEDBACK" == tag) {
                    // ANALYTICS EVENT: Click on the "send feedback" action in Session Details.
                    // Contains: The session title.
                    AnalyticsHelper.sendEvent("Session", "Feedback", data.sessionTitle!!)
                    val intent = data.feedbackIntent
                    startActivity(intent)
                } else {
                    sDismissedFeedbackCard.add(data.sessionId!!)
                    messageCardView.dismiss()
                }
            }
        })
    }

    private fun showStarredDeferred(starred: Boolean, allowAnimate: Boolean) {
        mDeferredUiOperations.add(Runnable { showStarred(starred, allowAnimate) })
        tryExecuteDeferredUiOperations()
    }

    private fun tryExecuteDeferredUiOperations() {
        for (r in mDeferredUiOperations) {
            r.run()
            mDeferredUiOperations.clear()
        }
    }

    /*
         * Event structure:
         * Category -> "Session Details"
         * Action -> Link Text
         * Label -> Session's Title
         * Value -> 0.
         */
    private fun fireLinkEvent(actionId: Int, data: SessionDetailModel) {
        // ANALYTICS EVENT:  Click on a link in the Session Details page.
        // Contains: The link's name and the session title.
        AnalyticsHelper.sendEvent("Session", getString(actionId), data.sessionTitle!!)
    }

    companion object {

        private val TAG = LogUtils.makeLogTag(SessionDetailFragment::class.java)

        /**
         * Stores the session IDs for which the user has dismissed the "give feedback" card. This
         * information is kept for the duration of the app's execution so that if they say "No,
         * thanks", we don't show the card again for that session while the app is still executing.
         */
        private val sDismissedFeedbackCard = HashSet<String>()

        private val PHOTO_ASPECT_RATIO = 1.7777777f
    }
}
