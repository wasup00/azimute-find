package com.watsup.azimute_find.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

const val EARTH_RADIUS_M = 6_371_000.0

data class GeoPoint(val lat: Double, val lon: Double)

/** Point reached by travelling [distanceM] from ([lat], [lon]) along initial [bearingDeg] (great circle). */
fun destinationPoint(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): GeoPoint {
    val delta = distanceM / EARTH_RADIUS_M
    val theta = Math.toRadians(bearingDeg)
    val phi1 = Math.toRadians(lat)
    val lambda1 = Math.toRadians(lon)

    val phi2 = asin(sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta))
    val lambda2 = lambda1 + atan2(
        sin(theta) * sin(delta) * cos(phi1),
        cos(delta) - sin(phi1) * sin(phi2),
    )
    return GeoPoint(Math.toDegrees(phi2), normalizeLon(Math.toDegrees(lambda2)))
}

/** Wraps any angle into [0, 360). */
fun normalizeDeg(deg: Float): Float = ((deg % 360f) + 360f) % 360f

private fun normalizeLon(lon: Double): Double = ((lon + 540.0) % 360.0) - 180.0

/** Signed smallest rotation from [from] to [to], in (-180, 180]. */
fun shortestAngleDelta(from: Float, to: Float): Float {
    val d = normalizeDeg(to - from)
    return if (d > 180f) d - 360f else d
}

/**
 * Exponential low-pass on an angle. Filters the unit vector instead of the raw degrees,
 * so 359° -> 1° moves by 2° rather than swinging through 180°.
 */
fun lowPassAngle(previous: Float?, new: Float, alpha: Float): Float {
    if (previous == null) return normalizeDeg(new)
    val p = Math.toRadians(previous.toDouble())
    val n = Math.toRadians(new.toDouble())
    val x = (1 - alpha) * cos(p) + alpha * cos(n)
    val y = (1 - alpha) * sin(p) + alpha * sin(n)
    return normalizeDeg(Math.toDegrees(atan2(y, x)).toFloat())
}
