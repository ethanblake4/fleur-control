package org.fleurcontrol.controllers

import android.app.Activity
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import android.view.View
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import org.fleurcontrol.controllers.base.VelocityProvider
import org.fleurcontrol.controllers.util.ARCanvas
import org.fleurcontrol.controllers.util.BackgroundDrawer
import org.fleurcontrol.math.MatrixF4x4
import org.fleurcontrol.math.Quaternion
import org.fleurcontrol.sensor.SensorFusionProvider
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArCoreController (
        val activity: Activity,
        val sensorFusionProvider: SensorFusionProvider
): VelocityProvider {

    var locationCallback: ((Float, Float, Float, Float) -> Unit)? = null

    var lastX: Float = 0f
    var lastY: Float = 0f
    var lastZ: Float = 0f

    override var velocityX = 0f
        private set
    override var velocityY = 0f
        private set
    override var velocityZ = 0f
        private set

    class Transient : Exception()

    companion object {
        const val MAX_RETRIES = 20
    }

    private var initializedSession = false
    private var configuredSurfaceAndSession = false
    private var setupRenderer = false
    private lateinit var session: Session
    private var installRequested = true
    private var didEnable = false
    private var curSurface: GLSurfaceView? = null

    private var rotationMatrix = MatrixF4x4()

    fun initialize() {
        // empty for now
    }

    fun enable(surfaceView: GLSurfaceView): Single<Boolean> {

        surfaceView.visibility = View.VISIBLE

        var retryCount = 0

        if(didEnable) {
            session.resume()
            return Single.just(true)
        }

        return Single.fromCallable { ArCoreApk.getInstance().checkAvailability(activity) }
                .map {
                    if (it.isTransient) throw Transient()
                    it.isSupported
                }.retryWhen {
                    it.flatMap { t ->
                        (if (++retryCount < MAX_RETRIES && t is Transient) {
                            // When this Observable calls onNext, the original
                            // Observable will be retried (i.e. re-subscribed).
                            Flowable.timer(200, TimeUnit.MILLISECONDS)
                        } else Flowable.error(t)) // Unrecoverable error
                    }
                }.map {
                    if (!initializedSession) {
                        when (ArCoreApk.getInstance().requestInstall(activity, installRequested)) {
                            ArCoreApk.InstallStatus.INSTALLED -> {
                                session = Session(activity) // Success
                                initializedSession = true
                                true
                            }
                            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                                // Ensures next invocation of requestInstall() will either return
                                // INSTALLED or throw an exception.
                                installRequested = true
                                throw Transient()
                            }
                            else -> {
                                throw Exception()
                            }
                        }
                    } else true
                }.retryWhen {
                    it.flatMap { t ->
                        (if (++retryCount < MAX_RETRIES && t is Transient) {
                            // When this Observable calls onNext, the original
                            // Observable will be retried (i.e. re-subscribed).
                            Flowable.timer(200, TimeUnit.MILLISECONDS)
                        } else Flowable.error(t)) // Unrecoverable error
                    }
                }
                .doOnSuccess {
                    if(!configuredSurfaceAndSession) {
                        session.configure(Config(session).apply {
                            lightEstimationMode = Config.LightEstimationMode.DISABLED
                            planeFindingMode = Config.PlaneFindingMode.DISABLED
                            updateMode = Config.UpdateMode.BLOCKING
                        })

                        surfaceView.visibility = View.VISIBLE

                        surfaceView.preserveEGLContextOnPause = true
                        surfaceView.setEGLContextClientVersion(2)
                        surfaceView.setEGLConfigChooser(
                                8, 8, 8, 8,
                                16, 0
                        ) // Alpha used for plane blending.
                        configuredSurfaceAndSession = true
                    }

                    session.resume()

                    didEnable = true

                    if(!setupRenderer) {

                        curSurface = surfaceView

                        surfaceView.setRenderer(object : GLSurfaceView.Renderer {

                            val arCanvas = ARCanvas()
                            val backgroundDrawer = BackgroundDrawer(session)

                            override fun onDrawFrame(gl: GL10?) {

                                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT
                                        or GLES20.GL_DEPTH_BUFFER_BIT)

                                try {
                                    val frame = session.update()
                                    arCanvas.arcoreFrame = frame

                                    backgroundDrawer.onDraw(arCanvas)

                                    val p = frame.camera.pose.rotationQuaternion

                                    val angles = Quaternion().apply {
                                        setXYZW(p[0], p[1], p[2], p[3])
                                    }.toEulerAngles()

                                    val yaw = angles[0].toFloat()

                                    frame.camera.pose.translation?.let { t ->

                                        val x = t[0]; val z = t[1]; val y = t[2]

                                        locationCallback?.let {
                                            it(x, y, z, yaw)
                                        }

                                        val distance = Math.hypot(x.toDouble()-lastX, y.toDouble()-lastY)
                                        val direction = Math.atan2(y.toDouble()-lastY, x.toDouble()-lastX)

                                        val newDirection = direction + yaw - 90

                                        val newY = Math.sin(newDirection) * distance
                                        val newX = Math.cos(newDirection) * distance

                                        velocityX = newX.toFloat()
                                        velocityY = newY.toFloat()
                                        velocityZ = z - lastZ

                                        lastX = x
                                        lastY = y
                                        lastZ = z
                                    }

                                } catch (e: Exception) {
                                    Timber.e(e)
                                }
                            }

                            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                                GLES20.glViewport(0, 0, width, height)
                                // Notify ARCore session that the view size changed
                                // so that the perspective matrix and the video background
                                // can be properly adjusted.
                                session.setDisplayGeometry(Surface.ROTATION_90, width, height)
                                arCanvas.width = width.toFloat()
                                arCanvas.height = height.toFloat()
                            }

                            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                                GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

                                backgroundDrawer.prepare(activity)
                            }
                        })

                        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

                        setupRenderer = true
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(AndroidSchedulers.mainThread())
    }

    fun transformXVelocity(velocity: Int) {

    }

    fun pause() {
        if(initializedSession) session.pause()
    }

    fun stop() {
        if(initializedSession) curSurface?.onPause()
    }

    fun start() {
        if(initializedSession) curSurface?.onResume()
    }
}