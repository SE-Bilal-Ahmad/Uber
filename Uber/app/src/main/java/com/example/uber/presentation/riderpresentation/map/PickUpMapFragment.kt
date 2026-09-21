package com.example.uber.presentation.riderpresentation.map


import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.example.uber.R
import com.example.uber.core.enums.SheetState
import com.example.uber.core.interfaces.IActions
import com.example.uber.core.interfaces.utils.mode.CheckMode
import com.example.uber.core.utils.FetchLocation
import com.example.uber.core.utils.permissions.PermissionManagers
import com.example.uber.core.utils.system.SystemInfo
import com.example.uber.data.remote.api.backend.rider.socket.ride.model.RideAccepted
import com.example.uber.databinding.FragmentPickUpMapBinding
import com.example.uber.presentation.riderpresentation.map.Routes.RouteCreationHelper
import com.example.uber.presentation.riderpresentation.map.Routes.TripRoute
import com.example.uber.presentation.riderpresentation.map.utils.ShowNearbyVehicleService
import com.example.uber.presentation.riderpresentation.map.viewmodels.RideViewModel
import com.example.uber.presentation.riderpresentation.viewModels.GoogleViewModel
import com.example.uber.presentation.riderpresentation.viewModels.LocationViewModel
import com.example.uber.presentation.riderpresentation.viewModels.MapAndSheetsSharedViewModel
import com.example.uber.presentation.riderpresentation.viewModels.MapboxViewModel
import com.example.uber.presentation.riderpresentation.viewModels.TripViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.GoogleMap.OnCameraMoveListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.math.abs


