package com.example.uber.domain.remote.socket.trip.usecase

import com.example.uber.data.remote.api.backend.rider.socket.trip.repository.TripRepository
import javax.inject.Inject

class TripStartedUseCase @Inject constructor(private val tripRepository: TripRepository)  {
    operator fun invoke() = tripRepository.observeTripStarted()
}