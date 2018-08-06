package org.fleurcontrol

import android.app.Application
import timber.log.Timber

/**
 * Sets up initial stuff
 */
class FlyLineApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
    }
}