package com.example.uber.domain.remote.socket.ride.repository

import android.util.Log
import com.example.uber.core.utils.SocketMethods
import com.example.uber.data.remote.api.backend.rider.socket.ride.model.RideAccepted
import com.example.uber.data.remote.api.backend.rider.socket.ride.model.RideRequest
import com.example.uber.data.remote.api.backend.rider.socket.ride.model.TripLocation
import com.example.uber.data.remote.api.backend.rider.socket.ride.repository.RideRepository
import com.example.uber.data.remote.api.backend.rider.socket.socketBroker.service.SocketBroker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

class RideRepository @Inject constructor(private val socketManager: SocketBroker) : RideRepository {
    private val socketScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rideAccepted = MutableSharedFlow<RideAccepted>()
    override fun sendRideRequest(rideRequest: RideRequest) {
        socketManager.send(rideRequest, SocketMethods.REQUEST_RIDE)
    }

    override fun startObservingRideAcceptedEvent(): Flow<RideAccepted> {
        socketManager.apply {
            getHubConnection()?.let {
                it.on(
                    SocketMethods.RIDE_ACCEPTED,
                    { riderId: String, driverId: String, rideId: String, latitude: Double, longitude: Double ->
                        socketScope.launch {
                            rideAccepted.emit(
                                RideAccepted(
                                    UUID.fromString(riderId),
                                    UUID.fromString(driverId),
                                    UUID.fromString(rideId),
                                    latitude,
                                    longitude
                                )
                            )
                        }
                    },
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Double::class.java,
                    Double::class.java
                )
            }
        }
        return rideAccepted
    }

}