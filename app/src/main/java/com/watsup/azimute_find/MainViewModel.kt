package com.watsup.azimute_find

import android.annotation.SuppressLint
import android.app.Application
import android.hardware.GeomagneticField
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapType
import com.watsup.azimute_find.geo.lowPassAngle
import com.watsup.azimute_find.geo.normalizeDeg
import com.watsup.azimute_find.location.LocationRepository
import com.watsup.azimute_find.sensors.CompassSensor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

const val MAX_AZIMUTHS = 4

/** [slot] is a color index in 0 until [MAX_AZIMUTHS], unique among the current azimuths. */
data class Azimuth(val id: Long, val degrees: Float, val slot: Int)

data class HeadingState(
    /** Filtered heading in degrees clockwise from true north, [0, 360); null until the first reading. */
    val trueHeading: Float? = null,
    val lowAccuracy: Boolean = false,
)

data class UiState(
    val myLocation: LatLng? = null,
    val lockedPoint: LatLng? = null,
    val azimuths: List<Azimuth> = emptyList(),
    val mapType: MapType = MapType.HYBRID,
    val hasLocationPermission: Boolean = false,
) {
    val isLocked: Boolean get() = lockedPoint != null

    /** Where the recenter button goes: the locked point, else my position. */
    val recenterTarget: LatLng? get() = lockedPoint ?: myLocation

    /** Azimuth lines start at the locked point, else at the map center. */
    fun lineOrigin(mapCenter: LatLng): LatLng = lockedPoint ?: mapCenter
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val compass = CompassSensor(app)
    private val locationRepo = LocationRepository(app)

    val compassAvailable: Boolean = compass.isAvailable

    /** Everything the user sets; [state] adds the live location on top. */
    private val userState = MutableStateFlow(UiState())
    private var nextAzimuthId = 0L

    @SuppressLint("MissingPermission") // only collected once permission is granted
    private val location = userState
        .map { it.hasLocationPermission }
        .distinctUntilChanged()
        .flatMapLatest { granted -> if (granted) locationRepo.locationUpdates() else emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Magnetic declination at the current fix; 0 until we have one. */
    private val declination = location
        .map { loc ->
            loc?.let {
                GeomagneticField(
                    it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), System.currentTimeMillis(),
                ).declination
            } ?: 0f
        }
        .distinctUntilChanged()

    val heading: StateFlow<HeadingState> = combine(compass.readings(), declination) { reading, decl ->
        reading to decl
    }
        .scan(HeadingState()) { prev, (reading, decl) ->
            HeadingState(
                trueHeading = lowPassAngle(prev.trueHeading, reading.magneticHeading + decl, HEADING_ALPHA),
                lowAccuracy = reading.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeadingState())

    val state: StateFlow<UiState> = combine(userState, location) { ui, loc ->
        ui.copy(myLocation = loc?.let { LatLng(it.latitude, it.longitude) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onPermissionResult(granted: Boolean) = userState.update { it.copy(hasLocationPermission = granted) }

    fun lockAt(point: LatLng) = userState.update { it.copy(lockedPoint = point) }

    fun lockAtMyPosition() {
        state.value.myLocation?.let(::lockAt)
    }

    fun unlock() = userState.update { it.copy(lockedPoint = null) }

    fun addAzimuth(degrees: Float) {
        val current = userState.value.azimuths
        val slot = (0 until MAX_AZIMUTHS).firstOrNull { s -> current.none { it.slot == s } } ?: return
        val azimuth = Azimuth(id = nextAzimuthId++, degrees = normalizeDeg(degrees), slot = slot)
        userState.update { it.copy(azimuths = it.azimuths + azimuth) }
    }

    fun removeAzimuth(id: Long) = userState.update { ui -> ui.copy(azimuths = ui.azimuths.filterNot { it.id == id }) }

    fun setMapType(type: MapType) = userState.update { it.copy(mapType = type) }

    private companion object {
        /** Low-pass weight of each new compass sample (lower = smoother, laggier). */
        const val HEADING_ALPHA = 0.15f
    }
}
