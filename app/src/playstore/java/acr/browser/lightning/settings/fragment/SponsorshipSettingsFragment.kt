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

package acr.browser.lightning.settings.fragment

import acr.browser.lightning.BuildConfig
import acr.browser.lightning.R
import acr.browser.lightning.Sponsorship
import acr.browser.lightning.settings.preferences.PreferenceCategoryEx
import acr.browser.lightning.settings.preferences.UserPreferences
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.billingclient.api.*
import dagger.hilt.android.AndroidEntryPoint
// See: https://stackoverflow.com/a/54188472/3969362
import org.threeten.bp.Period;
import javax.inject.Inject

/**
 * Manage in-app purchases and subscriptions.
 */
@AndroidEntryPoint
class SponsorshipSettingsFragment : AbstractSettingsFragment(),
        PurchasesUpdatedListener,
        BillingClientStateListener {

    private val LOG_TAG = "SponsorshipSettingsFragment"

    val SPONSOR_BRONZE = "sponsor.bronze"
    val SUBS_SKUS = listOf(SPONSOR_BRONZE)

    @Inject internal lateinit var userPreferences: UserPreferences

    // Google Play Store billing client
    private lateinit var playStoreBillingClient: BillingClient

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_contribute
    }

    override fun providePreferencesXmlResource() = R.xml.preference_sponsorship

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // Connect our billing client
        context?.let {
            playStoreBillingClient = BillingClient.newBuilder(it)
                .enablePendingPurchases() // required or app will crash
                .setListener(this).build()
        }
    }

    /**
     *
     */
    override fun onResume() {
        super.onResume()
        if (playStoreBillingClient.isReady) {
            // Update our preference screen on resume.
            // That should make sure newly bought stuff are showing after coming back from payment workflow.
            populatePreferenceScreen()
        } else {
            connectToPlayBillingService()
        }
    }

    /**
     *
     */
    override fun onDestroy() {
        super.onDestroy()
        playStoreBillingClient.endConnection()
    }

    /**
     * Start connection with Google Play store billing.
     */
    private fun connectToPlayBillingService(): Boolean {
        Log.d(LOG_TAG, "connectToPlayBillingService")
        if (!playStoreBillingClient.isReady) {
            playStoreBillingClient.startConnection(this)
            return true
        }
        return false
    }

    /**
     * Callback from [BillingClient] after opening connection.
     * It might make sense to get [SkuDetails] and [Purchases][Purchase] at this point.
     */
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Log.d(LOG_TAG, "onBillingSetupFinished successfully")
                //querySkuDetailsAsync(BillingClient.SkuType.INAPP, GameSku.INAPP_SKUS)
                // Ask client for list of available subscriptions
                populatePreferenceScreen()
                // Ask client for a list of purchase belonging to our customer
                //queryPurchasesAsync()
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                //Some apps may choose to make decisions based on this knowledge.
                Log.d(LOG_TAG, billingResult.debugMessage)
            }
            else -> {
                //do nothing. Someone else will connect it through retry policy.
                //May choose to send to server though
                Log.d(LOG_TAG, billingResult.debugMessage)
            }
        }
    }

    /**
     * This method is called when the app has inadvertently disconnected from the [BillingClient].
     * An attempt should be made to reconnect using a retry policy. Note the distinction between
     * [endConnection][BillingClient.endConnection] and disconnected:
     * - disconnected means it's okay to try reconnecting.
     * - endConnection means the [playStoreBillingClient] must be re-instantiated and then start
     *   a new connection because a [BillingClient] instance is invalid after endConnection has
     *   been called.
     **/
    override fun onBillingServiceDisconnected() {
        Log.d(LOG_TAG, "onBillingServiceDisconnected")
        connectToPlayBillingService()
    }

    /**
     * Populate preference screen with relevant SKUs.
     * That can include in-apps and subscriptions.
     */
    private fun populatePreferenceScreen() {
        // First remove all preferences
        preferenceScreen.removeAll()

        addCategorySubscriptions()
        populatePreferenceScreenStaticItems()
        populateSubscriptions()
    }

    /**
     *
     */
    private fun populatePreferenceScreenStaticItems() {
        addCategoryContribute()
        // Show link to five stars review
        addPreferenceLinkToGooglePlayStoreFiveStarsReview()
        //
        addPreferenceShareLink()
        // Crowdin link
        addPreferenceLinkToCrowdin()
        // Show GitHub sponsorship option
        addPreferenceLinkToGitHubSponsor()
        // Add preference with link to Fulguris download page
        addPreferenceLinkToFulgurisHome()
    }

    /**
     * Query our billing client for known subscriptions, then check which ones are currently active
     * to populate our preference screen.
     */
    private fun populateSubscriptions() {
        if (!isSubscriptionSupported()) {
            return
        }
        // Ask servers for our product list AKA SKUs
        val params = SkuDetailsParams.newBuilder().setSkusList(SUBS_SKUS).setType(BillingClient.SkuType.SUBS).build()
        playStoreBillingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    Log.d(LOG_TAG, "populateSubscriptions OK")
                    if (skuDetailsList.orEmpty().isNotEmpty()) {
                        // We got a valid list of SKUs for our subscriptions
                        var purchases = playStoreBillingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, PurchasesResponseListener { billingResult, purchases ->
                          if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                              //Log.d(LOG_TAG, "Purchases dump: ")
                              if (purchases.isEmpty()) {
                                  // No valid subscriptions anymore, just downgrade then
                                  // We only do this here for now so unless user goes to Sponsorship activity after expiration she should still be able to use her expired subscriptions
                                  userPreferences.sponsorship = Sponsorship.TIN
                              } else {
                                  purchases.forEach {
                                      //Log.d(LOG_TAG, it.toString())
                                      // Take this opportunity to update our entitlements
                                      // That should fix things up for re-installations
                                      if (it.skus.contains(SPONSOR_BRONZE)  && it.isAcknowledged) {
                                          userPreferences.sponsorship = Sponsorship.BRONZE
                                      }
                                  }
                              }

                              // TODO: do we need to check the result?
                              skuDetailsList?.forEach { skuDetails ->
                                  Log.d(LOG_TAG, skuDetails.toString())
                                  val pref = SwitchPreferenceCompat(requireContext())
                                  pref.isSingleLineTitle = false
                                  pref.title = skuDetails.title
                                  pref.summary = skuDetails.price + formatPeriod(skuDetails.subscriptionPeriod) + "\n" + skuDetails.description
                                  pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_payment, activity?.theme)
                                  // Check if that SKU is an active subscription
                                  pref.isChecked = purchases.firstOrNull { purchase -> purchase.skus.contains(skuDetails.sku) && purchase.isAcknowledged } != null
                                  //
                                  pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue: Any ->
                                      if (newValue == true) {
                                          // User is trying to buy that subscription
                                          // Launch subscription workflow
                                          val purchaseParams = BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build()
                                          activity?.let {
                                              playStoreBillingClient.launchBillingFlow(it, purchaseParams)
                                              // TODO: Check the result?
                                              // https://developer.android.com/reference/com/android/billingclient/api/BillingClient#launchBillingFlow(android.app.Activity,%20com.android.billingclient.api.BillingFlowParams)
                                              // Purchase results are delivered in onPurchasesUpdated
                                          }
                                      } else {
                                          // USer is trying to cancel subscription maybe
                                          showPlayStoreSubscriptions(skuDetails.sku)
                                      }
                                      false
                                  }
                                  pref.order = 0 // We want it at the top

                                  // Fetch subscription category
                                  var prefCat: PreferenceCategoryEx? = preferenceScreen.findPreference(getString(R.string.pref_key_subscriptions_category))
                                  if (prefCat == null) {
                                      // Create it if not yet present
                                      prefCat = addCategorySubscriptions()
                                  }
                                  // Add this subscription to our category
                                  prefCat.addPreference(pref)
                              }
                          } else {
                              Log.e(LOG_TAG, "queryPurchasesAsync failed")
                              Log.e(LOG_TAG, billingResult.debugMessage)
                          }
                        })
                    }
                }
                else -> {
                    Log.e(LOG_TAG, "querySkuDetailsAsync failed")
                    Log.e(LOG_TAG, billingResult.debugMessage)
                }
            }
        }
    }

    /**
     *
     */
    private fun addCategorySubscriptions() : PreferenceCategoryEx {
        val prefCat = PreferenceCategoryEx(requireContext())
        prefCat.key = getString(R.string.pref_key_subscriptions_category)
        prefCat.title = getString(R.string.pref_category_subscriptions)
        prefCat.summary = getString(R.string.pref_summary_subscriptions)
        prefCat.order = 0 // We want it at the top
        prefCat.isIconSpaceReserved = true
        preferenceScreen.addPreference(prefCat)
        return prefCat
    }

    /**
     * Open Play Store Subscriptions screen for our application
     */
    private fun showPlayStoreSubscriptions(aSku : String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=$aSku&package=${BuildConfig.APPLICATION_ID}")))
        } catch (e: ActivityNotFoundException) {
            // Just ignore
        }
    }

    /**
     *
     */
    private fun addPreferenceLinkToFulgurisHome() {
        val pref = Preference(requireContext())
        pref.isSingleLineTitle = false
        pref.title = resources.getString(R.string.pref_title_free_download)
        pref.summary = resources.getString(R.string.pref_summary_free_download)
        pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_free_breakfast, activity?.theme)
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // Open Fulguris home page
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(R.string.url_app_home_page))))
            true
        }
        prefGroup.addPreference(pref)
    }

    /**
     * TODO: Improve that I guess
     * Only support one year or one months
     * Otherwise just return an empty string which should still format nicely
     * It just won't specify the period but it is still visible in the payment workflow anyway
     */
    private fun formatPeriod(aPeriod : String) : String {
        var period = Period.parse(aPeriod)
        if (period.years == 1) {
            return resources.getString(R.string.per_year)
        }
        if (period.months == 1) {
            return resources.getString(R.string.per_month)
        }

        return ""
    }

    /**
     * New purchases are coming in from here.
     * We need to acknowledge them.
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        Log.d(LOG_TAG, "onPurchasesUpdated")
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach {
                    if (it.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        if (!it.isAcknowledged) {
                            // Just acknowledge our purchase
                            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()
                            playStoreBillingClient.acknowledgePurchase(acknowledgePurchaseParams) {
                                // TODO: Again what should we do with that?
                                billingResult -> Log.d(LOG_TAG, "onAcknowledgePurchaseResponse: $billingResult")
                                when (billingResult.responseCode) {
                                    BillingClient.BillingResponseCode.OK -> {
                                        if (it.skus.contains(SPONSOR_BRONZE) ) {
                                            // Purchase acknowledgement was successful
                                            // Update  sponsorship in our settings so that changes can take effect in the app
                                            userPreferences.sponsorship = Sponsorship.BRONZE
                                            // Update our screen to reflect changes
                                            populatePreferenceScreen()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                connectToPlayBillingService()
            }
            else -> {
                Log.i(LOG_TAG, billingResult.debugMessage)
            }
        }
    }


    /**
     * Checks if the user's device supports subscriptions
     */
    private fun isSubscriptionSupported(): Boolean {
        val billingResult =
                playStoreBillingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        var succeeded = false
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> connectToPlayBillingService()
            BillingClient.BillingResponseCode.OK -> succeeded = true
            else -> Log.w(LOG_TAG,
                    "isSubscriptionSupported() error: ${billingResult.debugMessage}")
        }
        return succeeded
    }


}
