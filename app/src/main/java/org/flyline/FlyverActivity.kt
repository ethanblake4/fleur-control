package org.flyline

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Toast
import co.flyver.flyvercore.DroneTypes.QuadCopterX
import co.flyver.flyvercore.MicroControllers.IOIOController
import ioio.lib.util.IOIOLooper
import ioio.lib.util.android.IOIOActivity
import kotlinx.android.synthetic.main.activity_flyver.*
import org.opencv.osgi.OpenCVNativeLoader
import timber.log.Timber

class FlyverActivity : IOIOActivity() {

    private val drone = QuadCopterX()
    private lateinit var cameraController: CameraController
    private lateinit var locationController: LocationController

    private var gpsKeySet: GPSKeySet? = null
    private var initialLocation: Location? = null

    private lateinit var sensorFusionProvider: SensorFusionProvider

    private var initialRoll = 0.0
    private var initialPitch = 0.0

    var quietController = QuietController(this, { keySet ->
        gpsKeySet = keySet
        flyState = FlyState.LIFTOFF
        val matrix = sensorFusionProvider.currentOrientationRotationMatrix.matrix

        initialRoll = ((matrix[2] + (matrix[8] * 0.91))) * SENSOR_SCALE_ROLL
        initialPitch = (matrix[8].toDouble()) * SENSOR_SCALE_PITCH

    })

    private var flyState = FlyState.CALIBRATE

    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flyver)

        val cameraPermission = checkSelfPermission(Manifest.permission.CAMERA)
        val locationPermission = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)

        sensorFusionProvider = SensorFusionProvider(
                getSystemService(Context.SENSOR_SERVICE) as SensorManager)

        sensorFusionProvider.start()

        if(cameraPermission != PackageManager.PERMISSION_GRANTED ||
                locationPermission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(mutableListOf<String>().apply {
                if (cameraPermission != PackageManager.PERMISSION_GRANTED)
                    add(Manifest.permission.CAMERA)
                if (locationPermission != PackageManager.PERMISSION_GRANTED)
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
            }.toTypedArray(), REQUEST_PERMISSION)
        } else {
            cameraController = CameraController(this, autofit, { mat ->
                val result = PatternFinder.analyzeRed(mat)
                if(result != doubleArrayOf(0.0, 0.0)) {
                    Toast.makeText(this, "Found pattern",
                            Toast.LENGTH_LONG).show()
                    Timber.d("Pattern %f %f", result[0], result[1])
                }
            })
            autofit.setOnClickListener({
                cameraController.lockFocus()
            })
            locationController = LocationController(this)
            initialized = true
        }

        val t = Thread(droneControlLoop)
        t.start()

        calibrateCompleteButton.setOnClickListener {
            flyState = FlyState.IDLE
            calibrationScreen.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()

        if(initialized) {
            cameraController.resume()
            locationController.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if(initialized) {
            cameraController.pause()
            locationController.pause()
        }
    }

    /**
     * After the camera permission has been accepted or granted, we are notified here
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.size != permissions.size ||
                    grantResults.all { it != PackageManager.PERMISSION_GRANTED }) {
                // The app can't work without both permissions, so just end the application
                finish()
            }
        } else {
            cameraController = CameraController(this, autofit, { mat ->
                val result = PatternFinder.analyzeRed(mat)
                if(result != doubleArrayOf(0.0, 0.0)) {
                    Timber.d("Pattern %d %d", result[0], result[1])
                    Toast.makeText(this, "Found pattern", Toast.LENGTH_SHORT).show()
                }
            })
            autofit.setOnClickListener({
                cameraController.lockFocus()
            })
            locationController = LocationController(this)
            initialized = true
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun createIOIOLooper(): IOIOLooper = IOIOController(drone)
        .addConnectionHooks(object : IOIOController.ConnectionHooks {
                override fun onConnect(ioioController: IOIOController) {

                    /*drone.updateSpeeds(0f,0f,0f,0.1f)

                    Handler().postDelayed({
                        drone.updateSpeeds(0f,0f,0f,0f)
                    }, 1000)*/
                }

                override fun onDisconnect() {
                }
        })

    private val droneControlLoop = Runnable {
        while(true) {

            val matrix = sensorFusionProvider.currentOrientationRotationMatrix.matrix

            val roll = ((matrix[2] + (matrix[8] * 0.91)) - initialRoll) * SENSOR_SCALE_ROLL
            val pitch = (matrix[8] - initialPitch) * SENSOR_SCALE_PITCH

            when(flyState) {
                FlyState.CALIBRATE -> {
                    matrix1Val.post {
                        matrix1Val.text = "Calibrate by rotating the device until the numbers look correct:\n" +
                                roll.toString() + "\n" + pitch.toString()
                        matrix1Val.invalidate()
                    }
                }

                FlyState.IDLE -> {
                    if(locationController.currentLocation != null) {
                        if(initialLocation == null || locationController.currentLocation!!.accuracy
                                > initialLocation!!.accuracy) {
                            initialLocation = locationController.currentLocation
                        }
                    }
                    if(!quietController.isRecording) quietController.initQuiet()
                }

                FlyState.LIFTOFF -> {
                    drone.updateSpeeds(0f, -pitch.toFloat(), -roll.toFloat(), 120f)
                }
            }



            Thread.sleep(2)
        }
    }

    companion object {
        init {
            OpenCVNativeLoader().init()
        }

        val REQUEST_PERMISSION = 1 /* Identifies camera permission request */

        val SENSOR_SCALE_ROLL = 130
        val SENSOR_SCALE_PITCH = 70
    }

}
