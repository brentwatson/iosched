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

import java.util.*

object ScheduleItemHelper {

    private val FREE_BLOCK_MINIMUM_LENGTH = 10 * 60 * 1000.toLong() // 10 minutes
    val ALLOWED_OVERLAP = 5 * 60 * 1000.toLong() // 5 minutes

    /**
     * Find and resolve time slot conflicts.
     * Items should already be ordered by start time. Conflicts among mutableItems, if any,
     * won't be checked, and they will be left as is.
     */
    fun processItems(mutableItems: ArrayList<ScheduleItem>, immutableItems: ArrayList<ScheduleItem>): ArrayList<ScheduleItem> {

        // move mutables as necessary to accommodate conflicts with immutables:
        moveMutables(mutableItems, immutableItems)

        // mark conflicting immutable:
        markConflicting(immutableItems)

        val result = ArrayList<ScheduleItem>()
        result.addAll(immutableItems)
        result.addAll(mutableItems)

        Collections.sort(result) { lhs, rhs -> if (lhs.startTime < rhs.startTime) -1 else 1 }

        return result
    }

    fun markConflicting(items: ArrayList<ScheduleItem>) {
        for (i in items.indices) {
            val item = items[i]
            // Notice that we only care about sessions when checking conflicts.
            if (item.type == ScheduleItem.SESSION)
                for (j in i + 1..items.size - 1) {
                    val other = items[j]
                    if (item.type == ScheduleItem.SESSION) {
                        if (intersect(other, item, true)) {
                            other.flags = other.flags or ScheduleItem.FLAG_CONFLICTS_WITH_PREVIOUS
                            item.flags = item.flags or ScheduleItem.FLAG_CONFLICTS_WITH_NEXT
                        } else {
                            // we assume the list is ordered by starttime
                            break
                        }
                    }
                }
        }
    }

    fun moveMutables(mutableItems: ArrayList<ScheduleItem>, immutableItems: ArrayList<ScheduleItem>) {
        val immutableIt = immutableItems.iterator()

        while (immutableIt.hasNext()) {
            val immutableItem = immutableIt.next()
            if (immutableItem.type == ScheduleItem.BREAK) {
                // Breaks (lunch, after hours, etc) should not make free blocks to move
                continue
            }
            val mutableIt = mutableItems.listIterator()
            while (mutableIt.hasNext()) {
                val mutableItem = mutableIt.next()
                var split: ScheduleItem? = null

                // If mutable item is overlapping the immutable one
                if (intersect(immutableItem, mutableItem, true)) {
                    if (isContainedInto(mutableItem, immutableItem)) {
                        // if mutable is entirely contained into immutable, just remove it
                        mutableIt.remove()
                        continue
                    } else if (isContainedInto(immutableItem, mutableItem)) {
                        // if immutable is entirely contained into mutable, split mutable if necessary:
                        if (isIntervalLongEnough(immutableItem.endTime, mutableItem.endTime)) {
                            split = mutableItem.clone() as ScheduleItem
                            split.startTime = immutableItem.endTime
                        }
                        mutableItem.endTime = immutableItem.startTime
                    } else if (mutableItem.startTime < immutableItem.endTime) {
                        // Adjust the start of the mutable
                        mutableItem.startTime = immutableItem.endTime
                    } else if (mutableItem.endTime > immutableItem.startTime) {
                        // Adjust the end of the mutable
                        mutableItem.endTime = immutableItem.startTime
                    }

                    if (!isIntervalLongEnough(mutableItem.startTime, mutableItem.endTime)) {
                        mutableIt.remove()
                    }
                    if (split != null) {
                        mutableIt.add(split)
                    }
                }
            }
        }

    }

    private fun isIntervalLongEnough(start: Long, end: Long): Boolean {
        return end - start >= FREE_BLOCK_MINIMUM_LENGTH
    }

    private fun intersect(block1: ScheduleItem, block2: ScheduleItem, useOverlap: Boolean): Boolean {
        return block2.endTime > block1.startTime + (if (useOverlap) ALLOWED_OVERLAP else 0) && block2.startTime + (if (useOverlap) ALLOWED_OVERLAP else 0) < block1.endTime
    }

    private fun isContainedInto(contained: ScheduleItem, container: ScheduleItem): Boolean {
        return contained.startTime >= container.startTime && contained.endTime <= container.endTime
    }


}
