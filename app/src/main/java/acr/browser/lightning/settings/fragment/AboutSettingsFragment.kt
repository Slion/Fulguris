/*
 * Copyright 2014 A.C.R. Development
 */
package acr.browser.lightning.settings.fragment

import acr.browser.lightning.BuildConfig
import acr.browser.lightning.R
import android.os.Bundle
import android.util.Log
import androidx.preference.Preference
import com.android.volley.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject


class AboutSettingsFragment : AbstractSettingsFragment() {

    override fun providePreferencesXmlResource() = R.xml.preference_about

    private lateinit var queue: RequestQueue

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        clickablePreference(
            preference = SETTINGS_VERSION,
            summary = versionString()
        )

        queue = Volley.newRequestQueue(this.context)

        if (BuildConfig.FLAVOR.contains("slionsFullDownload")) {
            findPreference<Preference>(SETTINGS_VERSION)?.apply {title = getString(R.string.checking_for_updates)}
            checkForUpdates()
        }


    }


    fun versionString() = BuildConfig.APPLICATION_ID + " - v" + BuildConfig.VERSION_NAME

    override fun onStop() {
        super.onStop()
        // Cancel all pending requests
        queue.cancelAll(TAG)
        Log.d(TAG,"Cancel requests")
    }

    private fun checkForUpdates() {
        val url = getString(R.string.slions_update_check_url)
        // Request a JSON object response from the provided URL.
        val request = object: JsonObjectRequest(Request.Method.GET,url,null,
                Response.Listener<JSONObject> { response ->
                    findPreference<Preference>(SETTINGS_VERSION)?.apply {
                        // Fetch latest version from JSON by parsing XenForo answer
                        val latestVersion = response.getJSONArray("versions").getJSONObject(0).getString("version_string")
                        if ( latestVersion == BuildConfig.VERSION_NAME) {
                            title = getString(R.string.up_to_date)
                        }
                        else {
                            title = getString(R.string.update_available) + " - v" + latestVersion
                        }
                    }

                    //Log.d(TAG,response.toString())
                },
                Response.ErrorListener {error: VolleyError ->
                    findPreference<Preference>(SETTINGS_VERSION)?.apply {
                        title = getString(R.string.update_check_error)
                        summary = versionString() + "\n" + error.cause.toString() + error.networkResponse?.let{"(" + it.statusCode.toString() + ")"}
                        // Use the following for network status code
                        // Though networkResponse can be null in flight mode for instance
                        // error.networkResponse.statusCode.toString()
                    }
                }
        ){
            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                val params: MutableMap<String, String> = HashMap()
                // Here is slions.net API key
                params["XF-Api-Key"] = getString(R.string.slions_api_key)
                return params
            }
        }

        request.tag = TAG
        // Add the request to the RequestQueue.
        queue.add(request)
    }


    companion object {
        private const val SETTINGS_VERSION = "pref_version"
        private const val TAG = "AboutSettingsFragment"
    }
}
