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

import android.content.Context
import android.content.CursorLoader
import android.database.Cursor
import android.provider.BaseColumns
import android.text.TextUtils

import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.framework.QueryEnum
import com.google.samples.apps.iosched.provider.ScheduleContract

import java.util.*

open class TagMetadata {

    // List of tags in each category, sorted by the category sort order.
    private val mTagsInCategory = HashMap<String, ArrayList<Tag>>()

    // Hash map from tag ID to tag.
    private val mTagsById = HashMap<String, Tag>()

    protected constructor() {
    }

    constructor(cursor: Cursor) {
        // Not using while(cursor.moveToNext()) because it would lead to issues when writing tests.
        // Either we would mock cursor.moveToNext() to return true and the test would have infinite
        // loop, or we would mock cursor.moveToNext() to return false, and the test would be for an
        // empty cursor.
        val count = cursor.count
        for (i in 0..count - 1) {
            cursor.moveToPosition(i)
            val tag = Tag(cursor.getString(cursor.getColumnIndex(ScheduleContract.Tags.TAG_ID)),
                    cursor.getString(cursor.getColumnIndex(ScheduleContract.Tags.TAG_NAME)),
                    cursor.getString(cursor.getColumnIndex(ScheduleContract.Tags.TAG_CATEGORY)),
                    cursor.getInt(cursor.getColumnIndex(ScheduleContract.Tags.TAG_ORDER_IN_CATEGORY)),
                    cursor.getString(cursor.getColumnIndex(ScheduleContract.Tags.TAG_ABSTRACT)),
                    cursor.getInt(cursor.getColumnIndex(ScheduleContract.Tags.TAG_COLOR)))
            mTagsById.put(tag.id, tag)
            if (!mTagsInCategory.containsKey(tag.category)) {
                mTagsInCategory.put(tag.category, ArrayList<Tag>())
            }
            mTagsInCategory[tag.category]!!.add(tag)
        }

        for (list in mTagsInCategory.values) {
            Collections.sort(list)
        }
    }

    open fun getTag(tagId: String): Tag? {
        return if (mTagsById.containsKey(tagId)) mTagsById[tagId] else null
    }

    fun getTagsInCategory(category: String): List<Tag>? {
        return if (mTagsInCategory.containsKey(category))
            Collections.unmodifiableList(mTagsInCategory[category])
        else
            null
    }

    /** Given the set of tags on a session, returns its group label.  */
    fun getSessionGroupTag(sessionTags: Array<String>): Tag {
        var bestOrder = Integer.MAX_VALUE
        var bestTag: Tag? = null
        for (tagId in sessionTags) {
            val tag = getTag(tagId)
            if (tag != null && Config.Tags.SESSION_GROUPING_TAG_CATEGORY == tag.category &&
                    tag.orderInCategory < bestOrder) {
                bestOrder = tag.orderInCategory
                bestTag = tag
            }
        }
        return bestTag!!
    }

    enum class TagsQueryEnum private constructor(private val id: Int, private val projection: Array<String>) : QueryEnum {
        TAG(0, arrayOf(BaseColumns._ID, ScheduleContract.Tags.TAG_ID, ScheduleContract.Tags.TAG_NAME, ScheduleContract.Tags.TAG_CATEGORY, ScheduleContract.Tags.TAG_ORDER_IN_CATEGORY, ScheduleContract.Tags.TAG_ABSTRACT, ScheduleContract.Tags.TAG_COLOR));

        override fun getId(): Int {
            return id
        }

        override fun getProjection(): Array<String> {
            return projection
        }
    }

    class Tag(val id: String, val name: String, val category: String, val orderInCategory: Int, val abstract: String,
              val color: Int) : Comparable<Tag> {

        override fun compareTo(another: Tag): Int {
            return orderInCategory - another.orderInCategory
        }
    }

    companion object {

        fun createCursorLoader(context: Context): CursorLoader {
            return CursorLoader(context, ScheduleContract.Tags.CONTENT_URI,
                    TagsQueryEnum.TAG.projection, null, null, null)
        }

        var TAG_DISPLAY_ORDER_COMPARATOR: Comparator<Tag> = Comparator { tag, tag2 ->
            if (!TextUtils.equals(tag.category, tag2.category)) {
                return@Comparator Config.Tags.CATEGORY_DISPLAY_ORDERS[tag.category]!! - Config.Tags.CATEGORY_DISPLAY_ORDERS[tag2.category]!!
            } else if (tag.orderInCategory != tag2.orderInCategory) {
                return@Comparator tag.orderInCategory - tag2.orderInCategory
            }

            tag.name.compareTo(tag2.name)
        }
    }
}
