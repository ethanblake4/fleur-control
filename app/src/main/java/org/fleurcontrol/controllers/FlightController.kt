package org.fleurcontrol.controllers

import ioio.lib.api.IOIO
import ioio.lib.api.IOIO.State
import ioio.lib.api.PulseInput.PulseMode
import ioio.lib.api.exception.ConnectionLostException
import ioio.lib.util.IOIOLooper
import org.fleurcontrol.controllers.base.VelocityProvider
import timber.log.Timber
import java.lang.Thread.sleep

class FlightController (
        val onConnect: () -> Unit,
        val onDisconnect: () -> Unit,
        val onIncompatible: () -> Unit,
        val opticalVelocityProvider: VelocityProvider
) : IOIOLooper {

    private lateinit var ioio: IOIO

    companion object {
        private const val PWM_FREQUENCY = 200

        private const val ECHO_PIN = 6 // Ultrasonic echo
        private const val TRIGGER_PIN = 7 // Ultrasonic trigger

        private const val FC_PIN = 33 // Front clockwise PWM
        private const val FCC_PIN = 35 // Front counterclockwise PWM
        private const val RC_PIN = 37 // Rear clockwise PWM
        private const val RCC_PIN = 39 // Rear counterclockwise PWM
    }

    private val echoPin by lazy { ioio.openPulseInput(ECHO_PIN, PulseMode.POSITIVE) }
    private val triggerPin by lazy { ioio.openDigitalOutput(TRIGGER_PIN)}

    private val fcMotor by lazy { ioio.openPwmOutput(FC_PIN, PWM_FREQUENCY) }
    private val fccMotor by lazy { ioio.openPwmOutput(FCC_PIN, PWM_FREQUENCY) }
    private val rcMotor by lazy { ioio.openPwmOutput(RC_PIN, PWM_FREQUENCY) }
    private val rccMotor by lazy { ioio.openPwmOutput(RCC_PIN, PWM_FREQUENCY) }

    private lateinit var ultrasonicThread: Thread
    private var echoDistanceCm = 0f

    @Throws(ConnectionLostException::class)
    override fun setup(ioio: IOIO) {
        this.ioio = ioio
        onConnect()

        ultrasonicThread = Thread {
            while(ioio.state == State.CONNECTED) {
                loopUltrasonic()
                sleep(10)
            }
        }
        //ultrasonicThread.start()
    }

    override fun loop() {

    }

    /*
     * Loops the ultrasonic readings
     */
    private fun loopUltrasonic() {
        try {
            /* Ultrasonic */
            triggerPin.write(false)
            sleep(5)
            triggerPin.write(true)
            sleep(0, 10)
            triggerPin.write(false)

            val echoSeconds = (echoPin.duration * 1000 * 1000)
            echoDistanceCm = echoSeconds / 29 / 2
        } catch (e: ConnectionLostException) {
            Timber.e(e)
        }
    }

    override fun disconnected() {
        onDisconnect()
    }

    @Suppress("OverridingDeprecatedMember")
    override fun incompatible() {
        onIncompatible()
    }

    override fun incompatible(ioio: IOIO) {
        this.ioio = ioio
        incompatible()
    }
}
