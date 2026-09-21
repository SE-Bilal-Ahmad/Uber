package com.example.uber.presentation.riderpresentation.bottomSheet

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.example.uber.R
import com.example.uber.core.utils.Helper
import com.example.uber.databinding.FragmentRideAcceptedSheetBinding
import com.example.uber.presentation.riderpresentation.map.Routes.RouteCreationHelper
import com.example.uber.presentation.riderpresentation.viewModels.MapAndSheetsSharedViewModel
import com.example.uber.presentation.riderpresentation.viewModels.TripViewModel
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class RideAcceptedSheet : Fragment(R.layout.fragment_ride_accepted_sheet) {
    private val sharedViewModel: MapAndSheetsSharedViewModel by activityViewModels<MapAndSheetsSharedViewModel>()
    private var bottomSheet: LinearLayout? = null
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private val tripViewModel:TripViewModel by activityViewModels<TripViewModel>()
    private var binding:FragmentRideAcceptedSheetBinding? = null
    private var _bottomSheet: GenericBottomSheet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRideAcceptedSheetBinding.inflate(inflater,container,false)
        return binding?.root
    }
    private fun handleBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            requireActivity()
                .findNavController(R.id.nav_host_bottom_sheet)
                .popBackStack()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setBottomSheetStyle()
        setupBottomSheetCallback()
        handleBackPressed()
        observeTimeAndDistanceFromTrip()
        observeDriverReachedPickup()
        observeReachedDropOffSpot()
    }

    private var bounds: LatLngBounds? = null
    private fun setBottomSheetStyle() {
        bottomSheet = requireActivity().findViewById<LinearLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet!!)

        bottomSheet?.layoutParams?.height =
            (requireContext().resources.displayMetrics.heightPixels * 0.70).toInt()
        bottomSheetBehavior?.peekHeight =
            (requireContext().resources.displayMetrics.heightPixels * 0.32).toInt()
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior?.isHideable = false
        bottomSheetBehavior?.isDraggable = true
    }

    private fun setupBottomSheetCallback() {
        bottomSheetBehavior?.addBottomSheetCallback(bottomSheetCallBack)
    }


    fun adjustMapForBottomSheet(slideOffset: Float) {
        bounds = Helper.calculateBounds(RouteCreationHelper.latLngBounds) ?: return
        val totalSheetHeight = bottomSheet?.height
        val mapPaddingBottom = (slideOffset * totalSheetHeight!!).toInt()
        sharedViewModel.setRideOptionsSheetOffsetAndBounds(mapPaddingBottom, bounds!!)
    }

    private val bottomSheetCallBack = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            if (slideOffset == 0.0f || slideOffset == 1f) {
                adjustMapForBottomSheet(slideOffset)
            }
        }
    }

    private fun observeTimeAndDistanceFromTrip(){
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.tripUpdates.collectLatest { trip->
                withContext(Dispatchers.Main) {
                    updateTimeAndDistanceOnSheet(trip.time)
                }
            }
        }
    }

    private fun updateTimeAndDistanceOnSheet(value:Int){
        binding?.timeToReach?.text = Helper.convertSecondsToMinutes(value).toString()
    }

    private fun observeDriverReachedPickup(){
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.driverReachedPickUpSpot().collectLatest {
                if(it.reached){
                    changeDriverStatusText()
                }
            }
        }
    }

    private fun changeDriverStatusText(){
        binding?.driverStatus?.text = "Driver Reached Your Pickup Location"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bottomSheetBehavior?.removeBottomSheetCallback(bottomSheetCallBack)
        bounds = null
        bottomSheet = null
        bottomSheetBehavior = null
        binding = null
    }

    private fun showPaymentSheet(){
        val customView = LayoutInflater.from(requireContext())
            .inflate(R.layout.pay_you_driver_content, null)
        _bottomSheet = GenericBottomSheet.newInstance(customView)
        _bottomSheet?.show(parentFragmentManager, _bottomSheet?.tag)
    }

    private fun observeReachedDropOffSpot(){
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.driverReachedDropOffSpot().collectLatest {
                showPaymentSheet()
            }
        }
    }
}