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

/*
 * Modifications:
 * -Imported from AOSP frameworks/base/core/java/com/android/internal/content
 * -Changed package name
 */

package com.google.samples.apps.iosched.util

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGV
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.util.*

/**
 * Helper for building selection clauses for [SQLiteDatabase]. Each
 * appended clause is combined using `AND`. This class is *not*
 * thread safe.
 */
class SelectionBuilder {

    private var mTable: String? = null
    private val mProjectionMap = HashMap<String, String>()
    private val mSelection = StringBuilder()
    private val mSelectionArgs = ArrayList<String>()
    private var mGroupBy: String? = null
    private var mHaving: String? = null

    /**
     * Reset any internal state, allowing this builder to be recycled.
     */
    fun reset(): SelectionBuilder {
        mTable = null
        mGroupBy = null
        mHaving = null
        mSelection.setLength(0)
        mSelectionArgs.clear()
        return this
    }

    /**
     * Append the given selection clause to the internal state. Each clause is
     * surrounded with parenthesis and combined using `AND`.
     */
    fun where(selection: String, vararg selectionArgs: String): SelectionBuilder {
        if (TextUtils.isEmpty(selection)) {
            if (selectionArgs != null && selectionArgs.size > 0) {
                throw IllegalArgumentException(
                        "Valid selection required when including arguments=")
            }

            // Shortcut when clause is empty
            return this
        }

        if (mSelection.length > 0) {
            mSelection.append(" AND ")
        }

        mSelection.append("(").append(selection).append(")")
        if (selectionArgs != null) {
            Collections.addAll(mSelectionArgs, *selectionArgs)
        }

        return this
    }

    fun groupBy(groupBy: String): SelectionBuilder {
        mGroupBy = groupBy
        return this
    }

    fun having(having: String): SelectionBuilder {
        mHaving = having
        return this
    }

    fun table(table: String): SelectionBuilder {
        mTable = table
        return this
    }

    /**
     * Replace positional params in table. Use for JOIN ON conditions.
     */
    fun table(table: String, vararg tableParams: String): SelectionBuilder {
        if (tableParams != null && tableParams.size > 0) {
            val parts = table.split("[?]".toRegex(), (tableParams.size + 1).coerceAtLeast(0)).toTypedArray()
            val sb = StringBuilder(parts[0])
            for (i in 1..parts.size - 1) {
                sb.append('"').append(tableParams[i - 1]).append('"').append(parts[i])
            }
            mTable = sb.toString()
        } else {
            mTable = table
        }
        return this
    }

    private fun assertTable() {
        if (mTable == null) {
            throw IllegalStateException("Table not specified")
        }
    }

    fun mapToTable(column: String, table: String): SelectionBuilder {
        mProjectionMap.put(column, table + "." + column)
        return this
    }

    fun map(fromColumn: String, toClause: String): SelectionBuilder {
        mProjectionMap.put(fromColumn, toClause + " AS " + fromColumn)
        return this
    }

    /**
     * Return selection string for current internal state.

     * @see .getSelectionArgs
     */
    val selection: String
        get() = mSelection.toString()

    /**
     * Return selection arguments for current internal state.

     * @see .getSelection
     */
    val selectionArgs: Array<String>
        get() = mSelectionArgs.toTypedArray()

    private fun mapColumns(columns: Array<String>) {
        for (i in columns.indices) {
            val target = mProjectionMap[columns[i]]
            if (target != null) {
                columns[i] = target
            }
        }
    }

    override fun toString(): String {
        return "SelectionBuilder[table=$mTable, selection=$selection" +
        ", selectionArgs=" + Arrays.toString(selectionArgs) +
        "projectionMap = " + mProjectionMap + " ]"
    }

    /**
     * Execute query using the current internal state as `WHERE` clause.
     */
    fun query(db: SQLiteDatabase, columns: Array<String>, orderBy: String): Cursor {
        return query(db, false, columns, orderBy, null)
    }

    /**
     * Execute query using the current internal state as `WHERE` clause.
     */
    fun query(db: SQLiteDatabase, distinct: Boolean, columns: Array<String>?, orderBy: String,
              limit: String?): Cursor {
        assertTable()
        if (columns != null) mapColumns(columns)
        LOGV(TAG, "query(columns=" + Arrays.toString(columns)
                + ", distinct=" + distinct + ") " + this)
        return db.query(distinct, mTable, columns, selection, selectionArgs, mGroupBy,
                mHaving, orderBy, limit)
    }

    /**
     * Execute update using the current internal state as `WHERE` clause.
     */
    fun update(db: SQLiteDatabase, values: ContentValues): Int {
        assertTable()
        LOGV(TAG, "update() " + this)
        return db.update(mTable, values, selection, selectionArgs)
    }

    /**
     * Execute delete using the current internal state as `WHERE` clause.
     */
    fun delete(db: SQLiteDatabase): Int {
        assertTable()
        LOGV(TAG, "delete() " + this)
        return db.delete(mTable, selection, selectionArgs)
    }

    companion object {
        private val TAG = makeLogTag(SelectionBuilder::class.java)
    }
}
