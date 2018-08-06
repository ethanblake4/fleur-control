package org.fleurcontrol.controllers

import android.app.Activity
import android.view.SurfaceView
import org.fleurcontrol.controllers.base.VelocityProvider
import org.fleurcontrol.controllers.util.OpticalFlowUtil
import org.opencv.android.*
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import timber.log.Timber

class CvOpticalFlowController (val activity: Activity) : VelocityProvider {

    override var velocityX = 0f
        private set
    override var velocityY = 0f
        private set
    override var velocityZ = 0f
        private set

    var useMedian = true

    var cvConnected = false
    var enabled = false
    var setCallbacks = false
    lateinit var camera: JavaCameraView

    private var lastFrame: Mat? = null
    private var frame: Mat? = null

    fun initialize() {

        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0,
                activity.applicationContext, object : LoaderCallbackInterface {
            override fun onPackageInstall(operation: Int, callback: InstallCallbackInterface) {
                Timber.d("package install")
                callback.install()
            }

            override fun onManagerConnected(status: Int) {
                Timber.d("Manager connected, enabled: $enabled")
                if (enabled) camera.enableView()
                else cvConnected = true
            }
        })

    }

    fun enable(cvCameraView: JavaCameraView) {
        camera = cvCameraView
        camera.setMaxFrameSize(720, 480)
        camera.visibility = SurfaceView.VISIBLE
        if(!setCallbacks) {
            camera.setCvCameraViewListener(object : CameraBridgeViewBase.CvCameraViewListener2 {
                override fun onCameraViewStarted(width: Int, height: Int) {
                    Timber.d("camera view started")
                }

                override fun onCameraViewStopped() {
                    Timber.d("camera view stopped")
                }

                override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
                    lastFrame = frame
                    frame = inputFrame.gray()

                    if (lastFrame != null) {
                        frame!!.let {
                            val (x, y) = OpticalFlowUtil.calc(lastFrame!!, it, useMedian) ?:
                                    Pair(0.0, 0.0)
                            val newImg = inputFrame.rgba()
                            if (x != 0.0) {
                                Imgproc.line(
                                        newImg,
                                        Point(it.width() / 2.0, it.height() / 2.0),
                                        Point((it.width() / 2.0) + x, (it.height() / 2.0) + y),
                                        Scalar(255.0, 255.0, 255.0),
                                        5)
                                velocityX = x.toFloat()
                                velocityY = y.toFloat()
                            }
                            return newImg
                        }
                    }

                    return frame!!
                }
            })
        }
        if(cvConnected) camera.enableView()
    }

    fun pause() {
        camera.disableView()
    }
}