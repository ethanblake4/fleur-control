package org.fleurcontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Toast
import ioio.lib.util.IOIOLooper
import ioio.lib.util.android.IOIOActivity
import kotlinx.android.synthetic.main.activity_flyver.*
import org.fleurcontrol.controllers.ArCoreController
import org.fleurcontrol.controllers.CvOpticalFlowController
import org.fleurcontrol.controllers.FlightController
import org.fleurcontrol.controllers.LocationController
import org.fleurcontrol.data.FlyState
import org.fleurcontrol.sensor.SensorFusionProvider
import org.opencv.osgi.OpenCVNativeLoader
import timber.log.Timber

class FlightActivity : IOIOActivity() {

    private lateinit var locationController: LocationController

    private var initialLocation: Location? = null

    private lateinit var sensorFusionProvider: SensorFusionProvider

    private var flyState = FlyState.CALIBRATE
        set(value) { field = value; stateText.apply {post {text = value.name}} }

    private var initialized = false

    private var useOpenCV = false

    private lateinit var arCoreController: ArCoreController
    private val opticalFlowController = CvOpticalFlowController(this)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flyver)

        sensorFusionProvider = SensorFusionProvider(
                getSystemService(Context.SENSOR_SERVICE) as SensorManager)

        arCoreController = ArCoreController(this, sensorFusionProvider)

        //droneControlThread = Thread(uiControlLoop)

        calibrateCompleteButton.setOnClickListener {
            flyState = FlyState.IDLE
            calibrationCard.visibility = View.GONE
        }

        if(useOpenCV) {
            stateText.text = "OpenCV Mode"
            opticalFlowController.initialize()
            autofit.setOnClickListener {
                opticalFlowController.useMedian = !opticalFlowController.useMedian
                stateText.text = if (opticalFlowController.useMedian) "median" else "average"
            }
        } else {
            stateText.text = "AR Mode"
            arCoreController.initialize()
            arCoreController.locationCallback = { x, y, z, w ->
                visualX.text = "X: $x"
                visualY.text = "Y: $y"
                visualZ.text = "Z: $z"

                rotateDisplay.translationX =  (Math.cos(w.toDouble()).toFloat() * 54)
                rotateDisplay.translationY = (Math.sin(w.toDouble()).toFloat() * 54)
                rotateDisplay.invalidate()

                uiControlLoop.run()
            }
        }
    }

    override fun onResume() {

        super.onResume()

        sensorFusionProvider.start()

        Timber.d("Flyver:: onResume")

        val cameraPermission = checkSelfPermission(Manifest.permission.CAMERA)
        val locationPermission = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)

        if(cameraPermission != PackageManager.PERMISSION_GRANTED ||
                locationPermission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(mutableListOf<String>().apply {
                if (cameraPermission != PackageManager.PERMISSION_GRANTED)
                    add(Manifest.permission.CAMERA)
                if (locationPermission != PackageManager.PERMISSION_GRANTED)
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
            }.toTypedArray(), REQUEST_PERMISSION)
        } else setupControllers()

        if(initialized) {
            locationController.resume()
        }
    }

    private fun setupControllers() {
        locationController = LocationController(this)

        if(useOpenCV) opticalFlowController.enable(autofit)
        else arCoreController.enable(arView)
                .subscribe ({ b ->
                    Toast.makeText(this, "AR enabled: $b", Toast.LENGTH_LONG)
                            .show()
                }) { e ->
                    Timber.e(e)
                    Toast.makeText(this,
                            "Error enabling ARCore, fallback to OpenCV", Toast.LENGTH_LONG)
                            .show()
                }

        initialized = true
    }

    override fun onPause() {
        super.onPause()
        sensorFusionProvider.stop()
        if(initialized) {
            if(useOpenCV) opticalFlowController.pause() else arCoreController.pause()
            locationController.pause()
        }
    }

    override fun onStart() {
        super.onStart()
        if(!useOpenCV) arCoreController.start()
    }

    override fun onStop() {
        super.onStop()
        if(!useOpenCV) arCoreController.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if(initialized && useOpenCV) autofit.disableView()
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
            setupControllers()
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun createIOIOLooper(): IOIOLooper = FlightController(
            {
                connectText.text = "CONNECTED"
                connectText.setTextColor(Color.GREEN)
            },
            {
                connectText.text = "DRONE DISCONNECTED"
                connectText.setTextColor(Color.YELLOW)
            },
            {
                Toast.makeText(this, "The drone firmware is not compatible with the app",
                        Toast.LENGTH_LONG).show()
            },
            if(useOpenCV) opticalFlowController else arCoreController
    )

    private val uiControlLoop: Runnable = Runnable {

        val orientation = FloatArray(3)
        sensorFusionProvider.getEulerAngles(orientation)

        // Convert radians to degrees
        val pitch = orientation[1] * -57
        val roll = orientation[2] * -57
        val yaw = orientation[0]

        sensorDisplay.translationX = pitch * 2
        sensorDisplay.translationY = roll * 2
        sensorDisplay.invalidate()

        rotateDisplay2.translationX = (pitch * 2) + (Math.cos(yaw.toDouble()).toFloat() * 54)
        rotateDisplay2.translationY = (roll * 2) + (Math.sin(yaw.toDouble()).toFloat() * 54)
        rotateDisplay2.invalidate()

        velocityCircle.translationX = arCoreController.velocityX * 2000
        velocityCircle.translationY = arCoreController.velocityY * 2000
        velocityCircle.invalidate()

        locationController.currentLocation?.let {

            val lat = it.latitude.toString()
            val long = it.longitude.toString()

            curLatText.text = "Lat: " + lat
            longText.text = "Long: " + long
            altText.text = "Altitude: " + it.altitude.toString()

            /*val apiKey = "AIzaSyCILVy_xQHscLY_aQYb8mMUxQfMrdc2F_M"

            val width = lastimgDisplay.measuredWidth
            val height = lastimgDisplay.measuredHeight

            if(locationController.lastUpdateTime > System.currentTimeMillis() - 20
                    && locationController.lastUpdateChangedMapPos)
                Glide.with(this)
                        .load("https://maps.googleapis.com/maps/api/staticmap?" +
                                "center=$lat,$long" +
                                "&zoom=17&size=${width}x$height&maptype=satellite" +
                                "&markers=color:red|label:A|$lat,$long\n" +
                                "&key=$apiKey")
                        .apply(RequestOptions.noAnimation())
                        .into(lastimgDisplay)*/
        }

        when(flyState) {

            FlyState.CALIBRATE -> {
                matrix1Val.text = "Place the device on a completely level surface and press Calibrate:\n" +
                        roll.toString() + "\n" + pitch.toString()
                matrix1Val.invalidate()
            }

            FlyState.IDLE -> {
                if(locationController.currentLocation != null) {
                    if(initialLocation == null || locationController.currentLocation!!.accuracy
                            > initialLocation!!.accuracy) {
                        initialLocation = locationController.currentLocation
                    }
                }
            }

            FlyState.LIFTOFF -> {

            }

            FlyState.FLYING -> {

            }
        }
    }


    companion object {
        init {
            OpenCVNativeLoader().init()
        }

        val REQUEST_PERMISSION = 1 /* Identifies camera permission request */
    }

}
