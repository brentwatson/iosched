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

package com.google.samples.apps.iosched.videolibrary

import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.samples.apps.iosched.Config
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.framework.PresenterFragmentImpl
import com.google.samples.apps.iosched.framework.QueryEnum
import com.google.samples.apps.iosched.framework.UpdatableView
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.ui.widget.CollectionView
import com.google.samples.apps.iosched.ui.widget.CollectionViewCallbacks
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.util.ImageLoader
import com.google.samples.apps.iosched.util.LogUtils.*
import com.google.samples.apps.iosched.util.UIUtils
import java.util.*

/**
 * This Fragment displays all the videos of past Google I/O sessions in the form of a card for each
 * topics and a card for new videos of the current year.
 */
class VideoLibraryFragment : Fragment(), UpdatableView<VideoLibraryModel>, CollectionViewCallbacks.GroupCollectionViewCallbacks {

    private var mImageLoader: ImageLoader? = null

    private var mCollectionView: CollectionView? = null

    private var mEmptyView: View? = null

    private val mListeners = ArrayList<UpdatableView.UserActionListener>()

    override fun displayData(model: VideoLibraryModel, query: QueryEnum) {
        if ((VideoLibraryModel.VideoLibraryQueryEnum.VIDEOS == query || VideoLibraryModel.VideoLibraryQueryEnum.MY_VIEWED_VIDEOS == query) && model.videos != null) {
            updateCollectionView(model.videos)
        }
    }

    override fun displayErrorMessage(query: QueryEnum) {
    }

    override fun getContext(): Context {
        return activity
    }

    override fun addListener(toAdd: UpdatableView.UserActionListener) {
        mListeners.add(toAdd)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        val root = inflater.inflate(R.layout.video_library_frag, container, false)
        mCollectionView = root.findViewById(R.id.videos_collection_view) as CollectionView
        mEmptyView = root.findViewById(android.R.id.empty)
        activity.overridePendingTransition(0, 0)

        // Reload the content so that new random Videos are shown.
        fireReloadEvent()

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mImageLoader = ImageLoader(activity, android.R.color.transparent)
    }

    private fun setContentTopClearance(clearance: Int) {
        if (mCollectionView != null) {
            mCollectionView!!.setContentTopClearance(clearance)
        }
    }

    override fun onResume() {
        super.onResume()
        activity.invalidateOptionsMenu()

        // configure video fragment's top clearance to take our overlaid controls (Action Bar
        // and spinner box) into account.
        val actionBarSize = UIUtils.calculateActionBarSize(activity)
        val drawShadowFrameLayout = activity.findViewById(R.id.main_content) as DrawShadowFrameLayout
        drawShadowFrameLayout?.setShadowTopOffset(actionBarSize)
        setContentTopClearance(actionBarSize)
    }

    /**
     * Returns a [CollectionView.InventoryGroup] containing `numRandomVideos` number of
     * videos randomly selected in the given `videos` list.
     */
    private fun makeRandomCollectionViewInventoryGroup(
            videos: List<VideoLibraryModel.Video>, numRandomVideos: Int, groupHeaderLabel: String,
            groupId: Int): CollectionView.InventoryGroup {
        var videos = videos

        // Get the number of display columns for each groups.
        val normalColumns = resources.getInteger(R.integer.video_library_columns)

        // Randomly select the requested number of items fro the list.
        videos = ArrayList(videos)
        Collections.shuffle(videos)
        videos = videos.subList(0, Math.min(videos.size, numRandomVideos))

        // Add these videos to the group.
        val lastYearGroup = CollectionView.InventoryGroup(groupId).setDataIndexStart(0).setHeaderLabel(groupHeaderLabel).setShowHeader(true).setDisplayCols(normalColumns)
        for (video in videos) {
            lastYearGroup.addItemWithTag(video)
        }
        return lastYearGroup
    }

