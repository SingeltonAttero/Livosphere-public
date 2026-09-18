package app.livosphere.wallpapers.neon

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.view.WindowManager

/** Called only on the wallpaper's render handler. Never uses a wake-up sensor or background timer. */
internal class NeonTiltSensor(context: Context, private val handler: Handler,
    private val onTilt: (NeonTiltFrame) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val window = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val controller = NeonTiltController()
    private var requested = false
    var sensor: Sensor? = null; private set
    val active get() = sensor != null

    fun setActive(value: Boolean) {
        if (requested == value) return
        requested = value
        manager?.unregisterListener(this)
        sensor = null; controller.reset()
        if (!value) return
        val sensorManager = manager ?: return
        for (type in listOf(Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER)) {
            val candidate = sensorManager.getDefaultSensor(type, false) ?: continue
            if (runCatching { sensorManager.registerListener(this, candidate, 50_000, handler) }.getOrDefault(false)) {
                sensor = candidate; break
            }
        }
    }
    fun recalibrate() { controller.reset() }
    override fun onSensorChanged(event: SensorEvent) {
        if (!requested || event.sensor != sensor || event.values.size < 3) return
        @Suppress("DEPRECATION")
        val rotation = window?.defaultDisplay?.rotation ?: 0
        controller.sample(event.values[0], event.values[1], event.values[2], rotation, event.timestamp / 1_000_000)
            ?.let(onTilt)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
