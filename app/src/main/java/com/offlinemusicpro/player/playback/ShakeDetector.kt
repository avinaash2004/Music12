package com.offlinemusicpro.player.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/** Only registered while music is playing and the setting is on, to keep battery use negligible. */
class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var running = false
    private var lastShake = 0L

    fun start() {
        if (running || accelerometer == null) return
        running = sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI) == true
    }

    fun stop() {
        if (!running) return
        sensorManager?.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (g > 2.7f) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastShake > 1_000) {
                lastShake = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
