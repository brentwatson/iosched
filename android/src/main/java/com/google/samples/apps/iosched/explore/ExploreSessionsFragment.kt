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

import android.app.Fragment
import android.app.LoaderManager
import android.content.Context
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.Parcelable
import android.provider.BaseColumns
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CursorAdapter
import android.widget.ImageView
import android.widget.TextView
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.model.TagMetadata
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.session.SessionDetailActivity
import com.google.samples.apps.iosched.ui.BaseActivity
import com.google.samples.apps.iosched.ui.widget.CollectionView
import com.google.samples.apps.iosched.ui.widget.CollectionViewCallbacks
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout
import com.google.samples.apps.iosched.util.ImageLoader
import com.google.samples.apps.iosched.util.LogUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGE
import com.google.samples.apps.iosched.util.TimeUtils
import com.google.samples.apps.iosched.util.UIUtils
import java.lang.ref.WeakReference
import java.util.*

/**
 * A fragment that shows the sessions based on the specific `Uri` that is
 * part of the arguments.
 */
class ExploreSessionsFragment : Fragment(), LoaderManager.LoaderCallbacks<Cursor> {

    private var mImageLoader: ImageLoader? = null
    private var mCollectionView: CollectionView? = null
    private var mEmptyView: View? = null
    private var mDisplayColumns: Int = 0
    private var mSessionsAdapter: SessionsAdapter? = null
    private var mCurrentUri: Uri? = null
    private var mSessionQueryToken: Int = 0
    private var mTagMetadata: TagMetadata? = null
    private val mSearchHandler = SearchHandler(this)

