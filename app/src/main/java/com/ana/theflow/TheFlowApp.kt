package com.ana.theflow

import android.app.Application
import com.ana.theflow.data.session.ActiveAccountHolder
import com.ana.theflow.data.session.RecommendationPreferenceCache

// Initializes process-wide state that must exist before any Activity is created.
class TheFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ActiveAccountHolder.init(this)
        RecommendationPreferenceCache.init(this)
    }
}
