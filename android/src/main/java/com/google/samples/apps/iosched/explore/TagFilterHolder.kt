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

import android.os.Parcel
import android.os.Parcelable
import com.google.samples.apps.iosched.Config
import java.util.*

/**
 * Class responsible for storing, managing and retrieving Tag filters used in
 * [ExploreSessionsActivity].
 */
class TagFilterHolder internal constructor() : Parcelable {

    private val mSelectedFilters: MutableSet<String>
    private val mCategories: IntArray
    /**
     * @return Returns whether a live streamed sessions shown be shown.
     */
    /**
     * @param show Set a boolean to indicate whether live streamed sessions should be shown
     */
    var isShowLiveStreamedSessions: Boolean = false

    init {
        mSelectedFilters = HashSet<String>()
        mCategories = IntArray(3)
        mCategories[CATEGORY_THEME] = 0
        mCategories[CATEGORY_TYPE] = 0
        mCategories[CATEGORY_TOPIC] = 0
    }

    /**
     * @param tagId The tagId to check in the filter
     * *
     * @return boolean Return a boolean indicating that the tagId is present.
     */
    operator fun contains(tagId: String): Boolean {
        return mSelectedFilters.contains(tagId)
    }

    /**
     * Add a tagId to the set of filters. Use the category to update the count of
     * the specific category.

     * @param tagId The tagId to be included in the filter.
     * *
     * @param category The category associated with the given tagId.
     * *
     * @return boolean Returns a boolean to indicate whether the operation was successful.
     */
    fun add(tagId: String, category: String): Boolean {
        val added = mSelectedFilters.add(tagId)
        if (added) {
            mCategories[categoryId(category)]++
        }
        return added
    }

    /**

     * @param tagId Tag to be remove from the filter set.
     * *
     * @param category The category of the tag being removed.
     * *
     * @return boolean Returns a boolean to indicate whether the operation was successful.
     */
    fun remove(tagId: String, category: String): Boolean {
        val removed = mSelectedFilters.remove(tagId)
        if (removed) {
            mCategories[categoryId(category)]--
        }
        return removed
    }

    /**
     * @return String[] containing all the tags from all the categories.
     */
    fun toStringArray(): Array<String> {
        return mSelectedFilters.toTypedArray()
    }

    /**
     * @return An unmodifiable set with all the filters.
     */
    val selectedFilters: Set<String>
        get() = Collections.unmodifiableSet(mSelectedFilters)

    /**
     * Method that returns the number of categories that are in use by this instance.
     * At least 1 and at most 3 categories can be returned by this method.
     *
     *
     * Example:
     * 1. If there are 2 topics and 1 theme the result would be 2 indicating
     * that two categories are in use by this filter.
     * 2. If there are 2 topics, 2 themes and 3 types then the result would be 3 to indicate
     * the non-zero presence of each category.

     * @return categoryCount Return the number of non categories in this instance.
     */
    val categoryCount: Int
        get() = Math.max(1,
                (if (mCategories[CATEGORY_THEME] > 0) 1 else 0) +
                        (if (mCategories[CATEGORY_TYPE] > 0) 1 else 0) +
                        if (mCategories[CATEGORY_TOPIC] > 0) 1 else 0)

    /**
     * @return Returns whether the collection is empty
     */
    val isEmpty: Boolean
        get() = mSelectedFilters.isEmpty()

    /**
     * @return Returns the number of filters currently in use.
     */
    fun size(): Int {
        return mSelectedFilters.size
    }

    /**

     * @param category The category to look up.
     * *
     * @return Return the number of entries for the given category.
     */
    fun getCountByCategory(category: String): Int {
        return mCategories[categoryId(category)]
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeStringArray(mSelectedFilters.toTypedArray())
        dest.writeIntArray(mCategories)
        dest.writeInt(if (isShowLiveStreamedSessions) 1 else 0)
    }

    companion object {

        val CATEGORY_THEME = 0
        val CATEGORY_TYPE = 1
        val CATEGORY_TOPIC = 2

        private fun categoryId(category: String): Int {
            when (category) {
                Config.Tags.CATEGORY_THEME -> return TagFilterHolder.CATEGORY_THEME
                Config.Tags.CATEGORY_TYPE -> return TagFilterHolder.CATEGORY_TYPE
                Config.Tags.CATEGORY_TOPIC -> return TagFilterHolder.CATEGORY_TOPIC
                else -> throw IllegalArgumentException("Invalid category " + category)
            }
        }

        @JvmStatic
        val CREATOR: Parcelable.Creator<TagFilterHolder> = object : Parcelable.Creator<TagFilterHolder> {

            override fun createFromParcel(`in`: Parcel): TagFilterHolder {
                val holder = TagFilterHolder()

                val filters = `in`.createStringArray()
                `in`.readStringArray(filters)
                Collections.addAll(holder.mSelectedFilters, *filters)

                val categories = `in`.createIntArray()
                `in`.readIntArray(categories)
                System.arraycopy(categories, 0, holder.mCategories, 0, categories.size)

                holder.isShowLiveStreamedSessions = `in`.readInt() == 1

                return holder
            }

            override fun newArray(size: Int): Array<TagFilterHolder?> {
                return arrayOfNulls(size)
            }
        }
    }
}
