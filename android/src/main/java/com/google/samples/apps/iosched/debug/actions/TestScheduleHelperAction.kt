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

package com.google.samples.apps.iosched.debug.actions

import android.content.Context
import android.os.AsyncTask
import com.google.samples.apps.iosched.debug.DebugAction
import com.google.samples.apps.iosched.model.ScheduleItem
import com.google.samples.apps.iosched.model.ScheduleItemHelper
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * A DebugAction that tests a few cases of schedule conflicts.
 */
class TestScheduleHelperAction : DebugAction {

    internal var out = StringBuilder()

    override fun run(context: Context, callback: DebugAction.Callback) {
        object : AsyncTask<Context, Void, Boolean>() {
            override fun doInBackground(vararg contexts: Context): Boolean? {
                return startTest()
            }

            override fun onPostExecute(success: Boolean?) {
                callback.done(success!!, out.toString())
            }
        }.execute(context)
    }

    override val label: String
        get() = "Test scheduler conflict handling"


    fun startTest(): Boolean? {
        var success = true
        val mut = ArrayList<ScheduleItem>()
        val immut = ArrayList<ScheduleItem>()

        mut.add(item("14:00", "14:30", "m1"))
        immut.add(item("14:25", "14:50", "i1"))
        success = success and check("no intersection - within range",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("14:00", "14:30", "m1"), item("14:25", "14:50", "i1")))

