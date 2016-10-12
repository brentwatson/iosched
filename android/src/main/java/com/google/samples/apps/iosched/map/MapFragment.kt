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

package com.google.samples.apps.iosched.map

import android.app.Activity
import android.app.LoaderManager.LoaderCallbacks
import android.content.Loader
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.map.util.CachedTileProvider
import com.google.samples.apps.iosched.map.util.MarkerLoadingTask
import com.google.samples.apps.iosched.map.util.MarkerModel
import com.google.samples.apps.iosched.map.util.TileLoadingTask
import com.google.samples.apps.iosched.provider.ScheduleContract
import com.google.samples.apps.iosched.util.AnalyticsHelper
import com.google.samples.apps.iosched.util.LogUtils.LOGD
import com.google.samples.apps.iosched.util.LogUtils.makeLogTag
import com.google.samples.apps.iosched.util.MapUtils
import com.jakewharton.disklrucache.DiskLruCache
import java.io.IOException
import java.util.*

/**
 * Shows a map of the conference venue.
 */
class MapFragment : com.google.android.gms.maps.MapFragment(), GoogleMap.OnMarkerClickListener, GoogleMap.OnIndoorStateChangeListener, GoogleMap.OnMapClickListener, OnMapReadyCallback {

    // Tile Providers
    private val mTileProviders = SparseArray<CachedTileProvider>(INITIAL_FLOOR_COUNT)
    private val mTileOverlays = SparseArray<TileOverlay>(INITIAL_FLOOR_COUNT)

    private val mTileCache: DiskLruCache? = null

    // Markers stored by id
    private val mMarkers = HashMap<String, MarkerModel>()
    // Markers stored by floor
    private val mMarkersFloor = SparseArray<ArrayList<Marker>>(INITIAL_FLOOR_COUNT)

    // Screen DPI
    private var mDPI = 0f

    // Indoor maps representation of Moscone Center
    private var mMosconeBuilding: IndoorBuilding? = null

    // currently displayed floor
    private var mFloor = INVALID_FLOOR

    private var mActiveMarker: Marker? = null
    private var ICON_ACTIVE: BitmapDescriptor? = null
    private var ICON_NORMAL: BitmapDescriptor? = null

    private var mAtMoscone = false
    private var mMosconeMaker: Marker? = null

    private var mMap: GoogleMap? = null
    private val mMapInsets = Rect()

    private var mHighlightedRoomId: String? = null
    private var mHighlightedRoom: MarkerModel? = null

    private var mInitialFloor = MOSCONE_DEFAULT_LEVEL_INDEX

    interface Callbacks {

        fun onInfoHide()

        fun onInfoShowMoscone()

        fun onInfoShowTitle(label: String, roomType: Int)

        fun onInfoShowSessionlist(roomId: String, roomTitle: String, roomType: Int)

        fun onInfoShowFirstSessionTitle(roomId: String, roomTitle: String, roomType: Int)
    }

