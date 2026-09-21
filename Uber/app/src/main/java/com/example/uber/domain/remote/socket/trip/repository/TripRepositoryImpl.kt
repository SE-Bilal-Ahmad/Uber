package com.example.uber.domain.remote.socket.trip.repository

import com.example.uber.core.utils.SocketMethods
import com.example.uber.data.remote.api.backend.rider.socket.ride.model.TripLocation
import com.example.uber.data.remote.api.backend.rider.socket.socketBroker.service.SocketBroker
import com.example.uber.data.remote.api.backend.rider.socket.trip.model.DriverReachedDropOffSpot
import com.example.uber.data.remote.api.backend.rider.socket.trip.model.DriverReachedPickUpSpot
import com.example.uber.data.remote.api.backend.rider.socket.trip.model.TripStarted
import com.example.uber.data.remote.api.backend.rider.socket.trip.repository.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

class TripRepositoryImpl @Inject constructor(private val socketManager: SocketBroker) :
    TripRepository {
    private val socketScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tripLocations = MutableSharedFlow<TripLocation>()
    private val reachedPickUpSpot = MutableSharedFlow<DriverReachedPickUpSpot>()
    private val reachedDropOffSpot = MutableSharedFlow<DriverReachedDropOffSpot>()
    private val tripStarted = MutableSharedFlow<TripStarted>()

    override fun observeTrip(): Flow<TripLocation> {
        socketManager.apply {
            getHubConnection()?.let {
                it.on(
                    SocketMethods.TRIP_UPDATES,
                    { rideId: String, driverId: String, latitude: Double, longitude: Double, time: Int, distance: Int ->
                        socketScope.launch {
                            tripLocations.emit(
                                TripLocation(
                                    UUID.fromString(rideId),
                                    UUID.fromString(driverId),
                                    latitude,
                                    longitude,
                                    time,
                                    distance
                                )
                            )
                        }
                    },
                    String::class.java,
                    String::class.java,
                    Double::class.java,
                    Double::class.java,
                    Int::class.java,
                    Int::class.java
                )
            }
        }
        return tripLocations
    }

    override fun driverReachedPickUpSpot(): Flow<DriverReachedPickUpSpot> {
        socketManager.apply {
            getHubConnection()?.let {
                it.on(
                    SocketMethods.DRIVER_REACHED_PICKUP_SPOT,
                    { riderId: String, driverId: String, rideId: String, reached: Boolean ->
                        socketScope.launch {
                            reachedPickUpSpot.emit(
                                DriverReachedPickUpSpot(
                                    UUID.fromString(riderId),
                                    UUID.fromString(driverId),
                                    UUID.fromString(rideId),
                                    reached
                                )
                            )
                        }
                    },
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Boolean::class.java,
                )
            }
        }
        return reachedPickUpSpot
    }

    override fun driverReachedDropOffSpot(): Flow<DriverReachedDropOffSpot> {
        socketManager.apply {
            getHubConnection()?.let {
                it.on(
                    SocketMethods.DRIVER_REACHED_DROPOFF_SPOT,
                    { riderId: String, driverId: String, rideId: String, reached: Boolean ->
                        socketScope.launch {
                            reachedDropOffSpot.emit(
                                DriverReachedDropOffSpot(
                                    UUID.fromString(riderId),
                                    UUID.fromString(driverId),
                                    UUID.fromString(rideId),
                                    reached
                                )
                            )
                        }
                    },
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Boolean::class.java,
                )
            }
        }
        return reachedDropOffSpot
    }

    override fun observeTripStarted(): Flow<TripStarted>{
        socketManager.apply {
            getHubConnection()?.let {
                it.on(
                    SocketMethods.TRIP_STARTED,
                    { riderId: String, rideId: String, driverId: String ->
                        socketScope.launch {
                            tripStarted.emit(
                                TripStarted(
                                    UUID.fromString(riderId),
                                    UUID.fromString(rideId),
                                    UUID.fromString(driverId)
                                )
                            )
                        }
                    },
                    String::class.java,
                    String::class.java,
                    String::class.java,
                )
            }
        }
        return tripStarted
    }
}