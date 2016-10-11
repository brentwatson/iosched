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

package com.google.samples.apps.iosched.ui

import android.content.Context
import android.database.DataSetObserver
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import java.util.*

class SimpleSectionedListAdapter(context: Context, private val mSectionResourceId: Int,
                                 private val mBaseAdapter: ListAdapter) : BaseAdapter() {
    private var mValid = true
    private val mLayoutInflater: LayoutInflater
    private val mSections = SparseArray<Section>()

    class Section(internal var firstPosition: Int, title: CharSequence) {
        internal var sectionedPosition: Int = 0
        var title: CharSequence
            internal set

        init {
            this.title = title
        }
    }

    init {
        mLayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mBaseAdapter.registerDataSetObserver(object : DataSetObserver() {
            override fun onChanged() {
                mValid = !mBaseAdapter.isEmpty
                notifyDataSetChanged()
            }

            override fun onInvalidated() {
                mValid = false
                notifyDataSetInvalidated()
            }
        })
    }

    fun setSections(sections: Array<Section>) {
        mSections.clear()

        Arrays.sort(sections) { o, o1 ->
            if (o.firstPosition == o1.firstPosition)
                0
            else
                if (o.firstPosition < o1.firstPosition) -1 else 1
        }

        var offset = 0 // offset positions for the headers we're adding
        for (section in sections) {
            section.sectionedPosition = section.firstPosition + offset
            mSections.append(section.sectionedPosition, section)
            ++offset
        }

        notifyDataSetChanged()
    }

    fun positionToSectionedPosition(position: Int): Int {
        var offset = 0
        for (i in 0..mSections.size() - 1) {
            if (mSections.valueAt(i).firstPosition > position) {
                break
            }
            ++offset
        }
        return position + offset
    }

    fun sectionedPositionToPosition(sectionedPosition: Int): Int {
        if (isSectionHeaderPosition(sectionedPosition)) {
            return ListView.INVALID_POSITION
        }

        var offset = 0
        for (i in 0..mSections.size() - 1) {
            if (mSections.valueAt(i).sectionedPosition > sectionedPosition) {
                break
            }
            --offset
        }
        return sectionedPosition + offset
    }

    fun isSectionHeaderPosition(position: Int): Boolean {
        return mSections.get(position) != null
    }

    override fun getCount(): Int {
        return if (mValid) mBaseAdapter.count + mSections.size() else 0
    }

    override fun getItem(position: Int): Any {
        return if (isSectionHeaderPosition(position))
            mSections.get(position)
        else
            mBaseAdapter.getItem(sectionedPositionToPosition(position))
    }

    override fun getItemId(position: Int): Long {
        return if (isSectionHeaderPosition(position))
            Integer.MAX_VALUE - mSections.indexOfKey(position) as Long
        else
            mBaseAdapter.getItemId(sectionedPositionToPosition(position))
    }

    override fun getItemViewType(position: Int): Int {
        return if (isSectionHeaderPosition(position))
            viewTypeCount - 1
        else
            mBaseAdapter.getItemViewType(position)
    }

    override fun isEnabled(position: Int): Boolean {
        //noinspection SimplifiableConditionalExpression
        return if (isSectionHeaderPosition(position))
            false
        else
            mBaseAdapter.isEnabled(sectionedPositionToPosition(position))
    }

    override fun getViewTypeCount(): Int {
        return mBaseAdapter.viewTypeCount + 1 // the section headings
    }

    override fun areAllItemsEnabled(): Boolean {
        return false
    }

    override fun hasStableIds(): Boolean {
        return mBaseAdapter.hasStableIds()
    }

    override fun isEmpty(): Boolean {
        return mBaseAdapter.isEmpty
    }

    override fun getView(position: Int, convertView: View, parent: ViewGroup): View {
        if (isSectionHeaderPosition(position)) {
            var view: TextView? = convertView as TextView
            if (view == null) {
                view = mLayoutInflater.inflate(mSectionResourceId, parent, false) as TextView
            }
            view.text = mSections.get(position).title
            return view

        } else {
            return mBaseAdapter.getView(sectionedPositionToPosition(position), convertView, parent)
        }
    }
}