        mut.clear()
        immut.clear()
        mut.add(item("14:00", "16:00", "m1"))
        immut.add(item("15:00", "16:00", "i1"))
        success = success and check("Simple intersection1",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("14:00", "15:00", "m1"), item("15:00", "16:00", "i1")))

        mut.clear()
        immut.clear()
        mut.add(item("14:00", "16:00", "m1"))
        immut.add(item("13:00", "15:00", "i1"))
        success = success and check("Simple intersection2",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("13:00", "15:00", "i1"), item("15:00", "16:00", "m1")))

        mut.clear()
        immut.clear()
        mut.add(item("14:00", "16:00", "m1"))
        immut.add(item("14:00", "16:00", "i1"))
        success = success and check("same time",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("14:00", "16:00", "i1")))

        mut.clear()
        immut.clear()
        mut.add(item("14:00", "16:09", "m1"))
        immut.add(item("14:05", "16:00", "i1"))
        success = success and check("no split, remaining not big enough",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("14:05", "16:00", "i1")))

        mut.clear()
        immut.clear()
        mut.add(item("14:00", "16:10", "m1"))
        immut.add(item("14:00", "16:00", "i1"))
        success = success and check("split",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("14:00", "16:00", "i1"), item("16:00", "16:10", "m1")))

        mut.clear()
        immut.clear()
        mut.add(item("14:00", "17:00", "m1"))
        immut.add(item("14:30", "15:00", "i1"))
        immut.add(item("15:30", "16:00", "i2"))
        success = success and check("2 splits",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("14:00", "14:30", "m1"), item("14:30", "15:00", "i1"), item("15:00", "15:30", "m1"), item("15:30", "16:00", "i2"), item("16:00", "17:00", "m1")))

        mut.clear()
        immut.clear()
        mut.add(item("14:00", "17:00", "m1"))
        immut.add(item("14:30", "15:00", "i1"))
        immut.add(item("16:30", "16:51", "i2"))
        success = success and check("2 splits with no remaining",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("14:00", "14:30", "m1"), item("14:30", "15:00", "i1"), item("15:00", "16:30", "m1"), item("16:30", "16:51", "i2")))

        mut.clear()
        immut.clear()
        mut.add(item("12:00", "15:00", "m1"))
        mut.add(item("15:00", "17:00", "m2"))
        mut.add(item("17:00", "17:40", "m3"))
        immut.add(item("14:30", "15:00", "i1"))
        immut.add(item("16:30", "16:51", "i2"))
        success = success and check("2 splits, 3 free blocks, no remaining",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("12:00", "14:30", "m1"), item("14:30", "15:00", "i1"), item("15:00", "16:30", "m2"), item("16:30", "16:51", "i2"), item("17:00", "17:40", "m3")))

        mut.clear()
        immut.clear()
        mut.add(item("12:00", "15:00", "m1"))
        mut.add(item("15:00", "17:00", "m2"))
        mut.add(item("17:00", "17:40", "m3"))
        immut.add(item("14:30", "15:00", "i1"))
        immut.add(item("16:30", "16:51", "i2"))
        immut.add(item("16:30", "16:40", "i3"))
        success = success and check("conflicting sessions, 2 splits, 3 free blocks, no remaining",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("12:00", "14:30", "m1"), item("14:30", "15:00", "i1"), item("15:00", "16:30", "m2"), item("16:30", "16:51", "i2"), item("16:30", "16:40", "i3", true), item("17:00", "17:40", "m3")))


        mut.clear()
        immut.clear()
        mut.add(item("12:00", "15:00", "m1"))
        mut.add(item("15:00", "17:00", "m2"))
        mut.add(item("17:00", "17:40", "m3"))
        immut.add(item("14:30", "15:00", "i1"))
        immut.add(item("16:30", "16:51", "i2"))
        immut.add(item("16:50", "17:00", "i3"))
        success = success and check("borderline conflicting sessions, 2 splits, 3 free blocks, no remaining",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("12:00", "14:30", "m1"), item("14:30", "15:00", "i1"), item("15:00", "16:30", "m2"), item("16:30", "16:51", "i2"), item("16:50", "17:00", "i3"), item("17:00", "17:40", "m3")))


        mut.clear()
        immut.clear()
        immut.add(item("14:30", "15:00", "i1"))
        immut.add(item("16:30", "19:00", "i2"))
        immut.add(item("16:30", "17:00", "i3"))
        immut.add(item("18:00", "18:30", "i4"))
        success = success and check("conflicting sessions",
                ScheduleItemHelper.processItems(mut, immut),
                arrayOf(item("14:30", "15:00", "i1"), item("16:30", "19:00", "i2"), item("16:30", "17:00", "i3", true), item("18:00", "18:30", "i4", true)))

        return success
    }

    private fun check(testDescription: String, actual: ArrayList<ScheduleItem>, expected: Array<ScheduleItem>): Boolean {
        out.append("testing $testDescription...")
        var equal = true
        if (actual.size != expected.size) {
            equal = false
        } else {
            var i = 0
            for (item in actual) {
                if (item.title != expected[i].title ||
                        item.startTime != expected[i].startTime ||
                        item.endTime != expected[i].endTime ||
                        item.flags and ScheduleItem.FLAG_CONFLICTS_WITH_PREVIOUS != expected[i].flags and ScheduleItem.FLAG_CONFLICTS_WITH_PREVIOUS) {
                    equal = false
                    break
                }
                i++
            }
        }
        if (!equal) {
            out.append("ERROR!:\n")
            out.append("       expected\n")
            for (item in expected) {
                out.append("  " + format(item) + "\n")
            }
            out.append("       actual\n")
            for (item in actual) {
                out.append("  " + format(item) + "\n")
            }
        } else {
            out.append("OK\n")
        }
        return equal
    }

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun format(item: ScheduleItem): String {
        return item.title + "  " + timeStr(item.startTime) + "-" + timeStr(item.endTime) +
                if (item.flags and ScheduleItem.FLAG_CONFLICTS_WITH_PREVIOUS > 0) "  conflict" else ""
    }

    private fun timeStr(time: Long): String {
        val d = Date(time)
        return "" + d.hours + ":" + d.minutes
    }

    private fun date(hourMinute: String): Long {
        try {
            return sdf.parse("2014-06-25 $hourMinute:00").time
        } catch (ex: ParseException) {
            throw RuntimeException(ex)
        }

    }

    private fun item(start: String, end: String, id: String, type: Int): ScheduleItem {
        return item(start, end, id, false, type)
    }

    private fun item(start: String, end: String, id: String, conflict: Boolean = false, type: Int = ScheduleItem.SESSION): ScheduleItem {
        val i = ScheduleItem()
        i.title = id
        i.startTime = date(start)
        i.endTime = date(end)
        i.type = type
        if (conflict) i.flags = ScheduleItem.FLAG_CONFLICTS_WITH_PREVIOUS
        return i
    }

}
