package com.example.uber.data.remote.api.backend.rider.socket.trip.model

import java.util.UUID

data class TripStarted(val riderId: UUID, val rideId: UUID, val driverId: UUID)
