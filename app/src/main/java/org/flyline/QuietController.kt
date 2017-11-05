package org.flyline

import android.util.Log
import android.widget.Toast
import org.quietmodem.Quiet.FrameReceiver
import org.quietmodem.Quiet.FrameReceiverConfig
import org.quietmodem.Quiet.ModemException
import timber.log.Timber
import java.io.IOException
import java.lang.StringBuilder

class QuietController (
        val activity: FlyverActivity,
        val gpsKeySetCallback: (GPSKeySet) -> Unit
){

    var isRecording = false

    fun initQuiet() {

        isRecording = true

        Timber.d("Initializing quiet")

        val receiverConfig: FrameReceiverConfig

        try {
            receiverConfig = FrameReceiverConfig(
                    activity,
                    "passqi")
        } catch (e: IOException) {
            return
        }

        val receiver: FrameReceiver

        try {
            receiver = FrameReceiver(receiverConfig)
            receiver.enableStats()
            receiver.statsSetBlocking(0, 0)
            val s = Thread {
                do {
                    try {
                        val stats = receiver.receiveStats()
                        Timber.d(stats.toString())
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                } while (isRecording)
            }
            s.start()

            val t = Thread {

                receiver.setBlocking(0, 0)
                val rec = Array(6, {_ -> ""})
                var receivedVals = 0
                var numBlocks = 4
                var receivedNum = false

                val buf = ByteArray(14)
                var recvLen: Long
                do {
                    try {
                        //OverlayLayoutHelper.changeTopMessage(Capture.this, Float.toString(receiver.receiveStats().getReceivedSignalStrengthIndicator()));
                        recvLen = receiver.receive(buf)
                        val sb = StringBuilder()
                        for (b in buf) {
                            sb.append(b.toString(10))
                        }
                        Timber.d("len" + recvLen + sb.toString())
                        Timber.d("%02X", buf[0])
                        if (buf[0].toInt() in 1..9) {
                            rec[buf[0].toInt()] = sb.toString().substring(2, recvLen.toInt() * 2)
                        } else {
                            rec[0] = sb.toString().substring(2, recvLen.toInt() * 2)
                            numBlocks = Integer.valueOf(sb.toString().substring(0, 1))!!
                            receivedNum = true
                        }

                        receivedVals++

                        if (receivedVals >= numBlocks && receivedNum) {
                            val combined = StringBuilder()

                            for (i in 0 until numBlocks) {
                                combined.append(rec[i])
                            }

                            val combstr = combined.toString()

                            Log.d("uncool", combstr)

                            val gps = GPSKeySet(
                                    combstr.substring(0, 6).toDouble() / 100000,
                                    combstr.substring(6, 12).toDouble() / 10000,
                                    combstr.substring(12, 18).toDouble() / 10000,
                                    combstr.substring(18,24).toDouble() / 10000)

                            isRecording = false

                            gpsKeySetCallback(gps)

                            rec.fill("")
                            receivedVals = 0
                            receivedNum = false
                        }
                    } catch (e: IOException) {
                        Toast.makeText(activity, "error", Toast.LENGTH_LONG).show()
                    }

                } while (isRecording)
                receiver.close()
            }
            t.priority = Thread.MAX_PRIORITY
            t.start()
        } catch (e: ModemException) {
            return
        }

    }
}