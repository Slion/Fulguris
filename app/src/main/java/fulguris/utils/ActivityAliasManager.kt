/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package fulguris.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import fulguris.enums.IncomingViewAction
import timber.log.Timber

/**
 * Manages enabling/disabling activity aliases based on the incoming view action preference.
 */
object ActivityAliasManager {

    private const val ALIAS_BROWSER_ACTIVITY = "fulguris.alias.default.BrowserActivity"
    private const val ALIAS_INCOGNITO_ACTIVITY = "fulguris.alias.default.IncognitoActivity"
    private const val SELECT_BROWSER_ACTIVITY = "fulguris.activity.SelectBrowserActivity"

    /**
     * Updates which activity/alias is enabled for handling VIEW intents based on the user's preference.
     * Activities declared from your manifest can be enabled/disabled at runtime using PackageManager.
     * We use this to configure the action taken when a VIEW intent is received based on user preference.
     * It defines what we do when our app is selected as the default browser app by the user.
     * It's actually the only way we could configure that since Android default browser app only selects a package rather than a specific activity.
     *
     * @param context The application context
     * @param action The incoming view action preference
     */
    fun updateEnabledActivity(context: Context, action: IncomingViewAction) {
        Timber.d("updateEnabledActivity: $action")

        val packageManager = context.packageManager

        // Disable all first
        setComponentEnabled(packageManager, context, ALIAS_BROWSER_ACTIVITY, false)
        setComponentEnabled(packageManager, context, ALIAS_INCOGNITO_ACTIVITY, false)
        setComponentEnabled(packageManager, context, SELECT_BROWSER_ACTIVITY, false)

        // Enable the appropriate one based on preference
        when (action) {
            IncomingViewAction.NEW_TAB -> {
                setComponentEnabled(packageManager, context, ALIAS_BROWSER_ACTIVITY, true)
                Timber.d("Enabled BrowserActivity alias for incoming VIEW intents")
            }
            IncomingViewAction.INCOGNITO_TAB -> {
                setComponentEnabled(packageManager, context, ALIAS_INCOGNITO_ACTIVITY, true)
                Timber.d("Enabled IncognitoActivity alias for incoming VIEW intents")
            }
            IncomingViewAction.SELECT_BROWSER -> {
                setComponentEnabled(packageManager, context, SELECT_BROWSER_ACTIVITY, true)
                Timber.d("Enabled SelectBrowserActivity for incoming VIEW intents")
            }
        }
    }

    /**
     * Helper function to enable/disable a component.
     */
    private fun setComponentEnabled(
        packageManager: PackageManager,
        context: Context,
        componentName: String,
        enabled: Boolean
    ) {
        val component = ComponentName(context, componentName)
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        try {
            packageManager.setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to set component enabled state for $componentName")
        }
    }
}
