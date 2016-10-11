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
package com.google.samples.apps.iosched.welcome

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.util.AccountUtils
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import java.util.*

/**
 * The attending in person fragment in the welcome screen.
 */
class AccountFragment : WelcomeFragment(), WelcomeActivity.WelcomeActivityContent, RadioGroup.OnCheckedChangeListener {

    private var mAccountManager: AccountManager? = null
    private var mAccounts: List<Account>? = null
    private var mSelectedAccount: String? = null

    override fun shouldDisplay(context: Context): Boolean {
        val account = AccountUtils.getActiveAccount(context) ?: return true
        return false
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)

        mAccountManager = AccountManager.get(activity)
        mAccounts = ArrayList(
                Arrays.asList(*mAccountManager!!.getAccountsByType(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE)))
    }

    override fun onDetach() {
        super.onDetach()
        mAccountManager = null
        mAccounts = null
        mSelectedAccount = null
    }

    override fun getPositiveListener(): View.OnClickListener {
        return object : WelcomeFragment.WelcomeFragmentOnClickListener(mActivity) {
            override fun onClick(v: View) {
                // Ensure we don't run this fragment again
                LOGD(TAG, "Marking attending flag.")
                AccountUtils.setActiveAccount(mActivity, mSelectedAccount!!.toString())
                doNext()
            }
        }
    }

    override fun getNegativeListener(): View.OnClickListener {
        return object : WelcomeFragment.WelcomeFragmentOnClickListener(mActivity) {
            override fun onClick(v: View) {
                // Nothing to do here
                LOGD(TAG, "User needs to select an account.")
                doFinish()
            }
        }
    }

    override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
        val rb = group.findViewById(checkedId) as RadioButton
        mSelectedAccount = rb.text.toString()
        LOGD(TAG, "Checked: " + mSelectedAccount!!)

        if (mActivity is WelcomeFragment.WelcomeFragmentContainer) {
            (mActivity as WelcomeFragment.WelcomeFragmentContainer).setPositiveButtonEnabled(true)
        }
    }

    override fun getPositiveText(): String {
        return getResourceString(R.string.ok)
    }

    override fun getNegativeText(): String {
        return getResourceString(R.string.cancel)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        // Inflate the layout for this fragment
        val layout = inflater.inflate(R.layout.welcome_account_fragment, container, false)
        if (mAccounts == null) {
            LOGD(TAG, "No accounts to display.")
            return null
        }

        if (mActivity is WelcomeFragment.WelcomeFragmentContainer) {
            (mActivity as WelcomeFragment.WelcomeFragmentContainer).setPositiveButtonEnabled(false)
        }

        // Find the view
        val accountsContainer = layout.findViewById(R.id.welcome_account_list) as RadioGroup
        accountsContainer.removeAllViews()
        accountsContainer.setOnCheckedChangeListener(this)

        // Create the child views
        for (account in mAccounts!!) {
            LOGD(TAG, "Account: " + account.name)
            val button = RadioButton(mActivity)
            button.text = account.name
            accountsContainer.addView(button)
        }

        return layout
    }

    companion object {
        private val TAG = makeLogTag(AccountFragment::class.java)
    }
}