@AndroidEntryPoint
class PickUpMapFragment : Fragment(), IActions, OnMapReadyCallback,
    ActivityCompat.OnRequestPermissionsResultCallback {
    private var _binding: FragmentPickUpMapBinding? = null
    private val binding get() = _binding!!
    private var isPopulatingLocation = false
    private val mapboxViewModel: MapboxViewModel by viewModels()
    private val googleViewModel: GoogleViewModel by activityViewModels<GoogleViewModel>()
    private var routeHelper: RouteCreationHelper? = null
    private var _areListenersRegistered = false
    private lateinit var mapFrag: SupportMapFragment
    private lateinit var googleMap: GoogleMap
    private var currentLocation: Location? = null
    private val locationViewModel: LocationViewModel by activityViewModels<LocationViewModel>()
    private val sharedViewModel: MapAndSheetsSharedViewModel by activityViewModels()
    private var nearbyVehicleService: ShowNearbyVehicleService? = null
    private val rideViewModel: RideViewModel by activityViewModels<RideViewModel>()
    private val tripViewModel: TripViewModel by activityViewModels<TripViewModel>()
    private var tripRoute: TripRoute? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPickUpMapBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setBackButtonOnClickListener()
        setUpGoogleMap()
        if (isAdded) {
            setUpCurrentLocationButton()
            requestLocationPermission()
            getInitialPickUpLocation()
        }
        observeConfirmDestinationBtnClicked()
        observeAnnotationClicks()
        observePickUpAndDropInputFocus()
        observeCurrentlyOpenedSheet()
        observeRideOptionsSheetExpanded()
        observeVehicleClicked()
        observeRideAccepted()
    }


    private fun setUpGoogleMap() {
        mapFrag =
            childFragmentManager.findFragmentById(R.id.googleMap) as SupportMapFragment
        mapFrag.getMapAsync(this)
    }

    private fun showUserLocation() {
        checkLocationPermission() {
            FetchLocation.getCurrentLocation(this@PickUpMapFragment, requireContext()) { location ->
                currentLocation = location
                animateCameraToCurrentLocation(location)
            }
        }
    }

    private fun requestLocationPermission() {
        checkLocationPermission {
            showUserLocation()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        onAddCameraAndMoveListeners()
        googleMap.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(
                requireContext(),
                getCurrentMapStyle()
            )
        )
        ifNetworkOrGPSDisabled()
        initializeRouteHelper()
        enableMyLocation()
        initializeNearbyVehicleService()
    }

    private fun enableMyLocation() {
        checkLocationPermission() {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = false
        }
    }

    private fun initializeRouteHelper() {
        routeHelper = RouteCreationHelper(
            WeakReference(googleMap),
            WeakReference(requireContext()),
            WeakReference(googleViewModel),
            WeakReference(sharedViewModel),
            viewLifecycleOwner,
        )
    }

    private fun animateCameraToCurrentLocation(lastKnownLocation: Location?) {
        if (googleMap != null) {
            val userLatLng = lastKnownLocation?.let { LatLng(it.latitude, it.longitude) }
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng!!, 13.0f))
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        storeLocationOffline()
        cleanUpResources()
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }

    private fun setUpCurrentLocationButton() {
        binding.currLocationBtn.setOnClickListener {
            showUserLocation()
            hideUserLocationButton()
        }
    }

    private fun cleanUpResources() {
        _binding = null
        onRemoveCameraAndMoveListener()
        FetchLocation.cleanResources()
        routeHelper = null
        sharedViewModel.setIsDestinationConfirmBtnClicked(false)
        sharedViewModel.cleanData()
        googleViewModel.cleanData()
        ShowNearbyVehicleService.drivers.clear()
    }

    private val cameraMoveListener = OnCameraMoveListener {
        if (sharedViewModel.currentSheet.value == SheetState.PICKUP_SHEET) {

            if (binding.currLocationBtn.visibility != View.VISIBLE) {
                fadeInUserLocationButton()
            }
        }

    }

    private val cameraIdleListener = OnCameraIdleListener {
        if (sharedViewModel.currentSheet.value == SheetState.PICKUP_SHEET) {
            ifNetworkOrGPSDisabled {
                if (!it) {
                    if (!isPopulatingLocation) {
                        fetchLocation()
                    }
                }
            }
            if (routeHelper != null) {
                routeHelper?.onCameraIdle()
            }
        }
    }

    private fun hideUserLocationButton() {
        floatingButtonFadeOutAnimation()
    }


    private fun floatingButtonFadeOutAnimation() {
        binding.currLocationBtn.animate()
            .alpha(0f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.currLocationBtn.visibility = View.GONE
                }
            })
    }

    private fun fadeInUserLocationButton() {
        binding.currLocationBtn.apply {
            visibility = View.VISIBLE
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null)
        }
    }

    private fun getCurrentMapStyle(): Int =
        if (CheckMode.isDarkMode(requireContext())) R.raw.night_map else R.raw.uber_style


    private fun checkLocationPermission( onGranted: () -> Unit) {
        PermissionManagers.requestPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) {
            if (it) {
                onGranted.invoke()
            }
        }
    }

    private fun setBackButtonOnClickListener() {
        binding.backFbn.setOnClickListener {
            routeHelper?.deleteEveryThingOnMap()
            showLocationPickerMarker()
            if (!_areListenersRegistered) {
                onAddCameraAndMoveListeners()
            }
        }
    }


    override fun onBottomSheetSlide(slideOffset: Float) {
        binding.backFbn.apply {
            if (slideOffset == 1.0f) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                alpha = abs(slideOffset - 1.0f)
            }
        }
    }

    override fun onBottomSheetStateChanged(newState: Int) {
    }


    private fun fetchLocation() {
        if (SystemInfo.CheckInternetConnection(requireContext())) {
            runCatching {
                try {
                    if (sharedViewModel.pickUpInputInFocus.value!!) {
                        googleViewModel.setPickUpLocationName(
                            googleMap.cameraPosition.target.latitude,
                            googleMap.cameraPosition.target.longitude
                        )
                    } else if (sharedViewModel.dropOffInputInFocus.value!!) {
                        googleViewModel.setDropOffLocationName(
                            googleMap.cameraPosition.target.latitude,
                            googleMap.cameraPosition.target.longitude
                        )
                    }
                } finally {
                    isPopulatingLocation = false
                }
            }.onFailure {
                Log.e("Location Error", it.message.toString())
            }
        }
    }

    private fun observePickUpAndDropInputFocus() {
        sharedViewModel.apply {
            pickUpInputInFocus.observe(viewLifecycleOwner) {
                if (it) {
                    animateToPickUpLocation()
                }
            }
            dropOffInputInFocus.observe(viewLifecycleOwner) {
                if (it) {
                    animateToDropOffLocation()
                }
            }
        }
    }


    private fun observeAnnotationClicks() {
        sharedViewModel.apply {
            pickUpAnnotationClick.observe(viewLifecycleOwner) {
                if (it) {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.nav_host_bottom_sheet) as? NavHostFragment
                    val navController = navHostFragment?.navController
                    navController?.navigate(R.id.bottomSheetManager)

                }
            }

            dropOffAnnotationClick.observe(viewLifecycleOwner) {
                if (it) {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.nav_host_bottom_sheet) as? NavHostFragment
                    val navController = navHostFragment?.navController
                    navController?.navigate(R.id.bottomSheetManager)
                }
            }
        }
    }

    private fun getInitialPickUpLocation() {
        checkLocationPermission() {
            FetchLocation.getCurrentLocation(
                this@PickUpMapFragment,
                requireContext()
            ) { location ->
                runCatching {
                    googleViewModel.setPickUpLocationName(
                        location!!.latitude, location.longitude
                    )
                    googleViewModel.setDropOffLocationName(
                        location.latitude, location.longitude
                    )
                }
            }

        }


    }

    private fun animateToPickUpLocation() {
        val locationMapper = FetchLocation.customLocationMapper(
            googleViewModel.pickUpLatitude, googleViewModel.pickUpLongitude
        )
        runCatching { animateCameraToCurrentLocation(locationMapper) }

    }

    private fun animateToDropOffLocation() {
        val locationMapper = FetchLocation.customLocationMapper(
            googleViewModel.dropOffLatitude,
            googleViewModel.dropOffLongitude
        )


        runCatching { animateCameraToCurrentLocation(locationMapper) }

    }

    private fun createRoute(
        pickUpLatLng: LatLng? = null,
        dropOffLatLng: LatLng? = null
    ) {
        val pickUp = pickUpLatLng ?: LatLng(
            googleViewModel.pickUpLatitude,
            googleViewModel.pickUpLongitude,
        )
        val dropOff = dropOffLatLng ?: LatLng(
            googleViewModel.dropOffLatitude,
            googleViewModel.dropOffLongitude
        )

        hideLocationPickerMarker()
        routeHelper?.createRoute(
            LatLng(
                pickUp.latitude,
                pickUp.longitude,
            ),
            LatLng(
                dropOff.latitude,
                dropOff.longitude,
            )
        )
        locationViewModel.setPickUpLocation(pickUp)
        sharedViewModel.setCurrentOpenedSheet(SheetState.RIDE_SHEET)
    }

    private fun hideLocationPickerMarker() {
        binding.activityMainCenterLocationPin.visibility = View.GONE
    }

    fun showLocationPickerMarker() {
        if (binding.activityMainCenterLocationPin.visibility != View.VISIBLE)
            binding.activityMainCenterLocationPin.visibility = View.VISIBLE
    }


    fun onAddCameraAndMoveListeners() {

        if (!_areListenersRegistered) {
            _areListenersRegistered = true
            googleMap.setOnCameraIdleListener(cameraIdleListener)
            googleMap.setOnCameraMoveListener(cameraMoveListener)
        }
    }

    private fun onRemoveCameraAndMoveListener() {
        if (_areListenersRegistered) {
            _areListenersRegistered = false
            googleMap.setOnCameraIdleListener(null)
            googleMap.setOnCameraMoveListener(null)
        }
    }

    override fun createRouteAction(
        pickUpLatLng: LatLng?,
        dropOffLatLng: LatLng?
    ) {
        createRoute(pickUpLatLng, dropOffLatLng)
    }

    private fun observeConfirmDestinationBtnClicked() {
        sharedViewModel.apply {
            isBtnDestinationClicked.observe(viewLifecycleOwner) {
                if (it) {
                    createRouteAction(
                        sharedViewModel.pickUpPosition.value,
                        sharedViewModel.dropOffPosition.value
                    )
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.nav_host_bottom_sheet) as? NavHostFragment
                    val navController = navHostFragment?.navController
                    navController?.navigate(R.id.rideOptionsBottomSheet)
                    sharedViewModel.setIsDestinationConfirmBtnClicked(false)
                }
            }
        }
    }


    private fun storeLocationOffline() {
        lifecycleScope.launch {
            async {
                mapboxViewModel.saveCurrentLocationToDB(
                    com.example.uber.data.local.location.entities.Location(
                        location = LatLng(
                            currentLocation?.latitude!!,
                            currentLocation?.longitude!!
                        )
                    )
                )
            }.await()
        }
    }

    private inline fun ifNetworkOrGPSDisabled(dispatcher: (Boolean) -> Unit = {}) {
        val isNetworkOrGPSDisabled = !SystemInfo.isLocationEnabled(requireContext()) ||
                !SystemInfo.CheckInternetConnection(requireContext())

        if (isNetworkOrGPSDisabled) {
            googleViewModel.setLatitudeAndLongitudeIfNoNetworkOrGPS()
            dispatcher(true)
        } else {
            dispatcher(false)
        }
    }

    private fun observeCurrentlyOpenedSheet() {
        sharedViewModel.apply {
            currentSheet.observe(viewLifecycleOwner) {
                when (it) {
                    SheetState.PICKUP_SHEET -> {
                        routeHelper?.deleteEveryThingOnMap()
                        showLocationPickerMarker()
                        if (::googleMap.isInitialized) {
                            onAddCameraAndMoveListeners()
                            googleMap.setPadding(0, 0, 0, 0)
                        }
                    }

                    SheetState.RIDE_ACCEPTED -> {
                        val navHostFragment =
                            childFragmentManager.findFragmentById(R.id.nav_host_bottom_sheet) as? NavHostFragment
                        val navController = navHostFragment?.navController
                        navController?.navigate(R.id.rideAcceptedSheet)
                    }

                    SheetState.RIDE_REQUESTED -> {
                        val navHostFragment =
                            childFragmentManager.findFragmentById(R.id.nav_host_bottom_sheet) as? NavHostFragment
                        val navController = navHostFragment?.navController
                        navController?.navigate(R.id.rideRequestedSheet)
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun observeRideOptionsSheetExpanded() {
        sharedViewModel.apply {
            sheetOffset.observe(viewLifecycleOwner) {
                if (bounds != null && ::googleMap.isInitialized && (currentSheet.value == SheetState.RIDE_SHEET || currentSheet.value == SheetState.VEHICLE_SHEET || currentSheet.value == SheetState.RIDE_REQUESTED)) {
                    googleMap.setPadding(0, 0, 0, it)
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            bounds!!,
                            100
                        )
                    )
                }
            }
        }
    }

    private fun initializeNearbyVehicleService() {
        nearbyVehicleService =
            ShowNearbyVehicleService(this, WeakReference(requireContext()), locationViewModel)
        nearbyVehicleService?.startObservingNearbyVehicles(WeakReference(googleMap))
    }

    private fun observeRideAccepted() {

        viewLifecycleOwner.lifecycleScope.launch {
            rideViewModel.rideAccept.collectLatest {
                it?.let {a->
                    sharedViewModel.setCurrentOpenedSheet(SheetState.RIDE_ACCEPTED)
                    routeHelper?.deleteEveryThingOnMap()
                    tripViewModel.setPickUpLocation(LatLng(googleViewModel.pickUpLatitude,googleViewModel.pickUpLongitude))
                    tripViewModel.setDropOffLocation(LatLng(googleViewModel.dropOffLatitude,googleViewModel.dropOffLongitude))
                    startTrip(a)
                }
            }
        }
    }

    private fun observeVehicleClicked() {
        sharedViewModel.apply {
            vehicleSelect.observe(viewLifecycleOwner) {
                if (it != null) {
                    nearbyVehicleService?.onCarItemListClickListener(it)
                }
            }
        }
    }

    private fun startTrip(ride: RideAccepted) {
        tripRoute = TripRoute(
            WeakReference(googleMap),
            this,
            locationViewModel,
            rideViewModel,
            tripViewModel,
            WeakReference(requireContext())
        )
        locationViewModel?.pickUpLocation?.let {
            ride?.let { r ->
                tripRoute?.createRoute(
                    LatLng(it.latitude, it.longitude),
                    LatLng(r.latitude, r.longitude)
                )
            }
        }
    }

    private fun getContinuousLocationUpdates(){
        checkLocationPermission {
            viewLifecycleOwner.lifecycleScope.launch {
                FetchLocation.getLocationUpdates(requireContext()).collect {
                    animateCameraToCurrentLocation(it)
                }
            }
        }
    }

}






