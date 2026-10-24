package com.watsup.azimute_find.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.maps.android.compose.MapType
import com.watsup.azimute_find.Azimuth
import com.watsup.azimute_find.MAX_AZIMUTHS
import com.watsup.azimute_find.geo.shortestAngleDelta
import com.watsup.azimute_find.ui.theme.azimuthColor
import java.util.Locale
import kotlin.math.abs

private fun formatDeg(deg: Float) = String.format(Locale.US, "%.1f°", deg)

@Composable
fun MapControls(
    isLocked: Boolean,
    showRecenter: Boolean,
    canLockAtMyPosition: Boolean,
    /** Read lazily so camera moves don't recompose the controls. */
    mapBearing: () -> Float,
    azimuths: List<Azimuth>,
    onRecenter: () -> Unit,
    onNorthUp: () -> Unit,
    onLockHere: () -> Unit,
    onLockAtCenter: () -> Unit,
    onUnlock: () -> Unit,
    onAddManual: (Float) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    var lockMenuOpen by remember { mutableStateOf(false) }
    val mapRotated by remember { derivedStateOf { abs(shortestAngleDelta(0f, mapBearing())) > 0.5f } }

    Box(modifier) {
        BadgedBox(
            badge = { if (azimuths.isNotEmpty()) Badge { Text(azimuths.size.toString()) } },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            SmallFloatingActionButton(onClick = { showSheet = true }) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Azimuth list")
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (mapRotated) {
                SmallFloatingActionButton(onClick = onNorthUp) {
                    // Arrow points at map north, like Google Maps' compass button.
                    Icon(
                        Icons.Filled.Navigation,
                        contentDescription = "Reset map to north up",
                        modifier = Modifier.graphicsLayer { rotationZ = -mapBearing() },
                    )
                }
            }

            if (showRecenter) {
                SmallFloatingActionButton(onClick = onRecenter) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = if (isLocked) "Go to locked point" else "Center on my location",
                    )
                }
            }

            Box {
                SmallFloatingActionButton(
                    onClick = { if (isLocked) onUnlock() else lockMenuOpen = true },
                    containerColor = if (isLocked) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = if (isLocked) "Unlock position" else "Lock position",
                    )
                }
                DropdownMenu(expanded = lockMenuOpen, onDismissRequest = { lockMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Lock at my position") },
                        leadingIcon = { Icon(Icons.Filled.MyLocation, null) },
                        enabled = canLockAtMyPosition,
                        onClick = { lockMenuOpen = false; onLockHere() },
                    )
                    DropdownMenuItem(
                        text = { Text("Lock at map center") },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        onClick = { lockMenuOpen = false; onLockAtCenter() },
                    )
                }
            }
        }
    }

    if (showSheet) {
        AzimuthSheet(
            azimuths = azimuths,
            onAdd = onAddManual,
            onRemove = onRemove,
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AzimuthSheet(
    azimuths: List<Azimuth>,
    onAdd: (Float) -> Unit,
    onRemove: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val full = azimuths.size >= MAX_AZIMUTHS
    val parsed = input.replace(',', '.').toFloatOrNull()?.takeIf { it >= 0f && it < 360f }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Azimuths (${azimuths.size}/$MAX_AZIMUTHS)", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(if (full) "Maximum $MAX_AZIMUTHS azimuths" else "Degrees (0–359.9)") },
                    enabled = !full,
                    isError = input.isNotEmpty() && parsed == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Button(enabled = parsed != null && !full, onClick = { parsed?.let(onAdd); input = "" }) {
                    Text("Add")
                }
            }
            Spacer(Modifier.size(8.dp))
            if (azimuths.isEmpty()) {
                Text(
                    "No azimuths yet. Type a bearing in degrees and tap Add.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            LazyColumn {
                items(azimuths, key = { it.id }) { az ->
                    ListItem(
                        leadingContent = {
                            Box(Modifier.size(16.dp).background(azimuthColor(az.slot), CircleShape))
                        },
                        headlineContent = { Text(formatDeg(az.degrees)) },
                        trailingContent = {
                            IconButton(onClick = { onRemove(az.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
fun LayerSwitcher(selected: MapType, onSelect: (MapType) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val options = listOf(
        MapType.HYBRID to "Hybrid",
        MapType.SATELLITE to "Satellite",
        MapType.TERRAIN to "Terrain",
        MapType.NORMAL to "Map",
    )
    Box(modifier) {
        FilledTonalIconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Layers, contentDescription = "Map layers")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (type, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { RadioButton(selected = type == selected, onClick = null) },
                    onClick = { open = false; onSelect(type) },
                )
            }
        }
    }
}

@Composable
fun CalibrationBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null)
            Spacer(Modifier.width(12.dp))
            Text(
                "Compass accuracy is low. Move the phone in a figure-8 to calibrate, away from metal.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
