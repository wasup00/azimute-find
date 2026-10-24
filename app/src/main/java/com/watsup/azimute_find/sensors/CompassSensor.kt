package com.watsup.azimute_find.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.watsup.azimute_find.geo.normalizeDeg
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

data class CompassReading(
    /** Degrees clockwise from magnetic north, [0, 360). */
    val magneticHeading: Float,
    /** One of SensorManager.SENSOR_STATUS_*. */
    val accuracy: Int,
)

class CompassSensor(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isAvailable: Boolean get() = rotationSensor != null

    /** Registers the listener while collected, unregisters on cancellation. */
    fun readings(): Flow<CompassReading> {
        val sensor = rotationSensor ?: return emptyFlow()
        return callbackFlow {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    trySend(CompassReading(normalizeDeg(azimuth), event.accuracy))
                }

                override fun onAccuracyChanged(s: Sensor, newAccuracy: Int) = Unit // also on every event
            }

            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { sensorManager.unregisterListener(listener) }
        }
    }
}
