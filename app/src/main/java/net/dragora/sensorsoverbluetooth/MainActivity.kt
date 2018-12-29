package net.dragora.sensorsoverbluetooth

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.ivbaranov.rxbluetooth.BluetoothConnection
import com.github.ivbaranov.rxbluetooth.RxBluetooth
import com.github.pwittchen.reactivesensors.library.ReactiveSensorFilter
import com.github.pwittchen.reactivesensors.library.ReactiveSensors
import com.google.android.material.snackbar.Snackbar
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import net.dragora.sensorsoverbluetooth.ServerState.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var rxBluetooth: RxBluetooth
    private lateinit var rxSensors: ReactiveSensors
    private lateinit var storage: Storage
    private var bluConnection: BluetoothConnection? = null

    private var disposables = CompositeDisposable()
    private var serverDisposable = CompositeDisposable()

    private var sensorDelay = SensorManager.SENSOR_DELAY_GAME

    private var sensors = listOf<SensorItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        //Workaround
        RxJavaPlugins.setErrorHandler { log("RxError: " + it.message) }

        initDependencies()

        //UI Setup
        server_button.setOnClickListener {
            toggleServer()
        }
        setupSensorsList()
        setupDelayBar()


    }

    override fun onDestroy() {
        disposables.dispose()
        serverDisposable.dispose()
        stopServer()
        super.onDestroy()
    }

    private var lastServerState = Closed
        set(state) {
            log("state $state")
            field = state
            runOnUiThread {
                server_state.text = state.toString()
                server_button.text = state.button
            }
        }

    private fun initDependencies() {
        lastServerState = (Closed)

        disposables.dispose()
        disposables = CompositeDisposable()

        rxBluetooth = RxBluetooth(this.applicationContext)
        rxSensors = ReactiveSensors(this.applicationContext)
        storage = Storage(this.applicationContext)
    }

    private fun setupDelayBar() {

        sensorDelay = storage.readSensorDelay()
        val progress = sensorDelay / (1000 * 1000)
        runOnUiThread {
            sensor_delay.progress = progress
            sensor_delay_caption.text = "Delay ${progress}ms"
        }

        sensor_delay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                log("delay $progress")
                sensorDelay = progress * 1000 * 1000
                sensor_delay_caption.text = "Delay ${progress}ms"
                storage.storeSensorDelay(sensorDelay)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

        })
    }

    private fun setupSensorsList() {
        sensors_recycler.layoutManager = LinearLayoutManager(this)
        val selectedSensors = storage.readSensors()
        sensors = rxSensors.sensors.map { SensorItem(it, selectedSensors.contains(it.myId())) }
        val adapter = SensorsAdapter(sensors)
        adapter.setOnItemChildClickListener { adapter, view, position ->
            val item = sensors[position]
            item.selected = !item.selected
            adapter.notifyItemChanged(position)
            storage.storeSensors(sensors.filter { it.selected }.map { it.sensor.myId() })
        }

        sensors_recycler.adapter = adapter

    }

    private fun toggleServer() {
        serverDisposable.dispose()
        serverDisposable = CompositeDisposable()

        when (lastServerState) {
            Waiting -> stopServer()
            Connected -> stopServer()
            Closed -> startServer()
        }
    }

    private fun stopServer() {
        lastServerState = (Closed)

        bluConnection?.closeConnection()
        bluConnection = null
        serverDisposable.dispose()
        serverDisposable = CompositeDisposable()
    }

    private fun startServer() {

        // check if bluetooth is supported on your hardware
        if (!rxBluetooth.isBluetoothAvailable) {
            showSnack("Bluetooth not available")
            return
        } else {
            // check if bluetooth is currently enabled and ready for use
            if (!rxBluetooth.isBluetoothEnabled) {
                // to enable bluetooth via startActivityForResult()
                rxBluetooth.enableBluetooth(this, 10)
                return
            } else {
                // you are ready
            }
        }

        lastServerState = (Waiting)

        serverDisposable += rxBluetooth.connectAsServer(SERVER_NAME, SPP_UUID)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .map { socket ->
                bluConnection = BluetoothConnection(socket)
            }
            .flatMapObservable {
                lastServerState = (Connected)
                sendSensorsData()
                bluConnection?.observeStringStream()
                    ?.subscribeOn(Schedulers.io())
                    ?.observeOn(Schedulers.io())
                    ?.onBackpressureBuffer()
                    ?.toObservable()
            }
            .subscribe(
                { data ->
                    log("remote: $data")
                },
                {
                    log("stream ${it.message}")
                    stopServer()
                })
    }

    private fun sendSensorsData() {

        sensors.filter { it.selected }.map { it.sensor }
            .forEach { sensor ->
                rxSensors.observeSensor(sensor.type, sensorDelay)
                    .filter(ReactiveSensorFilter.filterSensorChanged())
                    .map { it.sensorEvent }
                    .subscribeOn(Schedulers.computation())
                    .observeOn(Schedulers.io())
                    .subscribe { event ->
                        val data = DataEvent(event.timestamp, event.sensor.stringType, event.values).toString()
                        sendData(data)
                    }
            }

    }

    private fun sendData(data: String) {
        log("sendData $data")
        synchronized(bluConnection!!) {
            bluConnection?.send("$data\n")
        }
    }




    private fun showSnack(msg: String) {
        Snackbar.make(server_button, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun log(msg: String?) {
        Log.d(TAG, msg)
    }

    companion object {
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val SERVER_NAME = "Sensors Over Bluetooth"
        private const val TAG = "MainActivity"


    }
}


data class DataEvent(
    val timestamp: Long,
    val type: String,
    val values: FloatArray
) {
    override fun toString(): String {
        val buffer = StringBuffer("$type;$timestamp;")
        values.forEachIndexed() { pos, value ->
            buffer.append("$value;")
        }
        return buffer.toString()
    }
}

enum class ServerState(val button: String) { Waiting("Stop Server"), Connected("Stop Server"), Closed("Start Server") }

private operator fun CompositeDisposable.plusAssign(d: Disposable?) {
    d?.apply { add(d) }
}


fun Sensor.myId(): String {
    return "$name;$type;"
}