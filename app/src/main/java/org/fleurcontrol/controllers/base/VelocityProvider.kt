package org.fleurcontrol.controllers.base

/**
 * An interface for classes that provide velocity measurements.
 */
interface VelocityProvider {
    val velocityX: Float
    val velocityY: Float
    val velocityZ: Float
}