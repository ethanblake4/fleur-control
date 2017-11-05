package org.flyline

enum class FlyState {
    CALIBRATE,
    IDLE, /* Waits for start signal */
    LIFTOFF,
    GPS,
    CAMERA,
    LANDING,
    LANDED,
    RETURN
}