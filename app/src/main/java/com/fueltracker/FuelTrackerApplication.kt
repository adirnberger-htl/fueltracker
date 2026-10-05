package com.fueltracker

import android.app.Application

class FuelTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            com.fueltracker.work.PriceSyncWorker.schedulePeriodicSync(this)
        } catch (_: Exception) {}
    }
}