    /**
     * Whether we should limit our selection to live streamed events.
     */
    private var mShowLiveStreamedSessions: Boolean = false
    /**
     * Boolean that indicates whether the collectionView data is being fully reloaded in the
     * case of filters and other query arguments changing VS just a data refresh.
     */
    private var mFullReload = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        val rootView = inflater.inflate(R.layout.explore_sessions_frag, container, false)
        mCollectionView = rootView.findViewById(R.id.collection_view) as CollectionView
        mCollectionView!!.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            if (mSessionsAdapter != null) {
                mSessionsAdapter!!.handleOnClick(position)
            }
        }
        mEmptyView = rootView.findViewById(android.R.id.empty)
        activity.overridePendingTransition(0, 0)
        return rootView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mImageLoader = ImageLoader(activity, R.drawable.io_logo)
        mDisplayColumns = resources.getInteger(R.integer.deprecated_explore_sessions_columns)
        loaderManager.initLoader(TAG_METADATA_TOKEN, null, this)
        // Setup the tag filters
        if (savedInstanceState != null) {
            mCurrentUri = savedInstanceState.getParcelable<Uri>(STATE_CURRENT_URI)
            mSessionQueryToken = savedInstanceState.getInt(STATE_SESSION_QUERY_TOKEN)
            mShowLiveStreamedSessions = savedInstanceState.getBoolean(STATE_SHOW_LIVESTREAMED_SESSIONS)
            if (mSessionQueryToken > 0) {
                // Only if this is a config change should we initLoader(), to reconnect with an
                // existing loader. Otherwise, the loader will be init'd when reloadFromArguments
                // is called.
                loaderManager.initLoader(mSessionQueryToken, null, this)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_CURRENT_URI, mCurrentUri)
        outState.putInt(STATE_SESSION_QUERY_TOKEN, mSessionQueryToken)
        outState.putBoolean(STATE_SHOW_LIVESTREAMED_SESSIONS, mShowLiveStreamedSessions)
    }

    private fun setContentTopClearance(clearance: Int) {
        if (mCollectionView != null) {
            mCollectionView!!.setContentTopClearance(clearance)
        }
    }

    override fun onResume() {
        super.onResume()
        activity.invalidateOptionsMenu()
        // configure session fragment's top clearance to take our overlaid controls (Action Bar
        // and spinner box) into account.
        val actionBarSize = UIUtils.calculateActionBarSize(activity)
        val drawShadowFrameLayout = activity.findViewById(R.id.main_content) as DrawShadowFrameLayout
        drawShadowFrameLayout?.setShadowTopOffset(actionBarSize)
        setContentTopClearance(actionBarSize + resources.getDimensionPixelSize(R.dimen.explore_grid_padding))
    }

    override fun onCreateLoader(id: Int, args: Bundle): Loader<Cursor>? {
        when (id) {
            ExploreSessionsQuery.NORMAL_TOKEN -> return CursorLoader(activity,
                    mCurrentUri, ExploreSessionsQuery.NORMAL_PROJECTION,
                    if (mShowLiveStreamedSessions)
                        ScheduleContract.Sessions.LIVESTREAM_OR_YOUTUBE_URL_SELECTION
                    else
                        null,
                    null,
                    ScheduleContract.Sessions.SORT_BY_TYPE_THEN_TIME)
            ExploreSessionsQuery.SEARCH_TOKEN -> return CursorLoader(activity,
                    mCurrentUri, ExploreSessionsQuery.SEARCH_PROJECTION,
                    if (mShowLiveStreamedSessions)
                        ScheduleContract.Sessions.LIVESTREAM_OR_YOUTUBE_URL_SELECTION
                    else
                        null,
                    null,
                    ScheduleContract.Sessions.SORT_BY_TYPE_THEN_TIME)
            TAG_METADATA_TOKEN -> return TagMetadata.createCursorLoader(activity)
            else -> return null
        }
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor) {
        when (loader.id) {
            ExploreSessionsQuery.NORMAL_TOKEN // fall through
                , ExploreSessionsQuery.SEARCH_TOKEN -> reloadSessionData(cursor)
            TAG_METADATA_TOKEN -> mTagMetadata = TagMetadata(cursor)
            else -> cursor.close()
        }
    }

    private fun reloadSessionData(cursor: Cursor) {
        mEmptyView!!.visibility = if (cursor.count == 0) View.VISIBLE else View.GONE
        if (mSessionsAdapter == null) {
            mSessionsAdapter = SessionsAdapter(cursor)
        } else {
            val oldCursor = mSessionsAdapter!!.swapCursor(cursor)
            // If the cursor is the same as the old one, swapCursor returns a null.
            if (oldCursor == null) {
                mFullReload = false
            }
        }
        var state: Parcelable? = null
        if (!mFullReload) {
            state = mCollectionView!!.onSaveInstanceState()
        }
        mCollectionView!!.setCollectionAdapter(mSessionsAdapter)
        mCollectionView!!.updateInventory(mSessionsAdapter!!.inventory, mFullReload)
        if (state != null) {
            mCollectionView!!.onRestoreInstanceState(state)
        }
        mFullReload = false
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
    }

    fun reloadFromArguments(bundle: Bundle) {
        val oldUri = mCurrentUri
        val oldSessionQueryToken = mSessionQueryToken
        val oldShowLivestreamedSessions = mShowLiveStreamedSessions
        mCurrentUri = bundle.getParcelable<Uri>("_uri")

        if (ScheduleContract.Sessions.isSearchUri(mCurrentUri)) {
            mSessionQueryToken = ExploreSessionsQuery.SEARCH_TOKEN
        } else {
            mSessionQueryToken = ExploreSessionsQuery.NORMAL_TOKEN
        }
        mShowLiveStreamedSessions = bundle.getBoolean(EXTRA_SHOW_LIVESTREAMED_SESSIONS, false)

        if (oldUri != null && oldUri == mCurrentUri &&
                oldSessionQueryToken == mSessionQueryToken &&
                oldShowLivestreamedSessions == mShowLiveStreamedSessions) {
            mFullReload = false
            loaderManager.initLoader(mSessionQueryToken, null, this)
        } else {
            // We need to re-run the query
            mFullReload = true
            loaderManager.restartLoader(mSessionQueryToken, null, this)
        }
    }

    fun requestQueryUpdate(query: String) {
        mSearchHandler.removeMessages(SearchHandler.MESSAGE_QUERY_UPDATE)
        mSearchHandler.sendMessageDelayed(Message.obtain(mSearchHandler,
                SearchHandler.MESSAGE_QUERY_UPDATE, query), QUERY_UPDATE_DELAY_MILLIS)
    }

    private inner class SessionsAdapter(cursor: Cursor) : CursorAdapter(activity, cursor, 0), CollectionViewCallbacks {

        /**
         * Returns a new instance of [CollectionView.Inventory]. It always contains a single
         * [CollectionView.InventoryGroup].

         * @return A new instance of [CollectionView.Inventory].
         */
        val inventory: CollectionView.Inventory
            get() {
                val inventory = CollectionView.Inventory()
                inventory.addGroup(CollectionView.InventoryGroup(ExploreSessionsQuery.NORMAL_TOKEN).setDisplayCols(mDisplayColumns).setItemCount(cursor.count).setDataIndexStart(0).setShowHeader(false))
                return inventory
            }

        override fun newView(context: Context, cursor: Cursor?, parent: ViewGroup): View {
            return LayoutInflater.from(context).inflate(R.layout.explore_sessions_list_item,
                    parent, false)
        }

        override fun bindView(view: View, context: Context, cursor: Cursor) {
            val thumbnailView = view.findViewById(R.id.thumbnail) as ImageView
            val inScheduleIndicator = view.findViewById(R.id.indicator_in_schedule) as ImageView
            val titleView = view.findViewById(R.id.title) as TextView
            val infoView = view.findViewById(R.id.info_view) as TextView
            val sessionTypeView = view.findViewById(R.id.session_type_text) as TextView

            titleView.text = cursor.getString(ExploreSessionsQuery.TITLE)
            // Format: Day 1/ 9:00 AM - 11:00 AM/ Room 1
            val room = cursor.getString(ExploreSessionsQuery.ROOM_NAME)
            val startTime = cursor.getLong(ExploreSessionsQuery.SESSION_START)
            val endTime = cursor.getLong(ExploreSessionsQuery.SESSION_END)

            val day = UIUtils.startTimeToDayIndex(startTime)
            if (day == 0) {
                // We have a problem!
                LOGE(TAG, "Invalid Day for Session: " +
                        cursor.getString(ExploreSessionsQuery.SESSION_ID) + " " +
                        " startTime " + Date(startTime))
            }

            val tags = cursor.getString(ExploreSessionsQuery.TAGS)
            if (mTagMetadata != null) {
                val groupTag = mTagMetadata!!.getSessionGroupTag(tags.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                sessionTypeView.text = if (groupTag == null) "" else groupTag.name
            }
            var infoText = ""
            if (day != 0) {
                val startDate = Date(startTime)
                infoText = getString(R.string.explore_sessions_show_day_hour_and_room,
                        TimeUtils.formatShortDate(activity, startDate),
                        getString(R.string.explore_sessions_show_day_n, day),
                        TimeUtils.formatShortTime(activity, startDate),
                        TimeUtils.formatShortTime(activity, Date(endTime)),
                        room ?: context.getString(R.string.unknown_room))
            }
            infoView.text = infoText

            val thumbUrl = cursor.getString(ExploreSessionsQuery.PHOTO_URL)
            view.tag = cursor.getString(ExploreSessionsQuery.SESSION_ID)
            if (TextUtils.isEmpty(thumbUrl)) {
                thumbnailView.setImageResource(R.drawable.io_logo)
            } else {
                mImageLoader!!.loadImage(thumbUrl, thumbnailView)
            }
            inScheduleIndicator.visibility = if (cursor.getLong(ExploreSessionsQuery.IN_MY_SCHEDULE) == 1L)
                View.VISIBLE
            else
                View.GONE
        }

        override fun newCollectionHeaderView(context: Context, groupId: Int, parent: ViewGroup): View {
            return LayoutInflater.from(context).inflate(R.layout.list_item_explore_header, parent, false)
        }

        override fun bindCollectionHeaderView(context: Context, view: View, groupId: Int,
                                              headerLabel: String, headerTag: Any) {
            (view.findViewById(android.R.id.text1) as TextView).text = headerLabel
        }

        override fun newCollectionItemView(context: Context, groupId: Int, parent: ViewGroup): View {
            return newView(context, null, parent)
        }

        override fun bindCollectionItemView(context: Context, view: View, groupId: Int,
                                            indexInGroup: Int, dataIndex: Int, tag: Any) {
            setCursorPosition(indexInGroup)
            bindView(view, context, cursor)
        }

        private fun setCursorPosition(position: Int) {
            if (!cursor.moveToPosition(position)) {
                throw IllegalStateException("Invalid position: " + position)
            }
        }

        fun handleOnClick(position: Int) {
            setCursorPosition(position)
            val sessionId = cursor.getString(ExploreSessionsQuery.SESSION_ID)
            if (sessionId != null) {
                val data = ScheduleContract.Sessions.buildSessionUri(sessionId)
                val intent = Intent(this@ExploreSessionsFragment.activity,
                        SessionDetailActivity::class.java)
                intent.data = data
                startActivity(intent)
            }
        }
    }

    private interface ExploreSessionsQuery {
        companion object {
            val NORMAL_TOKEN = 0x1
            val SEARCH_TOKEN = 0x3
            val NORMAL_PROJECTION = arrayOf(BaseColumns._ID, ScheduleContract.Sessions.SESSION_ID, ScheduleContract.Sessions.SESSION_TITLE, ScheduleContract.Sessions.SESSION_START, ScheduleContract.Sessions.SESSION_END, ScheduleContract.Rooms.ROOM_NAME, ScheduleContract.Sessions.SESSION_URL, ScheduleContract.Sessions.SESSION_TAGS, ScheduleContract.Sessions.SESSION_PHOTO_URL, ScheduleContract.Sessions.SESSION_IN_MY_SCHEDULE)
            val SEARCH_PROJECTION = arrayOf(BaseColumns._ID, ScheduleContract.Sessions.SESSION_ID, ScheduleContract.Sessions.SESSION_TITLE, ScheduleContract.Sessions.SESSION_START, ScheduleContract.Sessions.SESSION_END, ScheduleContract.Rooms.ROOM_NAME, ScheduleContract.Sessions.SESSION_URL, ScheduleContract.Sessions.SESSION_TAGS, ScheduleContract.Sessions.SESSION_PHOTO_URL, ScheduleContract.Sessions.SESSION_IN_MY_SCHEDULE, ScheduleContract.Sessions.SEARCH_SNIPPET)
            val _ID = 0
            val SESSION_ID = 1
            val TITLE = 2
            val SESSION_START = 3
            val SESSION_END = 4
            val ROOM_NAME = 5
            val URL = 6
            val TAGS = 7
            val PHOTO_URL = 8
            val IN_MY_SCHEDULE = 9
            val SEARCH_SNIPPET = 10
        }
    }

    /**
     * `Handler` that sends search queries to the ExploreSessionsFragment.
     */
    private class SearchHandler internal constructor(fragment: ExploreSessionsFragment) : Handler() {

        private val mFragmentReference: WeakReference<ExploreSessionsFragment>

        init {
            mFragmentReference = WeakReference(fragment)
        }

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_QUERY_UPDATE -> {
                    val query = msg.obj as String
                    val instance = mFragmentReference.get()
                    instance?.reloadFromArguments(BaseActivity.intentToFragmentArguments(
                            Intent(Intent.ACTION_SEARCH,
                                    ScheduleContract.Sessions.buildSearchUri(query))))
                }
                else -> {
                }
            }
        }

        companion object {

            val MESSAGE_QUERY_UPDATE = 1
        }

    }

    companion object {
        private val TAG = LogUtils.makeLogTag(ExploreSessionsFragment::class.java)
        private val TAG_METADATA_TOKEN = 0x8
        private val STATE_CURRENT_URI = "com.google.samples.apps.iosched.explore.STATE_CURRENT_URI"
        private val STATE_SESSION_QUERY_TOKEN = "com.google.samples.apps.iosched.explore.STATE_SESSION_QUERY_TOKEN"
        private val STATE_SHOW_LIVESTREAMED_SESSIONS = "com.google.samples.apps.iosched.explore.EXTRA_SHOW_LIVESTREAMED_SESSIONS"

        val EXTRA_SHOW_LIVESTREAMED_SESSIONS = "com.google.samples.apps.iosched.explore.EXTRA_SHOW_LIVESTREAMED_SESSIONS"

        /** The delay before actual re-querying in milli seconds.  */
        private val QUERY_UPDATE_DELAY_MILLIS: Long = 100
    }
}