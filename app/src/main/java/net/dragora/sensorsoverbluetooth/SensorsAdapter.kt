package net.dragora.sensorsoverbluetooth

import android.hardware.Sensor
import android.util.Log
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder

/**
 * Created by luigipapino on 28/12/2018.
 */
class SensorsAdapter(data: List<SensorItem>?) :
    BaseQuickAdapter<SensorItem, BaseViewHolder>(R.layout.sensors_item, data) {

    override fun convert(helper: BaseViewHolder, item: SensorItem) {

        val color =  (if (item.selected) R.color.primary_light else R.color.white).run { mContext.getColor(this) }

        helper
            .setText(R.id.sensor_name, item.name)
            .setText(R.id.sensor_type, item.type)
            .addOnClickListener(R.id.sensor_main)
            .setBackgroundColor(R.id.sensor_main, color)

        Log.d(TAG, "convert ${item.name}")
    }


    companion object {
        private const val TAG = "SensorsAdapter"
    }
}

data class SensorItem(val sensor: Sensor, var selected: Boolean = false) {

    val name: String = sensor.name
    val type: String = sensor.stringType
}