package acr.browser.lightning.settings.fragment

import acr.browser.lightning.BuildConfig
import acr.browser.lightning.R
import acr.browser.lightning.Sponsorship
import acr.browser.lightning.di.injector
import acr.browser.lightning.preference.UserPreferences
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.billingclient.api.*
// See: https://stackoverflow.com/a/54188472/3969362
import org.threeten.bp.Period;
import javax.inject.Inject

/**
 * Manage in-app purchases and subscriptions.
 */
class SponsorshipSettingsFragment : AbstractSettingsFragment(),
        PurchasesUpdatedListener,
        BillingClientStateListener {

    //@Inject
    //internal lateinit var userPreferences: UserPreferences

    private val LOG_TAG = "SponsorshipSettingsFragment"

    val SPONSOR_BRONZE = "sponsor.bronze"
    val SUBS_SKUS = listOf(SPONSOR_BRONZE)

    @Inject internal lateinit var userPreferences: UserPreferences

    // Google Play Store billing client
    private lateinit var playStoreBillingClient: BillingClient


    override fun providePreferencesXmlResource() = R.xml.preference_sponsorship

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        injector.inject(this)

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
        populateSubscriptions()
    }

    /**
     * Query our billing client for known subscriptions, then check which ones are currently active
     * to populate our preference screen.
     */
    private fun populateSubscriptions() {
        if (!isSubscriptionSupported()) {
            // Subscription is not supported meaning this probably is not a proper Google Play Store installation
            // We invite user to installer our Google Play Store release
            val pref = Preference(context)
            pref.title = resources.getString(R.string.pref_title_no_sponsorship)
            pref.summary = resources.getString(R.string.pref_summary_no_sponsorship)
            pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_play_arrow, activity?.theme)
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                // Open up Fulguris play store page
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=net.slions.fulguris.full.playstore")))
                true
            }
            preferenceScreen.addPreference(pref)
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
                        var purchases = playStoreBillingClient.queryPurchases(BillingClient.SkuType.SUBS)
                        //Log.d(LOG_TAG, "Purchases dump: ")
                        if (purchases.purchasesList.isNullOrEmpty()) {
                            // No valid subscriptions anymore, just downgrade then
                            // We only do this here for now so unless user goes to Sponsorship activity after expiration she should still be able to use her expired subscriptions
                            userPreferences.sponsorship = Sponsorship.TIN
                        } else {
                            purchases.purchasesList?.forEach {
                                //Log.d(LOG_TAG, it.toString())
                                // Take this opportunity to update our entitlements
                                // That should fix things up for re-installations
                                if (it.sku == SPONSOR_BRONZE && it.isAcknowledged) {
                                    userPreferences.sponsorship = Sponsorship.BRONZE
                                }
                            }
                        }

                        // TODO: do we need to check the result?
                        skuDetailsList?.forEach { skuDetails ->
                            Log.d(LOG_TAG, skuDetails.toString())
                            val pref = SwitchPreferenceCompat(context)
                            pref.title = skuDetails.title
                            pref.summary = skuDetails.price + formatPeriod(skuDetails.subscriptionPeriod) + "\n" + skuDetails.description
                            pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_payment, activity?.theme)
                            // Check if that SKU is an active subscription
                            pref.isChecked = purchases.purchasesList?.firstOrNull { purchase -> purchase.sku == skuDetails.sku && purchase.isAcknowledged } != null
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
                            preferenceScreen.addPreference(pref)
                        }
                    }

                    // Add preference with link to Fulguris download page
                    val pref = Preference(context)
                    pref.title = resources.getString(R.string.pref_title_free_download)
                    pref.summary = resources.getString(R.string.pref_summary_free_download)
                    pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_free_breakfast, activity?.theme)
                    pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                        // Open Fulguris home page
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(R.string.url_app_home_page))))
                        true
                    }
                    preferenceScreen.addPreference(pref)
                }
                else -> {
                    Log.e(LOG_TAG, billingResult.debugMessage)
                }
            }
        }
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
                                        if (it.sku == SPONSOR_BRONZE) {
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
