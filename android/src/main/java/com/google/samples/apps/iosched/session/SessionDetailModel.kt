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

import android.content.Context
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Pair
import com.google.common.annotations.VisibleForTesting
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.feedback.SessionFeedbackActivity
import com.google.samples.apps.iosched.framework.Model
import com.google.samples.apps.iosched.framework.QueryEnum
import com.google.samples.apps.iosched.framework.UserActionEnum
import com.google.samples.apps.iosched.model.TagMetadata
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.service.SessionAlarmService
import com.google.samples.apps.iosched.service.SessionCalendarService
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.google.samples.apps.iosched.util.SessionsHelper
import com.google.samples.apps.iosched.util.UIUtils
import java.util.*

open class SessionDetailModel(private var mSessionUri: Uri?, private val mContext: Context, private val mSessionsHelper: SessionsHelper) : Model {

    var sessionId: String? = null
        private set

    private var mSessionLoaded = false

    open var sessionTitle: String? = null

    var sessionSubtitle: String? = null
        private set

    var sessionColor: Int = 0
        private set

    open var isInSchedule: Boolean = false

    open var isInScheduleWhenSessionFirstLoaded: Boolean = false

    open var isKeynote: Boolean = false

    private var mSessionStart: Long = 0

    private var mSessionEnd: Long = 0

    var sessionAbstract: String? = null
        private set

    var hashTag: String? = null
        private set

    var sessionUrl = ""
        private set

    private var mRoomId: String? = null

    private var mRoomName: String? = null

    open var tagsString: String? = null

    var liveStreamId: String? = null
        private set

    var youTubeUrl: String? = null
        private set

    var photoUrl: String? = null
        private set

    private var mHasLiveStream = false

    var liveStreamVideoWatched = false
        private set

    private var mHasFeedback = false

    var requirements: String? = null
        private set

    private var mSpeakersNames: String? = null

    open var tagMetadata: TagMetadata? = null

    /**
     * Holds a list of links for the session. The first element of the `Pair` is the resource
     * id for the string describing the link, the second is the `Intent` to launch when
     * selecting the link.
     */
    private val mLinks = ArrayList<Pair<Int, Intent>>()

    private val mSpeakers = ArrayList<Speaker>()

    private val mBuffer = StringBuilder()

    open val isSessionOngoing: Boolean
        get() {
            val currentTimeMillis = UIUtils.getCurrentTime(mContext)
            return currentTimeMillis > mSessionStart && currentTimeMillis <= mSessionEnd
        }

    fun hasSessionStarted(): Boolean {
        val currentTimeMillis = UIUtils.getCurrentTime(mContext)
        return currentTimeMillis > mSessionStart
    }

    fun hasSessionEnded(): Boolean {
        val currentTimeMillis = UIUtils.getCurrentTime(mContext)
        return currentTimeMillis > mSessionEnd
    }

    /**
     * Returns the number of minutes, rounded down, since session has started, or 0 if not started
     * yet.
     */
    fun minutesSinceSessionStarted(): Long {
        if (!hasSessionStarted()) {
            return 0L
        } else {
            val currentTimeMillis = UIUtils.getCurrentTime(mContext)
            // Rounded down number of minutes.
            return (currentTimeMillis - mSessionStart) / 60000
        }
    }

    /**
     * Returns the number of minutes, rounded up, until session stars, or 0 if already started.
     */
    fun minutesUntilSessionStarts(): Long {
        if (hasSessionStarted()) {
            return 0L
        } else {
            val currentTimeMillis = UIUtils.getCurrentTime(mContext)
            // Rounded up number of minutes.
            return (mSessionStart - currentTimeMillis) / 60000 + 1
        }
    }

    open val isSessionReadyForFeedback: Boolean
        get() {
            val currentTimeMillis = UIUtils.getCurrentTime(mContext)
            return currentTimeMillis > mSessionEnd - SessionDetailConstants.FEEDBACK_MILLIS_BEFORE_SESSION_END_MS
        }

    open fun hasLiveStream(): Boolean {
        return mHasLiveStream || !TextUtils.isEmpty(youTubeUrl)
    }

