package com.drywall.keygen

import android.app.Application
import com.drywall.common.utils.ErrorTracker

class KeygenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ErrorTracker.init(this, "Keygen Pro YHQuintero")
    }
}
