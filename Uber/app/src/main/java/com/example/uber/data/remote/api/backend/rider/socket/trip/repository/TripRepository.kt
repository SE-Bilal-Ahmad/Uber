package com.example.uber.data.remote.api.backend.rider.socket.trip.repository

import com.example.uber.data.remote.api.backend.rider.socket.ride.model.TripLocation
import com.example.uber.data.remote.api.backend.rider.socket.trip.model.DriverReachedDropOffSpot
import com.example.uber.data.remote.api.backend.rider.socket.trip.model.DriverReachedPickUpSpot
import com.example.uber.data.remote.api.backend.rider.socket.trip.model.TripStarted
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun observeTrip(): Flow<TripLocation>
    fun driverReachedPickUpSpot():Flow<DriverReachedPickUpSpot>
    fun driverReachedDropOffSpot():Flow<DriverReachedDropOffSpot>
    fun observeTripStarted():Flow<TripStarted>
}