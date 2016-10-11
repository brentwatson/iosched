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
package com.google.samples.apps.iosched.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.TargetApi
import android.app.LoaderManager
import android.app.SearchManager
import android.content.Context
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v4.widget.SimpleCursorAdapter
import android.support.v7.widget.SearchView
import android.text.TextUtils
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ListView
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.explore.ExploreSessionsActivity
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.session.SessionDetailActivity
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag

class SearchActivity : BaseActivity(), LoaderManager.LoaderCallbacks<Cursor>, AdapterView.OnItemClickListener {

    private var mSearchView: SearchView? = null
    private var mQuery = ""
    private var mSearchResults: ListView? = null
    private var mResultsAdapter: SimpleCursorAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        mSearchView = findViewById(R.id.search_view) as SearchView
        setupSearchView()
        mSearchResults = findViewById(R.id.search_results) as ListView
        mResultsAdapter = SimpleCursorAdapter(this,
                R.layout.list_item_search_result, null,
                arrayOf(ScheduleContract.SearchTopicSessionsColumns.SEARCH_SNIPPET),
                intArrayOf(R.id.search_result), 0)
        mSearchResults!!.adapter = mResultsAdapter
        mSearchResults!!.onItemClickListener = this
        val toolbar = actionBarToolbar

        val up = DrawableCompat.wrap(ContextCompat.getDrawable(this, R.drawable.ic_up))
        DrawableCompat.setTint(up, resources.getColor(R.color.app_body_text_2))
        toolbar.navigationIcon = up
        toolbar.setNavigationOnClickListener { BaseActivity.navigateUpOrBack(this@SearchActivity, null) }

        var query: String? = intent.getStringExtra(SearchManager.QUERY)
        query = if (query == null) "" else query
        mQuery = query

        if (mSearchView != null) {
            mSearchView!!.setQuery(query, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            doEnterAnim()
        }

        overridePendingTransition(0, 0)
    }

