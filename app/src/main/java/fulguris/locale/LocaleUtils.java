/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * This file was taken from the Mozilla Focus project:
 * https://github.com/mozilla-mobile/focus-android
 */

package fulguris.locale;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Collection;
import java.util.Locale;

/**
 * Utility class to manage overriding system language with user selected one.
 */
public class LocaleUtils {
    private static final String LOG_TAG = "LocaleUtils";

    /**
     * Provide the locale we should currently apply.
     */
    public static Locale requestedLocale(String aUserLocale) {
        if (aUserLocale.isEmpty()) {
            // Provide the current system locale then
            return Resources.getSystem().getConfiguration().locale;
        }

        return parseLocaleCode(aUserLocale);
    }

    /**
     * Provides a locale object from the given sting.
     * @param localeCode
     * @return
     */
    public static Locale parseLocaleCode(final String localeCode) {
        int index;
        if ((index = localeCode.indexOf('-')) != -1 ||
                (index = localeCode.indexOf('_')) != -1) {
            final String langCode = localeCode.substring(0, index);
            final String countryCode = localeCode.substring(index + 1);
            return new Locale(langCode, countryCode);
        }

        return new Locale(localeCode);
    }

    /**
     * This is public to allow for an activity to force the
     * current locale to be applied if necessary (e.g., when
     * a new activity launches).
     */
    private static void updateConfiguration(Context context, Locale locale) {
        Resources res = context.getResources();
        Configuration config = res.getConfiguration();

        if (config.locale == locale) {
            // Already in the correct locale
            return;
        }

        // We should use setLocale, but it's unexpectedly missing
        // on real devices.
        config.locale = locale;

        config.setLayoutDirection(locale);

        res.updateConfiguration(config, null);
    }

    /**
     * Change locale of the JVM instance and given activity.
     *
     * @param context Activity context, do not pass application context as it seems it breaks everything
     * @param locale
     * @return The Java locale string: e.g., "en_US".
     */
    public static String updateLocale(Context context, final Locale locale) {
        // Fast path.
        //if (Locale.getDefault().equals(locale)) {
        //    return null;
        //}

        Locale.setDefault(locale);
        // Update resources.
        updateConfiguration(context, locale);

        return locale.toString();
    }

    /**
     * Returns a list of supported locale codes
     */
    public static Collection<String> getPackagedLocaleTags(final Context context) {
        return fulguris.locale.LocaleList.BUNDLED_LOCALES;
    }

}
