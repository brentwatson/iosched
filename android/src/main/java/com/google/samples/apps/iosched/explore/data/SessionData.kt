package com.google.samples.apps.iosched.explore.data

import android.content.Context
import android.text.TextUtils
import com.google.samples.apps.iosched.util.UIUtils
import java.util.*

/**
 * This represent a Session that is pulled from the schedule.
 */
class SessionData {
    var sessionName: String? = null
        private set
    var details: String? = null
    var sessionId: String? = null
        private set
    var imageUrl: String? = null
        private set
    var mainTag: String? = null
        private set
    var startDate: Date? = null
        private set
    var endDate: Date? = null
        private set
    var liveStreamId: String? = null
        private set
    var youTubeUrl: String? = null
        private set
    var tags: String? = null
        private set
    var isInSchedule: Boolean = false
        private set

    constructor() {
    }

    constructor(sessionName: String, details: String, sessionId: String, imageUrl: String,
                mainTag: String, startTime: Long, endTime: Long, liveStreamId: String,
                youTubeUrl: String, tags: String, inSchedule: Boolean) {
        updateData(sessionName, details, sessionId, imageUrl, mainTag, startTime, endTime,
                liveStreamId, youTubeUrl, tags, inSchedule)
    }

    fun updateData(sessionName: String, details: String, sessionId: String, imageUrl: String,
                   mainTag: String, startTime: Long, endTime: Long, liveStreamId: String,
                   youTubeUrl: String, tags: String, inSchedule: Boolean) {
        this.sessionName = sessionName
        this.details = details
        this.sessionId = sessionId
        this.imageUrl = imageUrl
        this.mainTag = mainTag
        try {
            startDate = java.util.Date(startTime)
        } catch (ignored: Exception) {
        }

        try {
            endDate = java.util.Date(endTime)
        } catch (ignored: Exception) {
        }

        this.liveStreamId = liveStreamId
        this.youTubeUrl = youTubeUrl
        this.tags = tags
        isInSchedule = inSchedule
    }

    /**
     * Return whether this is a LiveStreamed session and whether it is happening right now.
     */
    fun isLiveStreamNow(context: Context): Boolean {
        if (!isLiveStreamAvailable) {
            return false
        }
        if (startDate == null || endDate == null) {
            return false
        }
        val now = java.util.Calendar.getInstance()
        now.timeInMillis = UIUtils.getCurrentTime(context)
        if (startDate!!.before(now.time) && endDate!!.after(now.time)) {
            return true
        } else {
            return false
        }
    }

    val isLiveStreamAvailable: Boolean
        get() = !TextUtils.isEmpty(liveStreamId)

    val isVideoAvailable: Boolean
        get() = !TextUtils.isEmpty(youTubeUrl)
}