    /**
     * As we only ever want one instance of this screen, we set a launchMode of singleTop. This
     * means that instead of re-creating this Activity, a new intent is delivered via this callback.
     * This prevents multiple instances of the search dialog 'stacking up' e.g. if you perform a
     * voice search.

     * See: http://developer.android.com/guide/topics/manifest/activity-element.html#lmode
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(SearchManager.QUERY)) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            if (!TextUtils.isEmpty(query)) {
                searchFor(query)
                mSearchView!!.setQuery(query, false)
            }
        }
    }

    private fun setupSearchView() {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        mSearchView!!.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        mSearchView!!.isIconified = false
        // Set the query hint.
        mSearchView!!.queryHint = getString(R.string.search_hint)
        mSearchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                mSearchView!!.clearFocus()
                return true
            }

            override fun onQueryTextChange(s: String): Boolean {
                searchFor(s)
                return true
            }
        })
        mSearchView!!.setOnCloseListener {
            dismiss(null)
            false
        }
        if (!TextUtils.isEmpty(mQuery)) {
            mSearchView!!.setQuery(mQuery, false)
        }
    }

    override fun onBackPressed() {
        dismiss(null)
    }

    fun dismiss(view: View?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            doExitAnim()
        } else {
            ActivityCompat.finishAfterTransition(this)
        }
    }

    /**
     * On Lollipop+ perform a circular reveal animation (an expanding circular mask) when showing
     * the search panel.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun doEnterAnim() {
        // Fade in a background scrim as this is a floating window. We could have used a
        // translucent window background but this approach allows us to turn off window animation &
        // overlap the fade with the reveal animation – making it feel snappier.
        val scrim = findViewById(R.id.scrim)
        scrim.animate().alpha(1f).setDuration(500L).setInterpolator(
                AnimationUtils.loadInterpolator(this, android.R.interpolator.fast_out_slow_in)).start()

        // Next perform the circular reveal on the search panel
        val searchPanel = findViewById(R.id.search_panel)
        if (searchPanel != null) {
            // We use a view tree observer to set this up once the view is measured & laid out
            searchPanel.viewTreeObserver.addOnPreDrawListener(
                    object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            searchPanel.viewTreeObserver.removeOnPreDrawListener(this)
                            // As the height will change once the initial suggestions are delivered by the
                            // loader, we can't use the search panels height to calculate the final radius
                            // so we fall back to it's parent to be safe
                            val revealRadius = (searchPanel.parent as ViewGroup).height
                            // Center the animation on the top right of the panel i.e. near to the
                            // search button which launched this screen.
                            val show = ViewAnimationUtils.createCircularReveal(searchPanel,
                                    searchPanel.right, searchPanel.top, 0f, revealRadius.toFloat())
                            show.duration = 250L
                            show.interpolator = AnimationUtils.loadInterpolator(this@SearchActivity,
                                    android.R.interpolator.fast_out_slow_in)
                            show.start()
                            return false
                        }
                    })
        }
    }

    /**
     * On Lollipop+ perform a circular animation (a contracting circular mask) when hiding the
     * search panel.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun doExitAnim() {
        val searchPanel = findViewById(R.id.search_panel)
        // Center the animation on the top right of the panel i.e. near to the search button which
        // launched this screen. The starting radius therefore is the diagonal distance from the top
        // right to the bottom left
        val revealRadius = Math.sqrt(Math.pow(searchPanel.width.toDouble(), 2.0) + Math.pow(searchPanel.height.toDouble(), 2.0)).toInt()
        // Animating the radius to 0 produces the contracting effect
        val shrink = ViewAnimationUtils.createCircularReveal(searchPanel,
                searchPanel.right, searchPanel.top, revealRadius.toFloat(), 0f)
        shrink.duration = 200L
        shrink.interpolator = AnimationUtils.loadInterpolator(this@SearchActivity,
                android.R.interpolator.fast_out_slow_in)
        shrink.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                searchPanel.visibility = View.INVISIBLE
                ActivityCompat.finishAfterTransition(this@SearchActivity)
            }
        })
        shrink.start()

        // We also animate out the translucent background at the same time.
        findViewById(R.id.scrim).animate().alpha(0f).setDuration(200L).setInterpolator(
                AnimationUtils.loadInterpolator(this@SearchActivity,
                        android.R.interpolator.fast_out_slow_in)).start()
    }

    private fun searchFor(query: String?) {
        var query = query
        // ANALYTICS EVENT: Start a search on the Search activity
        // Contains: Nothing (Event params are constant:  Search query not included)
        AnalyticsHelper.sendEvent(SCREEN_LABEL, "Search", "")
        val args = Bundle(1)
        if (query == null) {
            query = ""
        }
        args.putString(ARG_QUERY, query)
        if (TextUtils.equals(query, mQuery)) {
            loaderManager.initLoader(SearchTopicsSessionsQuery.TOKEN, args, this)
        } else {
            loaderManager.restartLoader(SearchTopicsSessionsQuery.TOKEN, args, this)
        }
        mQuery = query
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            overridePendingTransition(0, 0)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_search) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateLoader(id: Int, args: Bundle): Loader<Cursor> {
        return CursorLoader(this,
                ScheduleContract.SearchTopicsSessions.CONTENT_URI,
                SearchTopicsSessionsQuery.PROJECTION,
                null, arrayOf(args.getString(ARG_QUERY)), null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        mResultsAdapter!!.swapCursor(data)
        mSearchResults!!.visibility = if (data.count > 0) View.VISIBLE else View.GONE
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {

    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val c = mResultsAdapter!!.cursor
        c.moveToPosition(position)
        val isTopicTag = c.getInt(SearchTopicsSessionsQuery.IS_TOPIC_TAG) == 1
        val tagOrSessionId = c.getString(SearchTopicsSessionsQuery.TAG_OR_SESSION_ID)
        if (isTopicTag) {
            val intent = Intent(this, ExploreSessionsActivity::class.java)
            intent.putExtra(ExploreSessionsActivity.EXTRA_FILTER_TAG, tagOrSessionId)
            startActivity(intent)
        } else if (tagOrSessionId != null) {
            val intent = Intent(this, SessionDetailActivity::class.java)
            intent.data = ScheduleContract.Sessions.buildSessionUri(tagOrSessionId)
            startActivity(intent)
        }
    }

    private interface SearchTopicsSessionsQuery {
        companion object {
            val TOKEN = 0x4
            val PROJECTION = ScheduleContract.SearchTopicsSessions.DEFAULT_PROJECTION

            val _ID = 0
            val TAG_OR_SESSION_ID = 1
            val SEARCH_SNIPPET = 2
            val IS_TOPIC_TAG = 3
        }
    }

    companion object {

        private val TAG = makeLogTag("SearchActivity")
        private val SCREEN_LABEL = "Search"
        private val ARG_QUERY = "query"
    }
}
