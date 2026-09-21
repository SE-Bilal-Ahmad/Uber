package com.example.uber.presentation.riderpresentation.map.Routes

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.uber.R
import com.example.uber.core.utils.BitMapCreator
import com.example.uber.core.utils.HRMarkerAnimation
import com.example.uber.core.utils.PolyUtilExtension
import com.example.uber.data.remote.api.backend.rider.socket.ride.model.TripLocation
import com.example.uber.presentation.riderpresentation.map.viewmodels.RideViewModel
import com.example.uber.presentation.riderpresentation.viewModels.LocationViewModel
import com.example.uber.presentation.riderpresentation.viewModels.TripViewModel
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import dagger.hilt.android.internal.managers.ViewComponentManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class TripRoute(
    private val googleMap: WeakReference<GoogleMap>,
    private val viewLifecycleOwner: LifecycleOwner,
    private val locationViewModel: LocationViewModel,
    private val rideViewModel: RideViewModel,
    private val tripViewModel: TripViewModel,
    private val context: WeakReference<Context>
) {
    private var driverMarker: Marker? = null
    private var oldLocation: Location? = null
    private var mLastLocation: Location? = null
    private var riderPickUpLocation: LatLng? = null
    private var polylineOptions: PolylineOptions? = null

    init {
        observeDirectionsResponse()
        observeTripUpdates()
        observeTripStarted()
        startObservingTripUpdates()
    }

    fun createRoute(
        pickUpLocation: LatLng,
        driverInitialLocation: LatLng
    ) {
        riderPickUpLocation = pickUpLocation
        tripViewModel.directionsRequest(pickUpLocation, driverInitialLocation)

        addMarker(driverInitialLocation)
    }

    private fun observeDirectionsResponse() {
        tripViewModel?.run {
            viewLifecycleOwner.lifecycleScope.launch {
                directions.collectLatest {
                    it?.data?.let { a ->
                        if (a.routes.isNotEmpty()) {
                            createRoute(a.routes[0].overview_polyline.points)
                        }
                    }
                }
            }
        }
    }

    private var routePoints: List<LatLng>? = null
    private var polyline: Polyline? = null

    private fun createRoute(line: String) {
        val routePoints: List<LatLng> = PolyUtil.decode(line)
        if (routePoints.size > 1) {
            this.routePoints = routePoints
            polyline?.remove()
            polylineOptions = PolylineOptions()
                .width(5f)
                .color(Color.BLUE)

            polylineOptions?.let {
                it.addAll(routePoints)
                polyline = googleMap.get()?.addPolyline(it)
            }
            pickUpMarker()
            addAnnotation()
        }
    }

    private fun addMarker(driverInitialLocation: LatLng) {
//        driverMarker = googleMap.get()?.addMarker(
//            MarkerOptions().position(
//                LatLng(
//                    driverInitialLocation.latitude,
//                    driverInitialLocation.longitude
//                )
//            )
//        )
    }

    private fun animateMarker() {
        HRMarkerAnimation(
            googleMap.get(), 1000
        ) { updatedLocation -> oldLocation = updatedLocation }.animateMarker(
            mLastLocation,
            oldLocation,
            driverMarker
        )
    }


    private var tripJob: Job? = null
    private fun observeTripUpdates() {
        tripJob = viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.apply {
                tripUpdates.collectLatest { a ->
                    mLastLocation = Location("").apply {
                        latitude = a.latitude
                        longitude = a.longitude
                    }
                    animateMarker()
                    checkIfDriverLocationOnRoute(a)
//                    removeTravelledPolyLine()
                }
            }
        }
    }

    private fun startObservingTripUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.observeTripLocation()
        }
    }

    private fun checkIfDriverLocationOnRoute(trip: TripLocation) {

        if (routePoints != null && !PolyUtil.isLocationOnPath(
                LatLng(trip.latitude, trip.longitude),
                routePoints,
                true,
                50.0
            )
        ) {
            tripViewModel.directionsRequest(
                LatLng(trip.latitude, trip.longitude),
                riderPickUpLocation!!,
            )
        }
    }

    private fun removeTravelledPolyLine() {
        routePoints?.let {
            mLastLocation?.let { a ->
                val (index, closestPoint) = PolyUtilExtension.getNearestPointOnRoute(
                    LatLng(
                        a.latitude,
                        a.longitude
                    ), it
                )
                val trimmedPoints = routePoints?.subList(0, index)

                trimmedPoints?.let { b ->
                    polyline?.points = b
                }
            }
        }
    }

    fun clear() {
        polylineOptions = null
        polyline = null
        mLastLocation = null
        oldLocation = null
        driverMarker = null
    }

    private fun addAnnotation() {
        val marker_view: View =
            (context.get()
                ?.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(
                R.layout.custom_marker,
                null
            )
        val addressSrc =
            marker_view.findViewById<View>(com.example.uber.R.id.addressTxt) as TextView
        addressSrc.text = "Pickup Spot"

        riderPickUpLocation?.let {
            val marker_opt_source = MarkerOptions().position(
                LatLng(
                    it.latitude,
                    it.longitude
                )
            )

            marker_opt_source.icon(
                BitmapDescriptorFactory.fromBitmap(
                    createDrawableFromView(
                        context.get()!!,
                        marker_view
                    )
                )
            ).anchor(0.00f, 0.20f);
            googleMap.get()?.addMarker(marker_opt_source);
        }
    }


    private fun createDrawableFromView(context: Context, view: View): Bitmap {
        val mContext =
            if (context is ViewComponentManager.FragmentContextWrapper) context.baseContext else context
        val displayMetrics = DisplayMetrics()
        (mContext as Activity).windowManager.defaultDisplay.getMetrics(displayMetrics)
        view.layoutParams =
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
        view.measure(displayMetrics.widthPixels, displayMetrics.heightPixels)
        view.layout(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
        view.buildDrawingCache()
        val bitmap =
            Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)
        view.draw(canvas)

        return bitmap
    }

    private fun pickUpMarker() {
        riderPickUpLocation?.let {
            googleMap.get()?.addMarker(
                MarkerOptions()
                    .position(
                        LatLng(
                            it.latitude,
                            it.longitude
                        )
                    )
                    .icon(
                        BitMapCreator.bitmapDescriptorFromVector(
                            context.get()!!,
                            R.drawable.circle
                        )
                    )
            )
        }

    }

    private fun observeTripStarted() {
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.tripStarted().collectLatest {
                it.let {
                    if (tripViewModel.pickUp != null && tripViewModel.dropOff != null) {
                        createRoute(tripViewModel.pickUp!!, tripViewModel.dropOff!!)
                    }
                }

            }
        }
    }


}