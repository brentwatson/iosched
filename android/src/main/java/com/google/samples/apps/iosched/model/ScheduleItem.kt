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
package com.google.samples.apps.iosched.model

import com.google.samples.apps.iosched.provider.ScheduleContract

class ScheduleItem : Cloneable, Comparable<ScheduleItem> {

    // item type
    var type = FREE

    // session type
    var sessionType = SESSION_TYPE_MISC

    // main tag
    var mainTag: String? = null

    // start and end time for this item
    var startTime: Long = 0
    var endTime: Long = 0

    // number of sessions available in this block (usually for free blocks)
    var numOfSessions = 0

    // session id
    var sessionId = ""

    // title and subtitle
    var title = ""
    var subtitle = ""
    var room: String? = null

    // has feedback been given on this session?
    var hasGivenFeedback: Boolean = false

    // background image URL
    var backgroundImageUrl = ""
    var backgroundColor = 0

    // flags
    var flags = 0

    fun setTypeFromBlockType(blockType: String) {
        if (!ScheduleContract.Blocks.isValidBlockType(blockType) || ScheduleContract.Blocks.BLOCK_TYPE_FREE == blockType) {
            type = FREE
        } else {
            type = BREAK
        }
    }

    public override fun clone(): Any {
        try {
            return super.clone()
        } catch (unused: CloneNotSupportedException) {
            // does not happen (since we implement Cloneable)
            return ScheduleItem()
        }

    }

    override fun compareTo(another: ScheduleItem): Int {
        return if (this.startTime < another.startTime)
            -1
        else
            if (this.startTime > another.startTime) 1 else 0
    }

    override fun equals(o: Any?): Boolean {
        if (o == null || o !is ScheduleItem) {
            return false
        }
        return type == o.type &&
                sessionId == o.sessionId &&
                startTime == o.startTime &&
                endTime == o.endTime
    }

    override fun toString(): String {
        return String.format("[item type=%d, startTime=%d, endTime=%d, title=%s, subtitle=%s, flags=%d]",
                type, startTime, endTime, title, subtitle, flags)
    }

    companion object {
        // types:
        val FREE = 0  // a free chunk of time
        val SESSION = 1 // a session
        val BREAK = 2 // a break (lunch, breaks, after-hours party)

        // session types:
        val SESSION_TYPE_SESSION = 1
        val SESSION_TYPE_CODELAB = 2
        val SESSION_TYPE_BOXTALK = 3
        val SESSION_TYPE_MISC = 4
        val FLAG_HAS_LIVESTREAM = 0x01
        val FLAG_NOT_REMOVABLE = 0x02
        val FLAG_CONFLICTS_WITH_PREVIOUS = 0x04
        val FLAG_CONFLICTS_WITH_NEXT = 0x08
    }
}
