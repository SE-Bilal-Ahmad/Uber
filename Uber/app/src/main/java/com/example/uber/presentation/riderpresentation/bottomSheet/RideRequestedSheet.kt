package com.example.uber.presentation.riderpresentation.bottomSheet

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.uber.R
import com.example.uber.core.enums.SheetState
import com.example.uber.core.utils.Helper
import com.example.uber.databinding.FragmentRideRequestedSheetBinding
import com.example.uber.presentation.riderpresentation.map.Routes.RouteCreationHelper
import com.example.uber.presentation.riderpresentation.map.viewmodels.RideViewModel
import com.example.uber.presentation.riderpresentation.viewModels.MapAndSheetsSharedViewModel
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask


class RideRequestedSheet : Fragment(R.layout.fragment_ride_requested_sheet) {
    private val sharedViewModel: MapAndSheetsSharedViewModel by activityViewModels<MapAndSheetsSharedViewModel>()
    private var bottomSheet: LinearLayout? = null
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private val rideViewModel: RideViewModel by activityViewModels<RideViewModel>()
    private var binding: FragmentRideRequestedSheetBinding? = null

    val images = mutableListOf(
        R.drawable.undraw_delivery_location_um5t,
        R.drawable.undraw_destination_fkst,
        R.drawable.undraw_my_location_dcug,
        R.drawable.undraw_order_ride_4gaq
    )

    val views = mutableListOf(binding?.v1, binding?.v2, binding?.v3, binding?.v4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentRideRequestedSheetBinding.inflate(inflater, container, false)
        return binding?.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setBottomSheetStyle()
        handleBackPressed()
        rideViewModel.startObservingRideRequestAccepted()
        setInterval()
    }

    private fun handleBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            sharedViewModel.setCurrentOpenedSheet(SheetState.VEHICLE_SHEET)
            requireActivity()
                .findNavController(R.id.nav_host_bottom_sheet)
                .popBackStack()
        }
    }

    private var bounds: LatLngBounds? = null
    private fun setBottomSheetStyle() {
        bottomSheet = requireActivity().findViewById<LinearLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet!!)

        bottomSheet?.layoutParams?.height =
            (requireContext().resources.displayMetrics.heightPixels * 0.32).toInt()
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheetBehavior?.isHideable = false
        bottomSheetBehavior?.isDraggable = false
        adjustMapForBottomSheet(
            Helper.calculateSheetOffSet(
                (bottomSheet!!.parent as View).height,
                bottomSheetBehavior!!.peekHeight,
                bottomSheet!!.top
            )
        )
    }


    private fun adjustMapForBottomSheet(slideOffset: Float) {
        bounds = Helper.calculateBounds(RouteCreationHelper.latLngBounds) ?: return
        val totalSheetHeight = bottomSheet?.height
        val mapPaddingBottom = (slideOffset * totalSheetHeight!!).toInt()
        sharedViewModel.setRideOptionsSheetOffsetAndBounds(mapPaddingBottom, bounds!!)
    }

    private fun setInterval() {
        var index = 0
        if(isAdded) {
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    if (index < images.size) {
                        Handler(Looper.getMainLooper()).post {
                            binding?.ivRideRequestImage?.let {

                                if(isAdded) {
                                    Glide.with(this@RideRequestedSheet)
                                        .load(images[index])
                                        .transition(DrawableTransitionOptions.withCrossFade(500))
                                        .into(it)
                                    views[index]?.setBackgroundColor(
                                        ContextCompat.getColor(
                                            requireContext(),
                                            R.color.royal_blue
                                        )
                                    )
                                }
                            }
                        }
                        index += 1;
                    }
                    if (index == images.size) index = 0
                }
            }, 0, 2000)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        bounds = null
        bottomSheet = null
        bottomSheetBehavior = null
    }



}