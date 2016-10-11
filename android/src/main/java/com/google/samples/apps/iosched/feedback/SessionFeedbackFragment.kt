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

package com.google.samples.apps.iosched.feedback

import android.app.Fragment
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.framework.QueryEnum
import com.google.samples.apps.iosched.framework.UpdatableView
import com.google.samples.apps.iosched.ui.widget.NumberRatingBar
import com.google.samples.apps.iosched.util.AnalyticsHelper
import java.util.*


/**
 * A fragment that lets the user submit feedback about a given session.
 */
class SessionFeedbackFragment : Fragment(), UpdatableView<SessionFeedbackModel> {

    private var mTitle: TextView? = null

    private var mSpeakers: TextView? = null

    private var mOverallFeedbackBar: RatingBar? = null

    private var mSessionRelevantFeedbackBar: NumberRatingBar? = null

    private var mContentFeedbackBar: NumberRatingBar? = null

    private var mSpeakerFeedbackBar: NumberRatingBar? = null

    private val listeners = ArrayList<UpdatableView.UserActionListener>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {

        val rootView = inflater.inflate(R.layout.session_feedback_frag, container, false)

        mTitle = rootView.findViewById(R.id.feedback_header_session_title) as TextView
        mSpeakers = rootView.findViewById(R.id.feedback_header_session_speakers) as TextView
        mOverallFeedbackBar = rootView.findViewById(R.id.rating_bar_0) as RatingBar
        mSessionRelevantFeedbackBar = rootView.findViewById(
                R.id.session_relevant_feedback_bar) as NumberRatingBar
        mContentFeedbackBar = rootView.findViewById(R.id.content_feedback_bar) as NumberRatingBar
        mSpeakerFeedbackBar = rootView.findViewById(R.id.speaker_feedback_bar) as NumberRatingBar

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Helps accessibility services determine the importance of this view.
            mOverallFeedbackBar!!.importantForAccessibility = RatingBar.IMPORTANT_FOR_ACCESSIBILITY_YES

            // Automatically notifies the user about changes to the view's content description.
            mOverallFeedbackBar!!.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        }

        // When the rating changes, update the content description. In TalkBack mode, this
        // informs the user about the selected rating.
        mOverallFeedbackBar!!.onRatingBarChangeListener = RatingBar.OnRatingBarChangeListener { ratingBar, rating, fromUser ->
            ratingBar.contentDescription = getString(R.string.updated_session_feedback_rating_bar_content_description, rating.toInt())
        }

        rootView.findViewById(R.id.submit_feedback_button).setOnClickListener { submitFeedback() }
        return rootView
    }

    private fun submitFeedback() {
        val overallAnswer = mOverallFeedbackBar!!.rating.toInt()
        val sessionRelevantAnswer = mSessionRelevantFeedbackBar!!.progress
        val contentAnswer = mContentFeedbackBar!!.progress
        val speakerAnswer = mSpeakerFeedbackBar!!.progress
        val comments = ""

        val args = Bundle()
        args.putInt(SessionFeedbackModel.DATA_RATING_INT, overallAnswer)
        args.putInt(SessionFeedbackModel.DATA_SESSION_RELEVANT_ANSWER_INT, sessionRelevantAnswer)
        args.putInt(SessionFeedbackModel.DATA_CONTENT_ANSWER_INT, contentAnswer)
        args.putInt(SessionFeedbackModel.DATA_SPEAKER_ANSWER_INT, speakerAnswer)
        args.putString(SessionFeedbackModel.DATA_COMMENT_STRING, comments)

        for (h1 in listeners) {
            h1.onUserAction(SessionFeedbackModel.SessionFeedbackUserActionEnum.SUBMIT, args)
        }

        activity.finish()
    }

    override fun displayData(model: SessionFeedbackModel, query: QueryEnum) {
        if (SessionFeedbackModel.SessionFeedbackQueryEnum.SESSION == query) {
            mTitle!!.text = model.sessionTitle
            if (!TextUtils.isEmpty(model.sessionSpeakers)) {
                mSpeakers!!.text = model.sessionSpeakers
            } else {
                mSpeakers!!.visibility = View.GONE
            }

            // ANALYTICS SCREEN: View Send Session Feedback screen
            // Contains: Session title
            AnalyticsHelper.sendScreenView("Feedback: " + model.sessionTitle)
        }
    }

    override fun displayErrorMessage(query: QueryEnum) {
        //Close the Activity
        activity.finish()
    }

    override fun getDataUri(query: QueryEnum): Uri? {
        if (SessionFeedbackModel.SessionFeedbackQueryEnum.SESSION == query) {
            return (activity as SessionFeedbackActivity).sessionUri
        } else {
            return null
        }
    }

    override fun getContext(): Context {
        return activity
    }

    override fun addListener(listener: UpdatableView.UserActionListener) {
        listeners.add(listener)
    }
}
