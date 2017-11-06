package org.flyline

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.view.View
import co.flyver.flyvercore.DroneTypes.QuadCopterX
import co.flyver.flyvercore.MicroControllers.IOIOController
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import ioio.lib.util.IOIOLooper
import ioio.lib.util.android.IOIOActivity
import kotlinx.android.synthetic.main.activity_flyver.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.opencv.core.Mat
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

    private var loopsIndex = 0;

    var quietController = QuietController(this, { keySet ->
        gpsKeySet = keySet

        targetLat1.apply {post {text = "Lat 1: " + keySet.latitude.toString().substring(0,7)}}
        targetLat2.apply {post {text = "Lat 2: " + keySet.latitude2.toString().substring(0,7)}}
        targetLong1.apply {post {text = "Long 1: " + keySet.longitude.toString().substring(0,7)}}
        targetLong2.apply {post {text = "Long 2: " + keySet.longitude2.toString().substring(0,7)}}

        flyState = FlyState.LIFTOFF
        //val matrix = sensorFusionProvider.currentOrientationRotationMatrix.matrix

        reachTarget.apply { post {
            visibility = View.VISIBLE

            reachTarget.setOnClickListener {
                reachTarget.visibility = View.GONE
                flyState = FlyState.GPS
            }
        } }

        //initialRoll = ((matrix[2] + (matrix[8] * 0.91))) * SENSOR_SCALE_ROLL
        //initialPitch = (matrix[8].toDouble()) * SENSOR_SCALE_PITCH
    })

    private var flyState = FlyState.CALIBRATE
        set(value) { field = value; stateText.apply {post {text = value.name}} }

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
            cameraController = CameraController(this, autofit, { mat, bytes, bmp ->
                lastimgDisplay.post {
                    lastimgDisplay.setImageBitmap(bmp)
                }
                cameraCall(mat, bytes, bmp)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe()

            })
            /*autofit.setOnClickListener({
                cameraController.lockFocus()
            })*/
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
            cameraController = CameraController(this, autofit, { mat, bytes, bmp ->
                lastimgDisplay.post {
                    lastimgDisplay.setImageBitmap(bmp)
                }
                cameraCall(mat, bytes, bmp)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe()
            })
            autofit.setOnClickListener({
                cameraController.lockFocus()
            })
            locationController = LocationController(this)
            initialized = true
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    fun cameraCall(mat: Mat, bytes: ByteArray, bmp: Bitmap): Completable {
        val client = OkHttpClient.Builder()
                .followRedirects(true)
                .build()
        return Completable.fromCallable({
            val result = PatternFinder.analyzeRed(mat)
            if(result[0] > 0.0 && result[1] > 0.0) {
                Timber.d("Pattern %f %f", result[0], result[1])

                locationController.currentLocation?.let {
                    val response = client.newCall(Request.Builder()
                            .url(SERVER_URL + "?text=device:A"+ Gservices.getLong(
                                    contentResolver, "android_id", 0).toString()+
                                    ",lat:"+it.latitude.toString() + ",long:"
                                    + it.longitude.toString())
                            .build()).execute()
                    Timber.d(response.body()!!.string())
                }
                if(flyState == FlyState.GPS)
                    flyState = FlyState.CAMERA
                else if (flyState == FlyState.CAMERA) {
                    if(result[0] > 140 && result[0] < 240 && result[1] > 100 && result[1] < 200) {
                        flyState = FlyState.LANDING
                        land.apply { post {
                            visibility = View.VISIBLE
                            setOnClickListener {
                                land.visibility = View.GONE
                                flyState = FlyState.LIFTOFF

                                reachTarget.visibility = View.VISIBLE

                                reachTarget.setOnClickListener {
                                    reachTarget.visibility = View.GONE
                                    flyState = FlyState.GPS
                                }
                            }
                        } }
                    }
                }
            }
        })
    }

    override fun createIOIOLooper(): IOIOLooper = IOIOController(drone)
        .addConnectionHooks(object : IOIOController.ConnectionHooks {
                override fun onConnect(ioioController: IOIOController) {
                    connectText.text = "Connected"
                    connectText.setTextColor(Color.GREEN)
                }

                override fun onDisconnect() {
                    connectText.text = "DRONE DISCONNECTED"
                    connectText.setTextColor(Color.YELLOW)
                }
        })

    private val droneControlLoop = Runnable {
        while(true) {

            val matrix = sensorFusionProvider.currentOrientationRotationMatrix.matrix

            val roll = ((matrix[2] + (matrix[8] * 0.91)) - initialRoll) * SENSOR_SCALE_ROLL
            val pitch = (matrix[8] - initialPitch) * SENSOR_SCALE_PITCH

            sensorDisplay.postOnAnimation({
                sensorDisplay.translationX = roll.toFloat()
                sensorDisplay.translationY = pitch.toFloat()
                sensorDisplay.invalidate()
            })

            locationController.currentLocation?.let {
                curLatText.apply { post { text = "Lat: " + it.latitude.toString()}}
                longText.apply { post { text = "Long" + it.longitude.toString() }}
                altText.apply { post { text = "Altitude: " + it.altitude.toString() }}
            }

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

                FlyState.GPS -> {
                    loopsIndex++
                    if(loopsIndex % 540 == 0) {
                        cameraController.lockFocus()
                    }
                }

                FlyState.CAMERA -> {
                    loopsIndex++
                    if(loopsIndex % 230 == 0) {
                        cameraController.lockFocus()
                    }
                }
            }



            Thread.sleep(14)
        }
    }

    companion object {
        init {
            OpenCVNativeLoader().init()
        }

        val SERVER_URL = "http://cuturrufo-177916.appspot.com/SimpleServlet"

        val REQUEST_PERMISSION = 1 /* Identifies camera permission request */

        val SENSOR_SCALE_ROLL = 130
        val SENSOR_SCALE_PITCH = 70
    }

}
