package com.secrethq.store.util

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.LifecycleObserver
import com.android.billingclient.api.*
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.google.gson.Gson
import com.secrethq.utils.PTServicesBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.HashMap
import kotlin.math.min


private const val RECONNECT_TIMER_START_MILLISECONDS = 1L * 1000L
private const val RECONNECT_TIMER_MAX_TIME_MILLISECONDS = 1000L * 60L * 15L // 15 minutes
private const val SKU_DETAILS_REQUERY_TIME = 1000L * 60L * 60L * 4L // 4 hours

class BillingDataSource(
    application: Application,
    private val defaultScope: CoroutineScope,

    ): LifecycleObserver, PurchasesUpdatedListener, BillingClientStateListener {

    // Billing client, connection, cached data
    private val billingClient: BillingClient

    // known SKUs (used to query sku data and validate responses)
    private var iapSKUs: MutableList<String> = ArrayList()

    private val iapInfo : ArrayList<HashMap<String, String>>? by lazy {
        PTServicesBridge.getInAppIds()
    }


    var mSkuDetailsList: List<SkuDetails>? = null


    // how long before the data source tries to reconnect to Google play
    private var reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS

    // when was the last successful SkuDetailsResponse?
    private var skuDetailsResponseTime = -SKU_DETAILS_REQUERY_TIME

    // Observables that are used to communicate state.
    private val purchaseConsumptionInProcess: MutableSet<Purchase> = HashSet()
    private val newPurchaseFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val purchaseConsumedFlow = MutableSharedFlow<String>()
    private val billingFlowInProcess = MutableStateFlow(false)

    val BILLING_RESPONSE_RESULT_OK = 0
    val BILLING_RESPONSE_RESULT_USER_CANCELED = 1
    val BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3
    val BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4
    val BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5
    val BILLING_RESPONSE_RESULT_ERROR = 6
    val BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7
    val BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8
    val BILLING_RESPONSE_RESULT_NO_RESTORE = 9
    val BILLING_RESPONSE_RESULT_RESTORE_COMPLETED = 10

    private  var activity: Activity? = null
    private  var activeSKU: String? = null
    private  var isConsumable:Boolean = false
    private lateinit var callback: (Int, String) -> Void?
    private lateinit var restoreCallback: (Int, String) -> Void?
    private lateinit var pendingCallback: (Int, String) -> Void?

//// ---------

    init {
        billingClient = BillingClient.newBuilder(application)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        billingClient.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        Log.d(TAG, "PT: onBillingSetupFinished: $responseCode $debugMessage")

        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                // The billing client is ready. You can query purchases here.
                // This doesn't mean that your app is set up correctly in the console -- it just
                // means that you have a connection to the Billing service.
                reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS
            }
            else -> {
                Log.d(TAG, "PT: onBillingSetupFailed. Retrying...")
                retryBillingServiceConnectionWithExponentialBackoff()
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        // Try to restart the connection on the next request to
        // Google Play by calling the startConnection() method.
        retryBillingServiceConnectionWithExponentialBackoff()
    }

    /**
     * Retries the billing service connection with exponential backoff, maxing out at the time
     * specified by RECONNECT_TIMER_MAX_TIME_MILLISECONDS.
     */
    private fun retryBillingServiceConnectionWithExponentialBackoff() {
        handler.postDelayed(
            { billingClient.startConnection(this@BillingDataSource) },
            reconnectMilliseconds
        )
        reconnectMilliseconds = min(
            reconnectMilliseconds * 2,
            RECONNECT_TIMER_MAX_TIME_MILLISECONDS
        )
    }

    fun getSkuTitle(sku: String): String? {
        return getProductSku(sku)?.title
    }

    fun getSkuPrice(sku: String): String? {
        return getProductSku(sku)?.price
    }

    fun getSkuDescription(sku: String): String? {
        return getProductSku(sku)?.description
    }

    private fun getProductSku(sku: String): SkuDetails? {
        mSkuDetailsList?.let {
            for (list in it) {
                if (sku == list.sku) {
                    return list
                }
            }
        }
        return null
    }

    /**
     * Receives the result from [.querySkuDetailsAsync]}.
     *
     */
    private fun onSkuDetailsResponse(
        billingResult: BillingResult,
        skuDetailsList: List<SkuDetails>?,
    ) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Log.i(TAG, "onSkuDetailsResponse: $responseCode $debugMessage")
                if (skuDetailsList == null || skuDetailsList.isEmpty()) {
                    Log.e(
                        TAG,
                        "onSkuDetailsResponse: " +
                                "Found null or empty SkuDetails. " +
                                "Check to see if the SKUs you requested are correctly published " +
                                "in the Google Play Console."
                    )
                } else {
                    mSkuDetailsList = skuDetailsList
                }
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            BillingClient.BillingResponseCode.ERROR,
            -> {
                callback(BILLING_RESPONSE_RESULT_ERROR,
                    "onSkuDetailsResponse: $responseCode $debugMessage")
                Log.e(TAG, "onSkuDetailsResponse: $responseCode $debugMessage")
                return
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                callback(BILLING_RESPONSE_RESULT_USER_CANCELED, "onSkuDetailsResponse: $responseCode $debugMessage")
                Log.i(TAG, "onSkuDetailsResponse: $responseCode $debugMessage")
                return
            }

            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                callback(BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED, "onSkuDetailsResponse: $responseCode $debugMessage")
                Log.wtf(TAG, "onSkuDetailsResponse: $responseCode $debugMessage")
                return
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                callback(BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED, "onSkuDetailsResponse: $responseCode $debugMessage")
                Log.wtf(TAG, "onSkuDetailsResponse: $responseCode $debugMessage")
                return
            }
            else -> {
                Log.wtf(TAG, "onSkuDetailsResponse: $responseCode $debugMessage")
                return
            }

        }
        skuDetailsResponseTime = if (responseCode == BillingClient.BillingResponseCode.OK) {
            SystemClock.elapsedRealtime()
        } else {
            -SKU_DETAILS_REQUERY_TIME
        }

        processSKUDetails()
    }

    /**
     * Calls the billing client functions to query sku details for both the inapp and subscription
     * SKUs. SKU details are useful for displaying item names and price lists to the user, and are
     * required to make a purchase.
     */
    private suspend fun querySkuDetailsAsync() {
        if (!iapSKUs.isNullOrEmpty()) {
            val skuDetailsResult = billingClient.querySkuDetails(
                SkuDetailsParams.newBuilder()
                    .setType(BillingClient.SkuType.INAPP)
                    .setSkusList(iapSKUs)
                    .build()
            )
            onSkuDetailsResponse(skuDetailsResult.billingResult, skuDetailsResult.skuDetailsList)
        }
        else {
            callback(BILLING_RESPONSE_RESULT_ERROR, "No IAP SKU provided.")
        }
    }
    /**
     * https://developer.android.com/google/play/billing/billing_library_releases_notes#2_0_acknowledge

     * If the purchase token is not acknowledged within 3 days,
     * then Google Play will automatically refund and revoke the purchase.
     * This behavior helps ensure that users are not charged unless the user has successfully
     * received access to the content.
     * This eliminates a category of issues where users complain to developers
     * that they paid for something that the app is not giving to them.
     */
    private fun processRestoredPurchasesList(purchases: List<PurchaseHistoryRecord>) {
        if (iapInfo?.isEmpty() == true) {
            Log.d(TAG, "No saved In-app info. Can't restore.")
            restoreCallback(BILLING_RESPONSE_RESULT_NO_RESTORE,"No saved In-app info. Skipping restore.")
            return
        }
        val gson = Gson();
        for (p in purchases) {
            val jsonText = p.originalJson
            val product = gson.fromJson(jsonText,PurchaseHistory::class.java)
            if (isValidIAP(product.productId)) {
                if (!isConsumableIAP(product.productId)) {
                    this.restoreCallback(BILLING_RESPONSE_RESULT_OK, product.productId)
                }
            }
        }
        this.restoreCallback(BILLING_RESPONSE_RESULT_RESTORE_COMPLETED, "All products have been restored")
    }

    private fun isValidIAP( sku: String): Boolean {
        iapInfo?.let {
            for (obj in it) {
                if (obj["id"] == sku) {
                    return true;
                }
            }
        }

        return false
    }

    private fun isConsumableIAP(sku: String): Boolean {
        iapInfo?.let {
            for (obj in it) {
                if (obj["id"] == sku) {
                    val type = obj["type"]
                    return type == "consumable"
                }
            }
        }
        return false
    }

    private fun processPurchaseList(purchases: List<Purchase>?) {
        if (null != purchases) {
            for (purchase in purchases) {
                val purchaseState = purchase.purchaseState
                if (purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!isSignatureValid(purchase)) {
                        Log.e(
                            TAG,
                            "Invalid signature. Check to make sure your " +
                                    "public key is correct."
                        )
                        continue
                    }
                    if (!purchase.isAcknowledged && !isConsumable) {
                        defaultScope.launch {
                            processNonConsumablePurchase(purchase)
                        }
                    }
                    else if (!purchase.isAcknowledged && isConsumable) {
                        defaultScope.launch {
                            consumePurchase(purchase)
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "Empty purchase list.")
            callback(BILLING_RESPONSE_RESULT_ERROR,"Empty purchase list.")
        }
    }

    private suspend fun processNonConsumablePurchase(purchase: Purchase, wasPending: Boolean = false) {
        for (sku in purchase.skus) {
            // Acknowledge item and change its state
            val billingResult = billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            )
            if (billingResult.responseCode !=
                BillingClient.BillingResponseCode.OK) {
                Log.e(
                    TAG,
                    "Error acknowledging purchase: ${purchase.skus}"
                )
                if (wasPending) {
                    pendingCallback(BILLING_RESPONSE_RESULT_ERROR,"Error acknowledging purchase: ${purchase.skus}")
                }
                else {
                    callback(BILLING_RESPONSE_RESULT_ERROR,"Error acknowledging purchase: ${purchase.skus}")
                }
            } else {
                if (wasPending) {
                    pendingCallback(BILLING_RESPONSE_RESULT_OK,"Purchase successful.")
                }
                else {
                    callback(BILLING_RESPONSE_RESULT_OK,"Purchase successful.")
                }
            }
        }
    }

    /**
     * Internal call only. Assumes that all signature checks have been completed and the purchase
     * is ready to be consumed. If the sku is already being consumed, does nothing.
     * @param purchase purchase to consume
     */
    private suspend fun consumePurchase(purchase: Purchase, wasPending: Boolean = false) {
        // weak check to make sure we're not already consuming the sku
        if (purchaseConsumptionInProcess.contains(purchase)) {
            if (wasPending) {
                pendingCallback(BILLING_RESPONSE_RESULT_ERROR,"Already consuming the product.")
                return
            }
            callback(BILLING_RESPONSE_RESULT_ERROR,"Already consuming the product.")
            return
        }
        purchaseConsumptionInProcess.add(purchase)
        val consumePurchaseResult = billingClient.consumePurchase(
            ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        )

        purchaseConsumptionInProcess.remove(purchase)
        if (consumePurchaseResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "Consumption successful. Emitting sku.")
            defaultScope.launch {
                if (wasPending) {
                    pendingCallback(BILLING_RESPONSE_RESULT_OK,"Purchase successful.")
                }
                else {
                    callback(BILLING_RESPONSE_RESULT_OK,"Purchase successful.")
                }
            }
        } else {
            if (wasPending) {
                pendingCallback(BILLING_RESPONSE_RESULT_ERROR,"Purchase was not successful.")
                return
            }
            callback(BILLING_RESPONSE_RESULT_ERROR,"Purchase was not successful.")
            Log.e(TAG, "Error while consuming: ${consumePurchaseResult.billingResult.debugMessage}")
        }
    }

    fun launchBillingFlow(activity: Activity?, sku: String, isConsumable:Boolean = false, callback: (Int,String) -> Void?) {

        this.callback = callback
        this.activity = activity
        this.activeSKU = sku
        this.isConsumable = isConsumable

        iapSKUs.clear()
        iapSKUs?.add(sku)

        defaultScope.launch {
            querySkuDetailsAsync()
        }
    }

    fun acknowledgePendingPurchases(activity: Activity?, callback: (Int, String) -> Void?) {
        this.activity = activity
        this.pendingCallback = callback
        defaultScope.launch {
            val purchasesResult =
                billingClient
                    .queryPurchasesAsync(BillingClient.SkuType.INAPP)
            val billingResult = purchasesResult.billingResult
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {

                val gson = Gson();
                for (p in purchasesResult.purchasesList) {
                    if (!p.isAcknowledged) {
                        val jsonText = p.originalJson
                        val product = gson.fromJson(jsonText,PurchaseHistory::class.java)
                        if (isValidIAP(product.productId)) {
                            if (isConsumableIAP(product.productId)) {
                                consumePurchase(p,true)
                            }
                            else {
                                processNonConsumablePurchase(p,true)
                            }
                        }
                    }
                }
            }
        }
    }

    fun restorePreviousIAPs(activity: Activity?,callback: (Int, String) -> Void?) {
        this.activity = activity
        this.restoreCallback = callback
        billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP
        ) { resultCode, response ->
            if (resultCode.responseCode == BillingClient.BillingResponseCode.OK) {
                if (response.isNullOrEmpty()) {
                    Log.d(TAG,"Nothing to restore.")
                    restoreCallback(BILLING_RESPONSE_RESULT_NO_RESTORE,"No purchase history found. Skipping Restore")
                }
                else {
                    processRestoredPurchasesList(response)
                }
            }
        }
    }

    private fun processSKUDetails() {
        var skuDetails: SkuDetails? = null
        if (activeSKU != null) {
            skuDetails = getProductSku(this.activeSKU!!)
        }
        if (null != skuDetails) {
            val billingFlowParamsBuilder = BillingFlowParams.newBuilder()
            billingFlowParamsBuilder.setSkuDetails(skuDetails)
            defaultScope.launch {
                val br = billingClient.launchBillingFlow(
                    activity!!,
                    billingFlowParamsBuilder.build()
                )
                if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingFlowInProcess.emit(true)
                } else {
                    Log.e(TAG, "Billing failed: + " + br.debugMessage)
                }
            }
        } else {
            Log.e(TAG, "SkuDetails not found for: $activeSKU")
        }
    }

    /**
     * Called by the BillingLibrary when new purchases are detected; typically in response to a
     * launchBillingFlow.
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, list: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> if (null != list) {
                processPurchaseList(list)
                return
            } else {
                callback(BILLING_RESPONSE_RESULT_ERROR,
                    "Null Purchase List Returned from OK response!")
                Log.d(TAG, "Null Purchase List Returned from OK response!")
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                callback(BILLING_RESPONSE_RESULT_USER_CANCELED, "User has cancelled the purchase.")
                Log.i(
                    TAG,
                    "onPurchasesUpdated: User canceled the purchase"
                )
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.i(
                    TAG,
                    "onPurchasesUpdated: The user already owns this item"
                )
                if (isConsumable) {
                    if (list != null) {
                        for (purchase in list) {
                            defaultScope.launch {
                                consumePurchase(purchase)
                            }
                        }
                        return
                    }
                } else {
                    callback(BILLING_RESPONSE_RESULT_OK, "Already owned. Restore")
                    return
                }
            }
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                callback(BILLING_RESPONSE_RESULT_DEVELOPER_ERROR,
                    "onPurchasesUpdated: Developer error means that Google Play \" +\n" +
                            "                        \"does not recognize the configuration. If you are just getting started, \" +\n" +
                            "                        \"make sure you have configured the application correctly in the \" +\n" +
                            "                        \"Google Play Console. The SKU product ID must match and the APK you \" +\n" +
                            "                        \"are using must be signed with release keys.")

                Log.e(
                    TAG,
                    "onPurchasesUpdated: Developer error means that Google Play " +
                            "does not recognize the configuration. If you are just getting started, " +
                            "make sure you have configured the application correctly in the " +
                            "Google Play Console. The SKU product ID must match and the APK you " +
                            "are using must be signed with release keys."
                )
            }
            else -> {
                callback(BILLING_RESPONSE_RESULT_ERROR,
                    "BillingResult [" + billingResult.responseCode + "]: " + billingResult.debugMessage)
                Log.d(
                    TAG,
                    "BillingResult [" + billingResult.responseCode + "]: " + billingResult.debugMessage
                )
            }
        }

        defaultScope.launch {
            billingFlowInProcess.emit(false)
        }
    }

    private fun isSignatureValid(purchase: Purchase): Boolean {
        return  true //Security.verifyPurchase(purchase.originalJson, purchase.signature)
    }

    companion object {
        private val TAG = "BB3" + BillingDataSource::class.java.simpleName

        @Volatile
        var sInstance: BillingDataSource? = null
        private val handler = Handler(Looper.getMainLooper())

        @JvmStatic
        fun initialize(
            application: Application,
            defaultScope: CoroutineScope,
        ) = sInstance ?: synchronized(this) {
            sInstance ?: BillingDataSource(
                application,
                defaultScope
            ).also { sInstance = it }
        }
    }
}

data class PurchaseHistory(
    val productId: String
)