    /**
     * Updates the CollectionView with the given list of `videos`.
     */
    private fun updateCollectionView(videos: List<VideoLibraryModel.Video>) {
        LOGD(TAG, "Updating video library collection view.")
        val inventory = CollectionView.Inventory()
        val shownVideos = resources.getInteger(R.integer.shown_videos)

        // Find out what's the current year.
        val currentYear = currentYear

        // Get all the videos for the current year. They go into a special section for "new" videos.
        // This means this section will contain no videos between 31st of december and the next
        // Google IO which typically happens in May/June. So in effect Videos of more than 6 month
        // are not considered "New" anymore.
        val latestYearVideos = ArrayList<VideoLibraryModel.Video>()
        for (dataIndex in videos.indices) {
            val video = videos[dataIndex]
            if (currentYear == video.year) {
                latestYearVideos.add(video)
            }
        }

        if (latestYearVideos.size > 0) {
            val lastYearGroup = makeRandomCollectionViewInventoryGroup(
                    latestYearVideos, shownVideos,
                    getString(R.string.new_videos_title, currentYear), GROUP_ID_NEW)
            inventory.addGroup(lastYearGroup)
        }

        // Adding keynotes on top.
        val keynotes = ArrayList<VideoLibraryModel.Video>()
        for (dataIndex in videos.indices) {
            val video = videos[dataIndex]
            val curTopic = video.topic

            // We ignore the video if it;s not a keynote.
            if (VideoLibraryModel.KEYNOTES_TOPIC != curTopic) {
                continue
            }

            keynotes.add(video)
        }
        var curGroup = makeRandomCollectionViewInventoryGroup(
                keynotes, shownVideos, VideoLibraryModel.KEYNOTES_TOPIC, GROUP_ID_KEYNOTES)
        inventory.addGroup(curGroup)

        // Go through all videos and organize them into groups for each topic. We assume they are
        // already ordered by topics already.
        var curGroupVideos: MutableList<VideoLibraryModel.Video> = ArrayList()
        for (dataIndex in videos.indices) {
            val video = videos[dataIndex]
            val curTopic = video.topic

            // We ignore Keynotes because they have already been added.
            if (VideoLibraryModel.KEYNOTES_TOPIC == curTopic) {
                continue
            }

            // Skip some potentially problematic videos that have null topics.
            if (curTopic == null) {
                LOGW(TAG, "Video with title '" + video.title + "' has a null topic so it "
                        + "won't be displayed in the video library.")
                continue
            }
            curGroupVideos.add(video)

            // If we've added all the videos with the same topic (i.e. the next video has a
            // different topic) then we create the InventoryGroup and add it to the Inventory.
            if (dataIndex == videos.size - 1 || videos[dataIndex + 1].topic != curTopic) {
                curGroup = makeRandomCollectionViewInventoryGroup(
                        curGroupVideos, shownVideos, curTopic, GROUP_ID_TOPIC)
                inventory.addGroup(curGroup)
                curGroupVideos = ArrayList<VideoLibraryModel.Video>()
            }
        }

        mCollectionView!!.setCollectionAdapter(this)
        mCollectionView!!.updateInventory(inventory)

        mEmptyView!!.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun newCollectionGroupView(context: Context, groupId: Int,
                                        group: CollectionView.InventoryGroup, parent: ViewGroup): ViewGroup {
        val inflater = context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        return inflater.inflate(R.layout.video_lib_card_container, parent, false) as ViewGroup
    }

    override fun newCollectionHeaderView(context: Context, groupId: Int, parent: ViewGroup): View {
        val inflater = context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        return inflater.inflate(R.layout.card_header_with_button, parent, false)
    }

    override fun bindCollectionHeaderView(context: Context, view: View, groupId: Int,
                                          headerLabel: String, headerTag: Any) {
        (view.findViewById(android.R.id.title) as TextView).text = headerLabel
        view.contentDescription = getString(R.string.more_items_button_desc_with_label_a11y,
                headerLabel)
        view.setOnClickListener {
            LOGD(TAG, "Clicking More button on VideoLib category: " + headerLabel)

            // ANALYTICS EVENT: Click on the "More" button of a card in the Video Library
            // Contains: The clicked header's label
            AnalyticsHelper.sendEvent(VIDEO_LIBRARY_ANALYTICS_CATEGORY, "morebutton",
                    headerLabel)
            // Start the Filtered Video Library intent.
            val i = Intent(getContext(), VideoLibraryFilteredActivity::class.java)
            if (groupId == GROUP_ID_KEYNOTES) {
                i.putExtra(VideoLibraryFilteredActivity.KEY_FILTER_TOPIC,
                        VideoLibraryModel.KEYNOTES_TOPIC)
            } else if (groupId == GROUP_ID_NEW) {
                i.putExtra(VideoLibraryFilteredActivity.KEY_FILTER_YEAR, currentYear)
            } else if (groupId == GROUP_ID_TOPIC) {
                i.putExtra(VideoLibraryFilteredActivity.KEY_FILTER_TOPIC, headerLabel)
            }
            startActivity(i)
        }
    }

    /**
     * Holds pointers to View's children.
     */
    internal class CollectionItemViewHolder {
        var thumbnailView: ImageView? = null
        var titleView: TextView? = null
        var speakersView: TextView? = null
        var descriptionView: TextView? = null
    }

    override fun newCollectionItemView(context: Context, groupId: Int, parent: ViewGroup): View {
        val inflater = context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.video_library_item, parent, false)
        val viewHolder = CollectionItemViewHolder()
        viewHolder.thumbnailView = view.findViewById(R.id.thumbnail) as ImageView
        viewHolder.titleView = view.findViewById(R.id.title) as TextView
        viewHolder.speakersView = view.findViewById(R.id.speakers) as TextView
        viewHolder.descriptionView = view.findViewById(R.id.description) as TextView
        view.tag = viewHolder
        return view
    }