    private var mCallbacks = sDummyCallbacks


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mActiveMarker != null) {
            // A marker is currently selected, restore its selection.
            outState.putString(EXTRAS_HIGHLIGHT_ROOM, mActiveMarker!!.title)
            outState.putInt(EXTRAS_ACTIVE_FLOOR, INVALID_FLOOR)
        } else if (mAtMoscone) {
            // No marker is selected, store the active floor if at Moscone.
            outState.putInt(EXTRAS_ACTIVE_FLOOR, mFloor)
            outState.putString(EXTRAS_HIGHLIGHT_ROOM, null)
        }

        LOGD(TAG, "Saved state: " + outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ANALYTICS SCREEN: View the Map screen
        // Contains: Nothing (Page name is a constant)
        AnalyticsHelper.sendScreenView(SCREEN_LABEL)

        // get DPI
        mDPI = activity.resources.displayMetrics.densityDpi / 160f

        ICON_ACTIVE = BitmapDescriptorFactory.fromResource(R.drawable.map_marker_selected)
        ICON_NORMAL = BitmapDescriptorFactory.fromResource(R.drawable.map_marker_unselected)

        // Get the arguments and restore the highlighted room or displayed floor.
        val data = arguments
        if (data != null) {
            mHighlightedRoomId = data.getString(EXTRAS_HIGHLIGHT_ROOM, null)
            mInitialFloor = data.getInt(EXTRAS_ACTIVE_FLOOR, MOSCONE_DEFAULT_LEVEL_INDEX)
        }

        getMapAsync(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle): View? {
        val mapView = super.onCreateView(inflater, container, savedInstanceState)

        setMapInsets(mMapInsets)

        return mapView
    }

    fun setMapInsets(left: Int, top: Int, right: Int, bottom: Int) {
        mMapInsets.set(left, top, right, bottom)
        if (mMap != null) {
            mMap!!.setPadding(mMapInsets.left, mMapInsets.top, mMapInsets.right, mMapInsets.bottom)
        }
    }

    fun setMapInsets(insets: Rect) {
        mMapInsets.set(insets.left, insets.top, insets.right, insets.bottom)
        if (mMap != null) {
            mMap!!.setPadding(mMapInsets.left, mMapInsets.top, mMapInsets.right, mMapInsets.bottom)
        }
    }

    override fun onStop() {
        super.onStop()

        closeTileCache()
    }

    /**
     * Closes the caches of all allocated tile providers.

     * @see CachedTileProvider.closeCache
     */
    private fun closeTileCache() {
        for (i in 0..mTileProviders.size() - 1) {
            try {
                mTileProviders.valueAt(i).closeCache()
            } catch (e: IOException) {
            }

        }
    }

    /**
     * Clears the map and initialises all map variables that hold markers and overlays.
     */
    private fun clearMap() {
        if (mMap != null) {
            mMap!!.clear()
        }

        // Close all tile provider caches
        closeTileCache()

        // Clear all map elements
        mTileProviders.clear()
        mTileOverlays.clear()

        mMarkers.clear()
        mMarkersFloor.clear()

        mFloor = INVALID_FLOOR
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap!!.isIndoorEnabled = true
        mMap!!.isMyLocationEnabled = false
        mMap!!.setOnMarkerClickListener(this)
        mMap!!.setOnIndoorStateChangeListener(this)
        mMap!!.setOnMapClickListener(this)
        val mapUiSettings = mMap!!.uiSettings
        mapUiSettings.isZoomControlsEnabled = false
        mapUiSettings.isMapToolbarEnabled = false

        // load all markers
        val lm = loaderManager
        lm.initLoader(TOKEN_LOADER_MARKERS, null, mMarkerLoader).forceLoad()

        // load the tile overlays
        lm.initLoader(TOKEN_LOADER_TILES, null, mTileLoader).forceLoad()

        setupMap(true)
    }

    private fun setupMap(resetCamera: Boolean) {

        // Add a Marker for Moscone
        mMosconeMaker = mMap!!.addMarker(MapUtils.createMosconeMarker(MOSCONE).visible(false))

        if (resetCamera) {
            // Move camera directly to Moscone
            centerOnMoscone(false)
        }

        LOGD(TAG, "Map setup complete.")
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        if (activity !is Callbacks) {
            throw ClassCastException(
                    "Activity must implement fragment's callbacks.")
        }

        mCallbacks = activity

        activity.contentResolver.registerContentObserver(
                ScheduleContract.MapMarkers.CONTENT_URI, true, mObserver)
        activity.contentResolver.registerContentObserver(
                ScheduleContract.MapTiles.CONTENT_URI, true, mObserver)
    }

    override fun onDetach() {
        super.onDetach()
        mCallbacks = sDummyCallbacks

        activity.contentResolver.unregisterContentObserver(mObserver)
    }

    /**
     * Moves the camera to Moscone Center (as defined in [.MOSCONE] and [.CAMERA_ZOOM].

     * @param animate Animates the camera if true, otherwise it is moved
     */
    private fun centerOnMoscone(animate: Boolean) {
        val camera = CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().bearing(CAMERA_BEARING).target(MOSCONE_CAMERA).zoom(CAMERA_ZOOM).tilt(0f).build())
        if (animate) {
            mMap!!.animateCamera(camera)
        } else {
            mMap!!.moveCamera(camera)
        }
    }

    /**
     * Switches the displayed floor for which elements are displayed.
     * If the map is not initialised yet or no data has been loaded, nothing will be displayed.
     * If an invalid floor is specified and elements are currently on the map, all visible
     * elements will be hidden.
     * If this floor is not active for the indoor building, it is made active.

     * @param floor index of the floor to display. It requires an overlay and at least one Marker
     * *              to
     * *              be defined for it and it has to be a valid index in the
     * *              [com.google.android.gms.maps.model.IndoorBuilding] object that
     * *              describes Moscone.
     */
    private fun showFloorElementsIndex(floor: Int) {
        LOGD(TAG, "Show floor " + floor)

        // Hide previous floor elements if the floor has changed
        if (mFloor != floor) {
            setFloorElementsVisible(mFloor, false)
        }

        mFloor = floor

        if (isValidFloor(mFloor) && mAtMoscone) {
            // Always hide the Moscone marker if a floor is shown
            mMosconeMaker!!.isVisible = false
            setFloorElementsVisible(mFloor, true)
        } else {
            // Show Moscone marker if not at Moscone or at an invalid floor
            mMosconeMaker!!.isVisible = true
        }
    }

    /**
     * Change the active floor of Moscone Center
     * to the given floor index. See [.showFloorElementsIndex].

     * @param floor Index of the floor to show.
     * *
     * @see .showFloorElementsIndex
     */
    private fun showFloorIndex(floor: Int) {
        if (isValidFloor(floor) && mAtMoscone) {

            if (mMap!!.focusedBuilding.activeLevelIndex == floor) {
                // This floor is already active, show its elements
                showFloorElementsIndex(floor)
            } else {
                // This floor is not shown yet, switch to this floor on the map
                mMap!!.focusedBuilding.levels[floor].activate()
            }

        } else {
            LOGD(TAG, "Can't show floor index $floor.")
        }
    }

    /**
     * Change the visibility of all Markers and TileOverlays for a floor.
     */
    private fun setFloorElementsVisible(floor: Int, visible: Boolean) {
        // Overlays
        val overlay = mTileOverlays.get(floor)
        if (overlay != null) {
            overlay.isVisible = visible
        }

        // Markers
        val markers = mMarkersFloor.get(floor)
        if (markers != null) {
            for (m in markers) {
                m.isVisible = visible
            }
        }
    }

    /**
     * A floor is valid if the Moscone building contains that floor. It is not required for a floor
     * to have a tile overlay and markers.
     */
    private fun isValidFloor(floor: Int): Boolean {
        return floor < mMosconeBuilding!!.levels.size
    }

    /**
     * Display map features if Moscone is the current building.
     * This explicitly  re-enables all elements that should be displayed at the current floor.
     */
    private fun enableMapElements() {
        if (mMosconeBuilding != null && mAtMoscone) {
            onIndoorLevelActivated(mMosconeBuilding)
        }
    }

    private fun onDefocusMoscone() {
        // Hide all markers and tile overlays
        deselectActiveMarker()
        showFloorElementsIndex(INVALID_FLOOR)
        mCallbacks.onInfoShowMoscone()
    }

    private fun onFocusMoscone() {
        // Highlight a room if argument is set and it exists, otherwise show the default floor
        if (mHighlightedRoomId != null && mMarkers.containsKey(mHighlightedRoomId!!)) {
            highlightRoom(mHighlightedRoomId!!)
            showFloorIndex(mHighlightedRoom!!.floor)
            // Reset highlighted room because it has just been displayed.
            mHighlightedRoomId = null
        } else {
            // Hide the bottom sheet that is displaying the Moscone details at this point
            mCallbacks.onInfoHide()
            // Switch to the default level for Moscone and reset its value
            showFloorIndex(mInitialFloor)
        }
        mInitialFloor = MOSCONE_DEFAULT_LEVEL_INDEX
    }

    override fun onIndoorBuildingFocused() {
        val building = mMap!!.focusedBuilding

        if (building != null && mMosconeBuilding == null
                && mMap!!.projection.visibleRegion.latLngBounds.contains(MOSCONE)) {
            // Store the first active building. This will always be Moscone
            mMosconeBuilding = building
        }

        if (!mAtMoscone && building != null && building == mMosconeBuilding) {
            // Map is focused on Moscone Center
            mAtMoscone = true
            onFocusMoscone()
        } else if (mAtMoscone && mMosconeBuilding != null && mMosconeBuilding != building) {
            // Map is no longer focused on Moscone Center
            mAtMoscone = false
            onDefocusMoscone()
        }
        onIndoorLevelActivated(building)
    }

    override fun onIndoorLevelActivated(indoorBuilding: IndoorBuilding?) {
        if (indoorBuilding != null && indoorBuilding == mMosconeBuilding) {
            onMosconeFloorActivated(indoorBuilding.activeLevelIndex)
        }
    }

    /**
     * Called when an indoor floor level in the Moscone building has been activated.
     * If a room is to be highlighted, the map is centered and its marker is activated.
     */
    private fun onMosconeFloorActivated(activeLevelIndex: Int) {
        if (mHighlightedRoom != null && mFloor == mHighlightedRoom!!.floor) {
            // A room highlight is pending. Highlight the marker and display info details.
            onMarkerClick(mHighlightedRoom!!.marker!!)
            centerMap(mHighlightedRoom!!.marker!!.position)

            // Remove the highlight room flag, because the room has just been highlighted.
            mHighlightedRoom = null
            mHighlightedRoomId = null
        } else if (mFloor != activeLevelIndex) {
            // Deselect and hide the info details.
            deselectActiveMarker()
            mCallbacks.onInfoHide()
        }

        // Show map elements for this floor
        showFloorElementsIndex(activeLevelIndex)
    }

    override fun onMapClick(latLng: LatLng) {
        deselectActiveMarker()
        mCallbacks.onInfoHide()
    }

    private fun deselectActiveMarker() {
        if (mActiveMarker != null) {
            mActiveMarker!!.setIcon(ICON_NORMAL!!)
            mActiveMarker = null
        }
    }

    private fun selectActiveMarker(marker: Marker?) {
        if (mActiveMarker === marker) {
            return
        }
        if (marker != null) {
            mActiveMarker = marker
            mActiveMarker!!.setIcon(ICON_ACTIVE!!)
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val title = marker.title
        val model = mMarkers[title]

        // Log clicks on all markers (regardless of type)
        // ANALYTICS EVENT: Click on marker on the map.
        // Contains: Marker ID (for example room UUID)
        AnalyticsHelper.sendEvent("Map", "markerclick", title)

        deselectActiveMarker()

        // The Moscone marker can be compared directly.
        // For all other markers the model needs to be looked up first.
        if (marker == mMosconeMaker) {
            // Return camera to Moscone
            LOGD(TAG, "Clicked on Moscone marker, return to initial display.")
            centerOnMoscone(true)

        } else if (model != null && MapUtils.hasInfoTitleOnly(model.type)) {
            // Show a basic info window with a title only
            mCallbacks.onInfoShowTitle(model.label, model.type)
            selectActiveMarker(marker)

        } else if (model != null && MapUtils.hasInfoSessionList(model.type)) {
            // Type has sessions to display
            mCallbacks.onInfoShowSessionlist(model.id, model.label, model.type)
            selectActiveMarker(marker)

        } else if (model != null && MapUtils.hasInfoFirstDescriptionOnly(model.type)) {
            // Display the description of the first session only
            mCallbacks.onInfoShowFirstSessionTitle(model.id, model.label, model.type)
            selectActiveMarker(marker)

        } else {
            // Hide the bottom sheet for unknown markers
            mCallbacks.onInfoHide()
        }

        return true
    }

    private fun centerMap(position: LatLng) {
        mMap!!.animateCamera(CameraUpdateFactory.newLatLngZoom(position, CAMERA_ZOOM))
    }

    private fun highlightRoom(roomId: String) {
        val m = mMarkers[roomId]
        if (m != null) {
            mHighlightedRoom = m
            showFloorIndex(m.floor)
        }
    }

    private fun onMarkersLoaded(list: List<MarkerLoadingTask.MarkerEntry>?) {
        if (list != null) {
            for (entry in list) {

                // Skip incomplete entries
                if (entry.options == null || entry.model == null) {
                    break
                }

                // Add marker to the map
                val m = mMap!!.addMarker(entry.options)
                val model = entry.model
                model.marker = m

                // Store the marker and its model
                var markerList: ArrayList<Marker>? = mMarkersFloor.get(model.floor)
                if (markerList == null) {
                    // Initialise the list of Markers for this floor
                    markerList = ArrayList<Marker>()
                    mMarkersFloor.put(model.floor, markerList)
                }
                markerList.add(m)
                mMarkers.put(model.id, model)
            }
        }

        enableMapElements()
    }

    private fun onTilesLoaded(list: List<TileLoadingTask.TileEntry>?) {
        if (list != null) {
            // Display tiles if they have been loaded, skip them otherwise but display the rest of
            // the map.
            for (entry in list) {
                val tileOverlay = TileOverlayOptions().tileProvider(entry.provider).visible(false)

                // Store the tile overlay and provider
                mTileProviders.put(entry.floor, entry.provider)
                mTileOverlays.put(entry.floor, mMap!!.addTileOverlay(tileOverlay))
            }
        }

        enableMapElements()
    }

    private val mObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean) {
            if (!isAdded) {
                return
            }

            //clear map reload all data
            clearMap()
            setupMap(false)

            // reload data from loaders
            val lm = activity.loaderManager

            var loader: Loader<Cursor>? = lm.getLoader<Cursor>(TOKEN_LOADER_MARKERS)
            if (loader != null) {
                loader.forceLoad()
            }

            loader = lm.getLoader<Cursor>(TOKEN_LOADER_TILES)
            if (loader != null) {
                loader.forceLoad()
            }
        }
    }


    /**
     * LoaderCallbacks for the [MarkerLoadingTask] that loads all markers for the map.
     */
    private val mMarkerLoader = object : LoaderCallbacks<List<MarkerLoadingTask.MarkerEntry>> {
        override fun onCreateLoader(id: Int, args: Bundle): Loader<List<MarkerLoadingTask.MarkerEntry>> {
            return MarkerLoadingTask(activity)
        }

        override fun onLoadFinished(loader: Loader<List<MarkerLoadingTask.MarkerEntry>>,
                                    data: List<MarkerLoadingTask.MarkerEntry>) {
            onMarkersLoaded(data)
        }

        override fun onLoaderReset(loader: Loader<List<MarkerLoadingTask.MarkerEntry>>) {
        }
    }

    /**
     * LoaderCallbacks for the [TileLoadingTask] that loads all tile overlays for the map.
     */
    private val mTileLoader = object : LoaderCallbacks<List<TileLoadingTask.TileEntry>> {
        override fun onCreateLoader(id: Int, args: Bundle): Loader<List<TileLoadingTask.TileEntry>> {
            return TileLoadingTask(activity, mDPI)
        }

        override fun onLoadFinished(loader: Loader<List<TileLoadingTask.TileEntry>>,
                                    data: List<TileLoadingTask.TileEntry>) {
            onTilesLoaded(data)
        }

        override fun onLoaderReset(loader: Loader<List<TileLoadingTask.TileEntry>>) {
        }
    }

    companion object {

        private val MOSCONE = LatLng(37.783107, -122.403789)
        private val MOSCONE_CAMERA = LatLng(37.78308931536713, -122.40409433841705)

        private val EXTRAS_HIGHLIGHT_ROOM = "EXTRAS_HIGHLIGHT_ROOM"
        private val EXTRAS_ACTIVE_FLOOR = "EXTRAS_ACTIVE_FLOOR"

        // Initial camera zoom
        private val CAMERA_ZOOM = 18.19f
        private val CAMERA_BEARING = 234.2f

        private val INVALID_FLOOR = Integer.MIN_VALUE

        // Estimated number of floors used to initialise data structures with appropriate capacity
        private val INITIAL_FLOOR_COUNT = 3

        // Default level (index of level in IndoorBuilding object for Moscone)
        private val MOSCONE_DEFAULT_LEVEL_INDEX = 1

        private val TAG = makeLogTag(MapFragment::class.java)

        private val TOKEN_LOADER_MARKERS = 0x1
        private val TOKEN_LOADER_TILES = 0x2
        //For Analytics tracking
        val SCREEN_LABEL = "Map"

        private val sDummyCallbacks: Callbacks = object : Callbacks {

            override fun onInfoHide() {
            }

            override fun onInfoShowMoscone() {
            }

            override fun onInfoShowTitle(label: String, roomType: Int) {
            }

            override fun onInfoShowSessionlist(roomId: String, roomTitle: String, roomType: Int) {
            }

            override fun onInfoShowFirstSessionTitle(roomId: String, roomTitle: String, roomType: Int) {
            }

        }

        fun newInstance(): MapFragment {
            return MapFragment()
        }

        fun newInstance(highlightedRoomId: String): MapFragment {
            val fragment = MapFragment()

            val arguments = Bundle()
            arguments.putString(EXTRAS_HIGHLIGHT_ROOM, highlightedRoomId)
            fragment.arguments = arguments

            return fragment
        }

        fun newInstance(savedState: Bundle): MapFragment {
            val fragment = MapFragment()
            fragment.arguments = savedState
            return fragment
        }
    }

}
