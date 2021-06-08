package acr.browser.lightning

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

fun setAnalyticsCollectionEnabled(aContext: Context, aEnable: Boolean) {
    FirebaseAnalytics.getInstance(aContext).setAnalyticsCollectionEnabled(aEnable)
}

fun setCrashlyticsCollectionEnabled(aEnable: Boolean) {
    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(aEnable)
}