    open fun hasFeedback(): Boolean {
        return mHasFeedback
    }

    fun hasPhotoUrl(): Boolean {
        return !TextUtils.isEmpty(photoUrl)
    }

    open val links: List<Pair<Int, Intent>>
        get() = mLinks

    open val speakers: List<Speaker>
        get() = mSpeakers

    fun hasSummaryContent(): Boolean {
        return !TextUtils.isEmpty(sessionAbstract) || !TextUtils.isEmpty(requirements)
    }

    override fun getQueries(): Array<out QueryEnum> {
        return SessionDetailQueryEnum.values()
    }

    override fun readDataFromCursor(cursor: Cursor?, query: QueryEnum): Boolean {
        var success = false

        if (cursor != null && cursor.moveToFirst()) {
            if (SessionDetailQueryEnum.SESSIONS == query) {
                readDataFromSessionCursor(cursor)
                mSessionLoaded = true
                success = true
            } else if (SessionDetailQueryEnum.TAG_METADATA == query) {
                readDataFromTagMetadataCursor(cursor)
                success = true
            } else if (SessionDetailQueryEnum.FEEDBACK == query) {
                readDataFromFeedbackCursor(cursor)
                success = true
            } else if (SessionDetailQueryEnum.SPEAKERS == query) {
                readDataFromSpeakersCursor(cursor)
                success = true
            } else if (SessionDetailQueryEnum.MY_VIEWED_VIDEOS == query) {
                readDataFromMyViewedVideosCursor(cursor)
                success = true
            }
        }

        return success
    }

    private fun readDataFromMyViewedVideosCursor(cursor: Cursor) {
        val videoID = cursor.getString(cursor.getColumnIndex(
                ScheduleContract.MyViewedVideos.VIDEO_ID))
        if (videoID != null && videoID == liveStreamId) {
            liveStreamVideoWatched = true
        }
    }

    private fun readDataFromSessionCursor(cursor: Cursor) {
        sessionTitle = cursor.getString(cursor.getColumnIndex(
                ScheduleContract.Sessions.SESSION_TITLE))

        isInSchedule = cursor.getInt(cursor.getColumnIndex(
                ScheduleContract.Sessions.SESSION_IN_MY_SCHEDULE)) != 0
        if (!mSessionLoaded) {
            isInScheduleWhenSessionFirstLoaded = isInSchedule
        }
        tagsString = cursor.getString(cursor.getColumnIndex(ScheduleContract.Sessions.SESSION_TAGS))
        isKeynote = tagsString != null && tagsString!!.contains(Config.Tags.SPECIAL_KEYNOTE)

        sessionColor = cursor.getInt(
                cursor.getColumnIndex(ScheduleContract.Sessions.SESSION_COLOR))
        if (sessionColor == 0) {
            sessionColor = mContext.resources.getColor(R.color.default_session_color)
        } else {
            sessionColor = UIUtils.setColorOpaque(sessionColor)
        }

        liveStreamId = cursor.getString(cursor.getColumnIndex(ScheduleContract.Sessions.SESSION_LIVESTREAM_ID))

        mHasLiveStream = !TextUtils.isEmpty(liveStreamId)

        youTubeUrl = cursor.getString(cursor.getColumnIndex(
                ScheduleContract.Sessions.SESSION_YOUTUBE_URL))

        mSessionStart = cursor.getLong(cursor.getColumnIndex(ScheduleContract.Sessions.SESSION_START))
        mSessionEnd = cursor.getLong(cursor.getColumnIndex(ScheduleContract.Sessions.SESSION_END))

        mRoomName = cursor.getString(cursor.getColumnIndex(ScheduleContract.Sessions.ROOM_NAME))
        mRoomId = cursor.getString(cursor.getColumnIndex(ScheduleContract.Sessions.ROOM_ID))

        hashTag = cursor.getString(cursor.getColumnIndex(ScheduleContract.Sessions.SESSION_HASHTAG))

        photoUrl = cursor.getString(
                cursor.getColumnIndex(ScheduleContract.Sessions.SESSION_PHOTO_URL))
        sessionUrl = cursor.getString(cursor.getColumnIndex(ScheduleContract.Sessions.SESSION_URL))

        sessionAbstract = cursor.getString(cursor.getColumnIndex(ScheduleContract.Sessions.SESSION_ABSTRACT))

        mSpeakersNames = cursor.getString(cursor.getColumnIndex(ScheduleContract.Sessions.SESSION_SPEAKER_NAMES))

        requirements = cursor.getString(cursor.getColumnIndex(ScheduleContract.Sessions.SESSION_REQUIREMENTS))

        formatSubtitle()

        buildLinks(cursor)
    }

