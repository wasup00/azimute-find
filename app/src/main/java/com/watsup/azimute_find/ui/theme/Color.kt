package com.watsup.azimute_find.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
// One color per azimuth slot (MAX_AZIMUTHS). No red: that is the compass north marker.
private val AzimuthPalette = listOf(
    Color(0xFFFFD740),
    Color(0xFF40C4FF),
    Color(0xFF69F0AE),
    Color(0xFFE040FB),
)

/** Same color on the compass and on the map for a given azimuth slot. */
fun azimuthColor(slot: Int): Color = AzimuthPalette[slot.mod(AzimuthPalette.size)]
