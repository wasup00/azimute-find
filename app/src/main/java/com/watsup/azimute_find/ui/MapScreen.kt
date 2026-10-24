package com.watsup.azimute_find.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.watsup.azimute_find.MainViewModel
import com.watsup.azimute_find.geo.destinationPoint
import com.watsup.azimute_find.ui.theme.azimuthColor
import kotlinx.coroutines.launch

private const val AZIMUTH_LINE_LENGTH_M = 10_000.0
private val DEFAULT_CAMERA = CameraPosition.fromLatLngZoom(LatLng(50.0614, 19.9366), 15f) // Kraków
private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
private val MAP_UI_SETTINGS = MapUiSettings(
    myLocationButtonEnabled = false,
    compassEnabled = false,
    zoomControlsEnabled = false,
    mapToolbarEnabled = false,
    rotationGesturesEnabled = true,
    tiltGesturesEnabled = false,
)

@Composable
fun MapScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Heading changes ~50 Hz: keep the State object and read it lazily in the children that draw it,
    // so this screen only recomposes when the low-accuracy flag flips.
    val headingState = vm.heading.collectAsStateWithLifecycle()
    val heading = { headingState.value.trueHeading }
    val lowAccuracy by remember { derivedStateOf { headingState.value.lowAccuracy } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> vm.onPermissionResult(result.values.any { it }) }

    // Re-checked on every resume, so granting (or revoking) it in system settings applies on return.
    // The system dialog is shown only once per screen; after that the banner points to settings.
    var askedForPermission by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        val granted = LOCATION_PERMISSIONS
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        vm.onPermissionResult(granted)
        if (!granted && !askedForPermission) {
            askedForPermission = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
        onPauseOrDispose { }
    }

    // The user drives the camera; the app only moves it on request (first fix, recenter, north-up).
    val cameraPositionState = rememberCameraPositionState { position = DEFAULT_CAMERA }

    // Jump to the first GPS fix once, so the app doesn't open on the default city.
    var centeredOnFirstFix by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.myLocation != null) {
        val loc = state.myLocation
        if (loc != null && !centeredOnFirstFix && !state.isLocked) {
            centeredOnFirstFix = true
            cameraPositionState.move(CameraUpdateFactory.newLatLng(loc))
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = remember(state.mapType, state.hasLocationPermission) {
                MapProperties(mapType = state.mapType, isMyLocationEnabled = state.hasLocationPermission)
            },
            uiSettings = MAP_UI_SETTINGS,
        ) {
            state.lockedPoint?.let { locked ->
                Marker(state = remember(locked) { MarkerState(locked) }, title = "Locked point")
            }
            // Reading the camera here recomposes only the map content, not the whole screen.
            val origin = state.lineOrigin(mapCenter = cameraPositionState.position.target)
            state.azimuths.forEach { az ->
                val end = destinationPoint(origin.latitude, origin.longitude, az.degrees.toDouble(), AZIMUTH_LINE_LENGTH_M)
                Polyline(
                    points = listOf(origin, LatLng(end.lat, end.lon)),
                    color = azimuthColor(az.slot),
                    width = 8f,
                    geodesic = true,
                )
            }
        }

        // Replaces the crosshair: the rose's center is the map center ("lock at map center" target).
        CompassRose(
            heading = heading,
            azimuths = state.azimuths,
            modifier = Modifier.align(Alignment.Center),
        )

        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (lowAccuracy) CalibrationBanner(Modifier.fillMaxWidth())
            if (!vm.compassAvailable) {
                InfoBanner("This device has no rotation-vector sensor, so the compass is unavailable.")
            }
            if (!state.hasLocationPermission) {
                InfoBanner("Location permission is off. Grant it in system settings to show your position and lock at it.")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeadingReadout(heading)
                LayerSwitcher(selected = state.mapType, onSelect = vm::setMapType)
            }
        }

        MapControls(
            isLocked = state.isLocked,
            showRecenter = state.recenterTarget != null,
            canLockAtMyPosition = state.myLocation != null,
            mapBearing = { cameraPositionState.position.bearing },
            azimuths = state.azimuths,
            onRecenter = {
                state.recenterTarget?.let { target ->
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLng(target)) }
                }
            },
            onNorthUp = {
                val northUp = CameraPosition.builder(cameraPositionState.position).bearing(0f).build()
                scope.launch { cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(northUp)) }
            },
            onLockHere = vm::lockAtMyPosition,
            onLockAtCenter = { vm.lockAt(cameraPositionState.position.target) },
            onUnlock = vm::unlock,
            onAddManual = vm::addAzimuth,
            onRemove = vm::removeAzimuth,
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        )
    }
}