    @VisibleForTesting
    fun formatSubtitle() {
        sessionSubtitle = UIUtils.formatSessionSubtitle(
                mSessionStart, mSessionEnd, mRoomName, mBuffer, mContext)
        if (mHasLiveStream) {
            sessionSubtitle += " " + UIUtils.getLiveBadgeText(mContext, mSessionStart, mSessionEnd)
        }
    }

    private fun buildLinks(cursor: Cursor) {
        mLinks.clear()

        if (hasLiveStream() && isSessionOngoing) {
            mLinks.add(Pair(
                    R.string.session_link_livestream,
                    watchLiveIntent))
        }

        if (!hasFeedback() && isSessionReadyForFeedback) {
            mLinks.add(Pair(
                    R.string.session_feedback_submitlink,
                    feedbackIntent))
        }

        for (i in LINKS_CURSOR_FIELDS.indices) {
            val linkUrl = cursor.getString(cursor.getColumnIndex(LINKS_CURSOR_FIELDS[i]))
            if (TextUtils.isEmpty(linkUrl)) {
                continue
            }

            mLinks.add(Pair(
                    LINKS_DESCRIPTION_RESOURCE_IDS[i],
                    Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl)).addFlags(flagForUrlLink)))
        }
    }

    private val flagForUrlLink: Int
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                return Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET
            } else {
                return Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            }
        }

    val watchLiveIntent: Intent
        get() {
            val youtubeLink = String.format(
                    Locale.US, Config.VIDEO_LIBRARY_URL_FMT,
                    if (TextUtils.isEmpty(liveStreamId)) "" else liveStreamId)
            return Intent(Intent.ACTION_VIEW, Uri.parse(youtubeLink))
        }

    val feedbackIntent: Intent
        get() = Intent(Intent.ACTION_VIEW, mSessionUri, mContext,
                SessionFeedbackActivity::class.java)

    private fun readDataFromTagMetadataCursor(cursor: Cursor) {
        tagMetadata = TagMetadata(cursor)
    }

    private fun readDataFromFeedbackCursor(cursor: Cursor) {
        mHasFeedback = cursor.count > 0
    }

    private fun readDataFromSpeakersCursor(cursor: Cursor) {
        mSpeakers.clear()

        // Not using while(cursor.moveToNext()) because it would lead to issues when writing tests.
        // Either we would mock cursor.moveToNext() to return true and the test would have infinite
        // loop, or we would mock cursor.moveToNext() to return false, and the test would be for an
        // empty cursor.
        val count = cursor.count
        for (i in 0..count - 1) {
            cursor.moveToPosition(i)
            val speakerName = cursor.getString(cursor.getColumnIndex(ScheduleContract.Speakers.SPEAKER_NAME))
            if (TextUtils.isEmpty(speakerName)) {
                continue
            }

            val speakerImageUrl = cursor.getString(
                    cursor.getColumnIndex(ScheduleContract.Speakers.SPEAKER_IMAGE_URL))
            val speakerCompany = cursor.getString(
                    cursor.getColumnIndex(ScheduleContract.Speakers.SPEAKER_COMPANY))
            val speakerUrl = cursor.getString(
                    cursor.getColumnIndex(ScheduleContract.Speakers.SPEAKER_URL))
            val speakerPlusoneUrl = cursor.getString(
                    cursor.getColumnIndex(ScheduleContract.Speakers.SPEAKER_PLUSONE_URL))
            val speakerTwitterUrl = cursor.getString(
                    cursor.getColumnIndex(ScheduleContract.Speakers.SPEAKER_TWITTER_URL))
            val speakerAbstract = cursor.getString(
                    cursor.getColumnIndex(ScheduleContract.Speakers.SPEAKER_ABSTRACT))

            mSpeakers.add(Speaker(speakerName, speakerImageUrl, speakerCompany, speakerUrl,
                    speakerPlusoneUrl, speakerTwitterUrl, speakerAbstract))
        }
    }

    override fun createCursorLoader(loaderId: Int, uri: Uri, args: Bundle): Loader<Cursor> {
        var loader: CursorLoader? = null

        if (loaderId == SessionDetailQueryEnum.SESSIONS.id) {
            mSessionUri = uri
            sessionId = getSessionId(uri)
            loader = getCursorLoaderInstance(mContext, uri,
                    SessionDetailQueryEnum.SESSIONS.projection, null, null, null)
        } else if (loaderId == SessionDetailQueryEnum.SPEAKERS.id && mSessionUri != null) {
            val speakersUri = getSpeakersDirUri(sessionId!!)
            loader = getCursorLoaderInstance(mContext, speakersUri,
                    SessionDetailQueryEnum.SPEAKERS.projection, null, null,
                    ScheduleContract.Speakers.DEFAULT_SORT)
        } else if (loaderId == SessionDetailQueryEnum.FEEDBACK.id) {
            val feedbackUri = getFeedbackUri(sessionId!!)
            loader = getCursorLoaderInstance(mContext, feedbackUri,
                    SessionDetailQueryEnum.FEEDBACK.projection, null, null, null)
        } else if (loaderId == SessionDetailQueryEnum.TAG_METADATA.id) {
            loader = tagMetadataLoader
        } else if (loaderId == SessionDetailQueryEnum.MY_VIEWED_VIDEOS.id) {
            LOGD(TAG, "Starting My Viewed Videos query")
            val myPlayedVideoUri = ScheduleContract.MyViewedVideos.buildMyViewedVideosUri(
                    AccountUtils.getActiveAccountName(mContext))
            loader = getCursorLoaderInstance(mContext, myPlayedVideoUri,
                    SessionDetailQueryEnum.MY_VIEWED_VIDEOS.projection, null, null, null)
        }
        return loader!!
    }

    @VisibleForTesting
    fun getCursorLoaderInstance(context: Context, uri: Uri, projection: Array<String>,
                                selection: String?, selectionArgs: Array<String>?, sortOrder: String?): CursorLoader {
        return CursorLoader(context, uri, projection, selection, selectionArgs, sortOrder)
    }

    val tagMetadataLoader: CursorLoader
        @VisibleForTesting
        get() = TagMetadata.createCursorLoader(mContext)

    @VisibleForTesting
    fun getFeedbackUri(sessionId: String): Uri {
        return ScheduleContract.Feedback.buildFeedbackUri(sessionId)
    }

    @VisibleForTesting
    fun getSpeakersDirUri(sessionId: String): Uri {
        return ScheduleContract.Sessions.buildSpeakersDirUri(sessionId)
    }

    @VisibleForTesting
    fun getSessionId(uri: Uri): String {
        return ScheduleContract.Sessions.getSessionId(uri)
    }

    override fun requestModelUpdate(action: UserActionEnum, args: Bundle?): Boolean {
        var success = false
        if (action === SessionDetailUserActionEnum.STAR) {
            isInSchedule = true
            mSessionsHelper.setSessionStarred(mSessionUri, true, null)
            amendCalendarAndSetUpNotificationIfRequired()
            success = true
            sendAnalyticsEventForStarUnstarSession(true)
        } else if (action === SessionDetailUserActionEnum.UNSTAR) {
            isInSchedule = false
            mSessionsHelper.setSessionStarred(mSessionUri, false, null)
            amendCalendarAndSetUpNotificationIfRequired()
            success = true
            sendAnalyticsEventForStarUnstarSession(false)
        } else if (action === SessionDetailUserActionEnum.SHOW_MAP) {
            // ANALYTICS EVENT: Click on Map action in Session Details page.
            // Contains: Session title/subtitle
            sendAnalyticsEvent("Session", "Map", sessionTitle!!)
            mSessionsHelper.startMapActivity(mRoomId)
            success = true
        } else if (action === SessionDetailUserActionEnum.SHOW_SHARE) {
            // On ICS+ devices, we normally won't reach this as ShareActionProvider will handle
            // sharing.
            mSessionsHelper.shareSession(mContext, R.string.share_template, sessionTitle,
                    hashTag, sessionUrl)
            success = true
        }
        return success
    }

    private fun amendCalendarAndSetUpNotificationIfRequired() {
        if (!hasSessionStarted()) {
            val intent: Intent
            if (isInSchedule) {
                intent = Intent(SessionCalendarService.ACTION_ADD_SESSION_CALENDAR,
                        mSessionUri)
                intent.putExtra(SessionCalendarService.EXTRA_SESSION_START,
                        mSessionStart)
                intent.putExtra(SessionCalendarService.EXTRA_SESSION_END,
                        mSessionEnd)
                intent.putExtra(SessionCalendarService.EXTRA_SESSION_ROOM, mRoomName)
                intent.putExtra(SessionCalendarService.EXTRA_SESSION_TITLE, sessionTitle)
            } else {
                intent = Intent(SessionCalendarService.ACTION_REMOVE_SESSION_CALENDAR,
                        mSessionUri)
                intent.putExtra(SessionCalendarService.EXTRA_SESSION_START,
                        mSessionStart)
                intent.putExtra(SessionCalendarService.EXTRA_SESSION_END,
                        mSessionEnd)
                intent.putExtra(SessionCalendarService.EXTRA_SESSION_TITLE, sessionTitle)
            }
            intent.setClass(mContext, SessionCalendarService::class.java)
            mContext.startService(intent)

            if (isInSchedule) {
                setUpNotification()
            }
        }
    }

    private fun setUpNotification() {
        var scheduleIntent: Intent

        // Schedule session notification
        if (!hasSessionStarted()) {
            LOGD(TAG, "Scheduling notification about session start.")
            scheduleIntent = Intent(
                    SessionAlarmService.ACTION_SCHEDULE_STARRED_BLOCK,
                    null, mContext, SessionAlarmService::class.java)
            scheduleIntent.putExtra(SessionAlarmService.EXTRA_SESSION_START, mSessionStart)
            scheduleIntent.putExtra(SessionAlarmService.EXTRA_SESSION_END, mSessionEnd)
            mContext.startService(scheduleIntent)
        } else {
            LOGD(TAG, "Not scheduling notification about session start, too late.")
        }

        // Schedule feedback notification
        if (!hasSessionEnded()) {
            LOGD(TAG, "Scheduling notification about session feedback.")
            scheduleIntent = Intent(
                    SessionAlarmService.ACTION_SCHEDULE_FEEDBACK_NOTIFICATION,
                    null, mContext, SessionAlarmService::class.java)
            scheduleIntent.putExtra(SessionAlarmService.EXTRA_SESSION_ID, sessionId)
            scheduleIntent.putExtra(SessionAlarmService.EXTRA_SESSION_START, mSessionStart)
            scheduleIntent.putExtra(SessionAlarmService.EXTRA_SESSION_END, mSessionEnd)
            scheduleIntent.putExtra(SessionAlarmService.EXTRA_SESSION_TITLE, sessionTitle)
            mContext.startService(scheduleIntent)
        } else {
            LOGD(TAG, "Not scheduling feedback notification, too late.")
        }
    }

    @VisibleForTesting
    fun sendAnalyticsEvent(category: String, action: String, label: String) {
        AnalyticsHelper.sendEvent(category, action, label)
    }

    private fun sendAnalyticsEventForStarUnstarSession(starred: Boolean) {
        // ANALYTICS EVENT: Add or remove a session from My Schedule (starred vs unstarred)
        // Contains: Session title, whether it was added or removed (starred or unstarred)
        sendAnalyticsEvent("Session", if (starred) "Starred" else "Unstarred", sessionTitle!!)
    }

    class Speaker(val name: String, val imageUrl: String, val company: String, val url: String, val plusoneUrl: String,
                  val twitterUrl: String, val abstract: String)

    enum class SessionDetailQueryEnum private constructor(private val id: Int, private val projection: Array<String>?) : QueryEnum {
        SESSIONS(0, arrayOf(ScheduleContract.Sessions.SESSION_START, ScheduleContract.Sessions.SESSION_END, ScheduleContract.Sessions.SESSION_LEVEL, ScheduleContract.Sessions.SESSION_TITLE, ScheduleContract.Sessions.SESSION_ABSTRACT, ScheduleContract.Sessions.SESSION_REQUIREMENTS, ScheduleContract.Sessions.SESSION_IN_MY_SCHEDULE, ScheduleContract.Sessions.SESSION_HASHTAG, ScheduleContract.Sessions.SESSION_URL, ScheduleContract.Sessions.SESSION_YOUTUBE_URL, ScheduleContract.Sessions.SESSION_PDF_URL, ScheduleContract.Sessions.SESSION_NOTES_URL, ScheduleContract.Sessions.SESSION_LIVESTREAM_ID, ScheduleContract.Sessions.SESSION_MODERATOR_URL, ScheduleContract.Sessions.ROOM_ID, ScheduleContract.Rooms.ROOM_NAME, ScheduleContract.Sessions.SESSION_COLOR, ScheduleContract.Sessions.SESSION_PHOTO_URL, ScheduleContract.Sessions.SESSION_RELATED_CONTENT, ScheduleContract.Sessions.SESSION_TAGS, ScheduleContract.Sessions.SESSION_SPEAKER_NAMES)),
        SPEAKERS(1, arrayOf(ScheduleContract.Speakers.SPEAKER_NAME, ScheduleContract.Speakers.SPEAKER_IMAGE_URL, ScheduleContract.Speakers.SPEAKER_COMPANY, ScheduleContract.Speakers.SPEAKER_ABSTRACT, ScheduleContract.Speakers.SPEAKER_URL, ScheduleContract.Speakers.SPEAKER_PLUSONE_URL, ScheduleContract.Speakers.SPEAKER_TWITTER_URL)),
        FEEDBACK(2, arrayOf(ScheduleContract.Feedback.SESSION_ID)),
        TAG_METADATA(3, null),
        MY_VIEWED_VIDEOS(4, arrayOf(ScheduleContract.MyViewedVideos.VIDEO_ID));

        override fun getId(): Int {
            return id
        }

        override fun getProjection(): Array<String> {
            return projection!!
        }

    }

    enum class SessionDetailUserActionEnum private constructor(private val id: Int) : UserActionEnum {
        STAR(1),
        UNSTAR(2),
        SHOW_MAP(3),
        SHOW_SHARE(4);

        override fun getId(): Int {
            return id
        }

    }

    companion object {

        protected val TAG = makeLogTag(SessionDetailModel::class.java)

        /**
         * The cursor fields for the links. The corresponding resource ids for the links descriptions
         * are in  [.LINKS_DESCRIPTION_RESOURCE_IDS].
         */
        private val LINKS_CURSOR_FIELDS = arrayOf(ScheduleContract.Sessions.SESSION_YOUTUBE_URL, ScheduleContract.Sessions.SESSION_MODERATOR_URL, ScheduleContract.Sessions.SESSION_PDF_URL, ScheduleContract.Sessions.SESSION_NOTES_URL)

        /**
         * The resource ids for the links descriptions. The corresponding cursor fields for the links
         * are in [.LINKS_CURSOR_FIELDS].
         */
        private val LINKS_DESCRIPTION_RESOURCE_IDS = intArrayOf(R.string.session_link_youtube, R.string.session_link_moderator, R.string.session_link_pdf, R.string.session_link_notes)
    }
}