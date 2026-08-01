package com.ana.theflow

import android.app.Application
import com.ana.theflow.data.session.ActiveAccountHolder

// Initializes process-wide state that must exist before any Activity is created.
class TheFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ActiveAccountHolder.init(this)
    }
}
