package org.flyline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import co.flyver.flyvercore.DroneTypes.QuadCopterX
import co.flyver.flyvercore.MainControllers.MainController
import co.flyver.flyvercore.MicroControllers.IOIOController
import ioio.lib.util.IOIOLooper
import ioio.lib.util.android.IOIOActivity
import kotlinx.android.synthetic.main.activity_flyver.*

class FlyverActivity : IOIOActivity() {

    private val drone = QuadCopterX()
    private lateinit var cameraController: CameraController
    private lateinit var locationController: LocationController

    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flyver)

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
        } else {
            cameraController = CameraController(this, autofit)
            locationController = LocationController(this)
            initialized = true
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
            cameraController = CameraController(this, autofit)
            locationController = LocationController(this)
            initialized = true
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun createIOIOLooper(): IOIOLooper = IOIOController(drone)
            .addConnectionHooks(object : IOIOController.ConnectionHooks {
                    override fun onConnect(ioioController: IOIOController) {

                        MainController.MainControllerBuilder()
                                .setMicroController(ioioController)
                                .setDrone(drone)
                                .setActivity(this@FlyverActivity)
                                .build()

                        MainController.getInstance().onIoioConnect()
                    }

                    override fun onDisconnect() {
                        MainController.getInstance().onIoioDisconnect()
                    }
        })

    companion object {
        val REQUEST_PERMISSION = 1 /* Identifies camera permission request */
    }



}
