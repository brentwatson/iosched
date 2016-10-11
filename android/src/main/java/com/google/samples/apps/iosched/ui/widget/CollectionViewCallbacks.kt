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

package com.google.samples.apps.iosched.ui.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.google.samples.apps.iosched.ui.widget.CollectionViewCallbacks.GroupCollectionViewCallbacks

/**
 * Defines an interface to the callbacks that a [CollectionView] will be called to create each
 * elements of the collection. Alternatively you can implement [GroupCollectionViewCallbacks]
 * if you also want to define a custom container for each collection groups.
 */
interface CollectionViewCallbacks {

    /**
     * Returns a new custom View that will be used for each of the collection group headers.
     */
    fun newCollectionHeaderView(context: Context, groupId: Int, parent: ViewGroup): View

    /**
     * Binds the given data (like the header label) with the given collection group header View.
     */
    fun bindCollectionHeaderView(context: Context, view: View, groupId: Int, headerLabel: String,
                                 headerTag: Any)

    /**
     * Returns a new custom View that will be used for each of the collection item.
     */
    fun newCollectionItemView(context: Context, groupId: Int, parent: ViewGroup): View

    /**
     * Binds the given data with the given collection item View.
     */
    fun bindCollectionItemView(context: Context, view: View, groupId: Int, indexInGroup: Int,
                               dataIndex: Int, tag: Any)

    /**
     * Can be used in place of a `CollectionViewCallbacks` to define a custom layout for each
     * groups.
     */
    interface GroupCollectionViewCallbacks : CollectionViewCallbacks {

        /**
         * Returns the custom ViewGroup to be used as a container for each group of the
         * [CollectionView]. For example a [android.support.v7.widget.CardView] could be
         * returned.
         */
        fun newCollectionGroupView(context: Context, groupId: Int, group: CollectionView.InventoryGroup, parent: ViewGroup): ViewGroup
    }
}
