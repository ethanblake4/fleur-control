package org.fleurcontrol.data

enum class FlyState {
    CALIBRATE,
    IDLE, /* Waits for start signal */
    LIFTOFF,
    FLYING,
    LANDING
}