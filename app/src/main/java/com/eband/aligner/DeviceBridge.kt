package com.eband.aligner

import android.content.Context
import android.content.Intent
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.webkit.JavascriptInterface
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.max

/**
 * Phone sensors for the web UI:
 *  - direction the BACK CAMERA points (azimuth from magnetic north, elevation above horizon),
 *    computed from the rotation-vector sensor so it works in any phone orientation
 *  - compass calibration status
 *  - magnetic declination for a location (Android's built-in World Magnetic Model)
 *  - real field of view of a camera lens
 */
class DeviceBridge(
    private val context: Context,
    private val onMaxBrightness: (Boolean) -> Unit = {}
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rot = FloatArray(9)

    @Volatile private var azimuth = Double.NaN
    @Volatile private var elevation = Double.NaN
    @Volatile private var magAccuracy = -1
    @Volatile private var headingAccuracyDeg = Double.NaN

    fun start() {
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rot, event.values)
        // rot maps device axes to world (x = East, y = North, z = Up).
        // The back camera looks along the device's -Z axis.
        val east = -rot[2].toDouble()
        val north = -rot[5].toDouble()
        val up = (-rot[8].toDouble()).coerceIn(-1.0, 1.0)
        var az = Math.toDegrees(atan2(east, north))
        if (az < 0) az += 360.0
        azimuth = az
        elevation = Math.toDegrees(asin(up))
        if (event.values.size > 4 && event.values[4] >= 0f) {
            headingAccuracyDeg = Math.toDegrees(event.values[4].toDouble())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magAccuracy = accuracy
    }

    @JavascriptInterface
    fun getOrientation(): String {
        val o = JSONObject()
        o.put("az", if (azimuth.isNaN()) JSONObject.NULL else azimuth)
        o.put("el", if (elevation.isNaN()) JSONObject.NULL else elevation)
        o.put("acc", magAccuracy)
        o.put("hacc", if (headingAccuracyDeg.isNaN()) JSONObject.NULL else headingAccuracyDeg)
        return o.toString()
    }

    @JavascriptInterface
    fun getDeclination(lat: Double, lon: Double, altM: Double): Double =
        GeomagneticField(lat.toFloat(), lon.toFloat(), altM.toFloat(), System.currentTimeMillis())
            .declination.toDouble()

    /** Long-edge field of view (degrees) of a camera2 id, or -1 if unknown. */
    @JavascriptInterface
    fun getCameraFov(cameraId: String): Double = try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ch = cm.getCameraCharacteristics(cameraId)
        val size = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val f = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        if (size == null || f == null || f <= 0f) -1.0
        else Math.toDegrees(2.0 * atan(max(size.width, size.height) / (2.0 * f)))
    } catch (e: Exception) {
        -1.0
    }

    /** Full screen brightness while the app is in front (the phone's own setting is not changed). */
    @JavascriptInterface
    fun setMaxBrightness(on: Boolean) = onMaxBrightness(on)

    @JavascriptInterface
    fun openExternal(url: String) {
        if (!url.startsWith("https://")) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (e: Exception) { }
    }
}