    override fun bindCollectionItemView(context: Context, view: View, groupId: Int,
                                        indexInGroup: Int, dataIndex: Int, tag: Any) {
        val video = tag as VideoLibraryModel.Video ?: return
        val viewHolder = view.tag as CollectionItemViewHolder
        viewHolder.titleView!!.text = video.title
        viewHolder.speakersView!!.text = video.speakers
        viewHolder.speakersView!!.visibility = if (TextUtils.isEmpty(video.speakers)) View.GONE else View.VISIBLE
        viewHolder.descriptionView!!.text = video.desc
        viewHolder.descriptionView!!.visibility = if (TextUtils.isEmpty(video.desc) || video.title == video.desc)
            View.GONE
        else
            View.VISIBLE

        val thumbUrl = video.thumbnailUrl
        if (TextUtils.isEmpty(thumbUrl)) {
            viewHolder.thumbnailView!!.setImageResource(android.R.color.transparent)
        } else {
            mImageLoader!!.loadImage(thumbUrl, viewHolder.thumbnailView)
        }

        val videoId = video.id
        val youtubeLink = if (TextUtils.isEmpty(videoId))
            ""
        else if (videoId.contains("://"))
            videoId
        else
            String.format(Locale.US, Config.VIDEO_LIBRARY_URL_FMT, videoId)

        // Display the overlay if the video has already been played.
        if (video.alreadyPlayed) {
            styleVideoAsViewed(view)
        } else {
            viewHolder.thumbnailView!!.setColorFilter(getContext().resources.getColor(
                    R.color.light_content_scrim))
        }

        view.setOnClickListener { view ->
            if (!TextUtils.isEmpty(youtubeLink)) {
                LOGD(TAG, "Launching Youtube video: " + youtubeLink)

                // ANALYTICS EVENT: Click on a video on the Video Library screen
                // Contains: video's YouTube URL, http://www.youtube.com/...
                AnalyticsHelper.sendEvent(VIDEO_LIBRARY_ANALYTICS_CATEGORY, "selectvideo",
                        youtubeLink)
                // Start playing the video on Youtube.
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeLink))
                UIUtils.preferPackageForIntent(activity, i,
                        UIUtils.YOUTUBE_PACKAGE_NAME)
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                startActivity(i)
                // Mark the video as played.
                fireVideoPlayedEvent(video)
                // Display the overlay for videos that has already been played.
                styleVideoAsViewed(view)
            }
        }
    }

    /**
     * Show the video as Viewed. We display a semi-transparent grey overlay over the video
     * thumbnail.
     */
    private fun styleVideoAsViewed(videoItemView: View) {
        val thumbnailView = videoItemView.findViewById(R.id.thumbnail) as ImageView
        thumbnailView.setColorFilter(context.resources.getColor(
                R.color.video_scrim_watched))
    }

    /**
     * Let all UserActionListener know that the video list has been reloaded and that therefore we
     * need to display another random set of videos.
     */
    private fun fireReloadEvent() {
        for (h1 in mListeners) {
            val args = Bundle()
            args.putInt(PresenterFragmentImpl.KEY_RUN_QUERY_ID,
                    VideoLibraryModel.VideoLibraryQueryEnum.VIDEOS.id)
            h1.onUserAction(VideoLibraryModel.VideoLibraryUserActionEnum.RELOAD, args)
        }
    }

    /**
     * Let all UserActionListener know that the given Video has been played.
     */
    private fun fireVideoPlayedEvent(video: VideoLibraryModel.Video) {
        for (h1 in mListeners) {
            val args = Bundle()
            args.putString(VideoLibraryModel.KEY_VIDEO_ID, video.id)
            h1.onUserAction(VideoLibraryModel.VideoLibraryUserActionEnum.VIDEO_PLAYED, args)
        }
    }

    override fun getDataUri(query: QueryEnum): Uri {
        if (query === VideoLibraryModel.VideoLibraryQueryEnum.VIDEOS) {
            return ScheduleContract.Videos.CONTENT_URI
        } else if (query === VideoLibraryModel.VideoLibraryQueryEnum.MY_VIEWED_VIDEOS) {
            return ScheduleContract.MyViewedVideos.CONTENT_URI
        }
        return Uri.EMPTY
    }

    companion object {

        private val TAG = makeLogTag(VideoLibraryFragment::class.java)

        private val VIDEO_LIBRARY_ANALYTICS_CATEGORY = "Video Library"

        private val GROUP_ID_NEW = 0

        private val GROUP_ID_KEYNOTES = 1

        private val GROUP_ID_TOPIC = 2

        /**
         * Returns the current year. We use it to display a special card for new videos.
         */
        private val currentYear: Int
            get() = Calendar.getInstance().get(Calendar.YEAR)
    }
}
