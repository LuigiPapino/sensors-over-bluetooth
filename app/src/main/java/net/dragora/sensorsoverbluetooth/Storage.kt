package net.dragora.sensorsoverbluetooth

import android.content.Context
import android.hardware.SensorManager

/**
 * Created by luigipapino on 28/12/2018.
 */
class Storage(private val context: Context) {

    val pref = context.getSharedPreferences("storage", Context.MODE_PRIVATE)


    fun storeSensors(sensors: List<String>) {
        pref.edit().putStringSet(KEY_SENSORS, sensors.toSet()).apply()

    }

    fun readSensors(): List<String> {
        return pref.getStringSet(KEY_SENSORS, setOf()).toList()
    }

    fun storeSensorDelay(delay: Int) {
        pref.edit().putInt(KEY_SENSOR_DELAY, delay).apply()

    }

    fun readSensorDelay(): Int {
        return pref.getInt(KEY_SENSOR_DELAY, SensorManager.SENSOR_DELAY_GAME)
    }

    companion object {
        private const val KEY_SENSORS = "sensors"
        private const val KEY_SENSOR_DELAY = "sensor_delay"

    }
}