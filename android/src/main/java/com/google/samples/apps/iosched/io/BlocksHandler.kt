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

package com.google.samples.apps.iosched.io

import android.content.ContentProviderOperation
import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.samples.apps.iosched.io.model.Block
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.provider.ScheduleContractHelper
import com.google.samples.apps.iosched.util.LogUtils.LOGW
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.google.samples.apps.iosched.util.ParserUtils
import java.util.*


class BlocksHandler(context: Context) : JSONHandler(context) {
    private val mBlocks = ArrayList<Block>()

    override fun makeContentProviderOperations(list: ArrayList<ContentProviderOperation>) {
        val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                ScheduleContract.Blocks.CONTENT_URI)
        list.add(ContentProviderOperation.newDelete(uri).build())
        for (block in mBlocks) {
            outputBlock(block, list)
        }
    }

    override fun process(element: JsonElement) {
        for (block in Gson().fromJson(element, Array<Block>::class.java)) {
            mBlocks.add(block)
        }
    }

    companion object {
        private val TAG = makeLogTag(BlocksHandler::class.java)

        private fun outputBlock(block: Block, list: ArrayList<ContentProviderOperation>) {
            val uri = ScheduleContractHelper.setUriAsCalledFromSyncAdapter(
                    ScheduleContract.Blocks.CONTENT_URI)
            val builder = ContentProviderOperation.newInsert(uri)
            val title = if (block.title != null) block.title else ""
            val meta = if (block.subtitle != null) block.subtitle else ""

            var type = block.type
            if (!ScheduleContract.Blocks.isValidBlockType(type)) {
                LOGW(TAG, "block from " + block.start + " to " + block.end + " has unrecognized type ("
                        + type + "). Using " + ScheduleContract.Blocks.BLOCK_TYPE_BREAK + " instead.")
                type = ScheduleContract.Blocks.BLOCK_TYPE_BREAK
            }

            val startTimeL = ParserUtils.parseTime(block.start)
            val endTimeL = ParserUtils.parseTime(block.end)
            val blockId = ScheduleContract.Blocks.generateBlockId(startTimeL, endTimeL)
            builder.withValue(ScheduleContract.Blocks.BLOCK_ID, blockId)
            builder.withValue(ScheduleContract.Blocks.BLOCK_TITLE, title)
            builder.withValue(ScheduleContract.Blocks.BLOCK_START, startTimeL)
            builder.withValue(ScheduleContract.Blocks.BLOCK_END, endTimeL)
            builder.withValue(ScheduleContract.Blocks.BLOCK_TYPE, type)
            builder.withValue(ScheduleContract.Blocks.BLOCK_SUBTITLE, meta)
            list.add(builder.build())
        }
    }
}
