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

package com.google.samples.apps.iosched.util

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.webkit.WebView
import android.widget.TextView
import com.google.samples.apps.iosched.R

/**
 * This is a set of helper methods for showing various "about" information in the app.
 */
object AboutUtils {

    fun showOpenSourceLicenses(activity: Activity) {
        val fm = activity.fragmentManager
        val ft = fm.beginTransaction()
        val prev = fm.findFragmentByTag("dialog_licenses")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)

        OpenSourceLicensesDialog().show(ft, "dialog_licenses")
    }

    class OpenSourceLicensesDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
            val webView = WebView(activity)
            webView.loadUrl("file:///android_asset/licenses.html")

            return AlertDialog.Builder(activity).setTitle(R.string.about_licenses).setView(webView).setPositiveButton(R.string.ok
            ) { dialog, whichButton -> dialog.dismiss() }.create()
        }
    }

    fun showEula(activity: Activity) {
        val fm = activity.fragmentManager
        val ft = fm.beginTransaction()
        val prev = fm.findFragmentByTag("dialog_eula")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)

        EulaDialog().show(ft, "dialog_eula")
    }

    class EulaDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
            val padding = resources.getDimensionPixelSize(R.dimen.content_padding_dialog)

            val eulaTextView = TextView(activity)
            eulaTextView.text = Html.fromHtml(getString(R.string.eula_legal_text))
            eulaTextView.movementMethod = LinkMovementMethod.getInstance()
            eulaTextView.setPadding(padding, padding, padding, padding)

            return AlertDialog.Builder(activity).setTitle(R.string.about_eula).setView(eulaTextView).setPositiveButton(R.string.ok
            ) { dialog, whichButton -> dialog.dismiss() }.create()
        }
    }
}
