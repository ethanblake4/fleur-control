package org.flyline

import android.os.Bundle
import co.flyver.flyvercore.DroneTypes.QuadCopterX
import co.flyver.flyvercore.MainControllers.MainController
import co.flyver.flyvercore.MicroControllers.IOIOController
import ioio.lib.util.IOIOLooper
import ioio.lib.util.android.IOIOActivity



class FlyverActivity : IOIOActivity() {

    val drone = QuadCopterX()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flyver)
    }

    override fun createIOIOLooper(): IOIOLooper {
        val ic: IOIOController

        ic = IOIOController(drone).addConnectionHooks(object : IOIOController.ConnectionHooks {
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
        return ic
    }


}
