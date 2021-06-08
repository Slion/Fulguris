package acr.browser.lightning

import android.content.Context

// Disable firebase for fdroid

fun setAnalyticsCollectionEnabled(aContext: Context, aEnable: Boolean) {
    //FirebaseAnalytics.getInstance(aContext).setAnalyticsCollectionEnabled(aEnable)
}

fun setCrashlyticsCollectionEnabled(aEnable: Boolean) {
    //FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(aEnable)
}


