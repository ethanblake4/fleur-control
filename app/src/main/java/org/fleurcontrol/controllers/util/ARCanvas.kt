package org.fleurcontrol.controllers.util

import com.google.ar.core.Frame


//not a canvas, but just for troll :)
class ARCanvas {
    var arcoreFrame: Frame? = null
    var cameraMatrix: FloatArray? = null
    var projMatrix: FloatArray? = null
    var width: Float = 0.toFloat()
    var height: Float = 0.toFloat()
}