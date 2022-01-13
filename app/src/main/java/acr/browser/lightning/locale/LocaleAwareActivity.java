/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package acr.browser.lightning.locale;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.core.text.TextUtilsCompat;
import androidx.core.view.ViewCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
//import android.view.WindowManager;


import java.util.Locale;

import javax.inject.Inject;

import acr.browser.lightning.di.UserPrefs;
import acr.browser.lightning.settings.preferences.UserPreferences;
import dagger.hilt.android.AndroidEntryPoint;

//@AndroidEntryPoint
public abstract class LocaleAwareActivity extends AppCompatActivity {

    private static final String TAG = "LocaleAwareActivity";
    private volatile Locale mLastLocale;

    @Inject
    public UserPreferences userPreferences;

    @UserPrefs
    @Inject
    public SharedPreferences userSharedPreferences;

    /**
     * Is called whenever the application locale has changed. Your Activity must either update
     * all localised Strings, or replace itself with an updated version.
     */
    public abstract void onLocaleChanged();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLastLocale = LocaleUtils.requestedLocale(userPreferences.getLocale());
        LocaleUtils.updateLocale(this, mLastLocale);
        setLayoutDirection(getWindow().getDecorView(), mLastLocale);
    }


    /**
     * Upon configuration change our new config is reset to system locale.
     * Locale.geDefault is also reset to system local apparently.
     * That's also true if locale was previously change on the application context.
     * Therefore we don't bother with application context for now.
     *
     * @param newConfig
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        Locale requestedLocale = LocaleUtils.requestedLocale(userPreferences.getLocale());

        Log.d(TAG,"Config changed - Last locale: " + mLastLocale);
        Log.d(TAG,"Config changed - Requested locale: " + requestedLocale);
        Log.d(TAG,"Config changed - New config locale (ignored): " + newConfig.locale);

        // Check if our request local was changed
        if (requestedLocale.equals(mLastLocale)) {
            // Requested locale is the same make sure we apply it anew as it was reset in our new config
            LocaleUtils.updateLocale(this,mLastLocale);
            setLayoutDirection(getWindow().getDecorView(), mLastLocale);
        } else {
            // Requested locale was changed, we will need to restart our activity then
            localeChanged(requestedLocale);
        }

        super.onConfigurationChanged(newConfig);
    }

    /**
     * Force set layout direction to RTL or LTR by Locale.
     *
     * @param view
     * @param locale
     */
    public static void setLayoutDirection(View view, Locale locale) {
        switch (TextUtilsCompat.getLayoutDirectionFromLocale(locale)) {
            case ViewCompat.LAYOUT_DIRECTION_RTL:
                ViewCompat.setLayoutDirection(view, ViewCompat.LAYOUT_DIRECTION_RTL);
                break;
            case ViewCompat.LAYOUT_DIRECTION_LTR:
            default:
                ViewCompat.setLayoutDirection(view, ViewCompat.LAYOUT_DIRECTION_LTR);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //onConfigurationChanged(getResources().getConfiguration());
    }

    /**
     *
     * @param aNewLocale
     */
    void localeChanged(Locale aNewLocale) {
        Log.d(TAG,"Apply locale: " + aNewLocale);
        mLastLocale = aNewLocale;
        onLocaleChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Locale requestedLocale = LocaleUtils.requestedLocale(userPreferences.getLocale());
        Log.d(TAG,"Resume - Last locale: " + mLastLocale);
        Log.d(TAG,"Resume - Requested locale: " + requestedLocale);

        // Check if locale was changed as we were paused, apply new locale as needed
        if (!requestedLocale.equals(mLastLocale)) {
            localeChanged(requestedLocale);
        }
    }

}
