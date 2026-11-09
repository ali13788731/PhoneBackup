package com.example.phonebackuppro

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.request.PurchaseRequest

/**
 * Cafe Bazaar billing (Poolakey). All the billing code of the app is in this one class.
 * The result is only ever written into [PlanManager]; UI just reads [PlanManager.isPaid].
 */
class BillingManager(
    private val activity: ComponentActivity,
    private val plan: PlanManager
) {
    /** Text for the UI to show in a message dialog (null = nothing to show). */
    var message by mutableStateOf<String?>(null)
    var working by mutableStateOf(false)
        private set
    private var connection: Connection? = null
    private var connected = false
    private var buyAfterConnect = false

    private val payment: Payment by lazy {
        val check = if (Config.BAZAAR_RSA_KEY.isBlank()) SecurityCheck.Disable
        else SecurityCheck.Enable(rsaPublicKey = Config.BAZAAR_RSA_KEY)
        Payment(context = activity, config = PaymentConfiguration(localSecurityCheck = check))
    }

    fun start() {
        if (connection != null) return
        connection = payment.connect {
            connectionSucceed {
                connected = true
                if (buyAfterConnect) { buyAfterConnect = false; launchPurchase() } else queryOwned(silent = true)
            }
            connectionFailed {
                connected = false
                connection = null
                if (buyAfterConnect) {
                    buyAfterConnect = false
                    working = false
                    message = "اتصال به کافه‌بازار برقرار نشد. مطمئن شوید کافه‌بازار نصب و وارد حساب شده است."
                }
            }
            disconnected {
                connected = false
                connection = null
            }
        }
    }

    fun stop() {
        runCatching { connection?.disconnect() }
        connection = null
        connected = false
    }

    /** Starts the purchase screen of Cafe Bazaar. */
    fun buy() {
        working = true
        if (!connected) {
            buyAfterConnect = true
            start()
        } else launchPurchase()
    }

    /** "Restore purchases": asks Bazaar again whether this account owns the product. */
    fun restore() {
        if (!connected) {
            start()
            message = "در حال اتصال به کافه‌بازار… چند ثانیه بعد دوباره امتحان کنید."
        } else {
            working = true
            queryOwned(silent = false)
        }
    }

    private fun launchPurchase() {
        val request = PurchaseRequest(productId = Config.PREMIUM_SKU, payload = "pbp")
        if (Config.USE_SUBSCRIPTION) {
            payment.subscribeProduct(registry = activity.activityResultRegistry, request = request) {
                purchaseFlowBegan { }
                failedToBeginFlow { working = false; message = "شروع پرداخت ممکن نشد." }
                purchaseSucceed { info -> savePurchase(info); working = false; plan.setVerified(true); message = "پریمیوم فعال شد. ممنون از حمایت شما 💜" }
                purchaseCanceled { working = false }
                purchaseFailed { _ -> working = false; message = "پرداخت ناموفق بود. مبلغی کسر نشده است." }
            }
        } else {
            payment.purchaseProduct(registry = activity.activityResultRegistry, request = request) {
                purchaseFlowBegan { }
                failedToBeginFlow { working = false; message = "شروع پرداخت ممکن نشد." }
                purchaseSucceed { info -> savePurchase(info); working = false; plan.setVerified(true); message = "پریمیوم فعال شد. ممنون از حمایت شما 💜" }
                purchaseCanceled { working = false }
                purchaseFailed { _ -> working = false; message = "پرداخت ناموفق بود. مبلغی کسر نشده است." }
            }
        }
    }

    /** Copies what Bazaar returned about the purchase (read by name, so it never breaks the build). */
    private fun savePurchase(info: Any) {
        fun read(name: String): Any? = runCatching { info.javaClass.getMethod("get$name").invoke(info) }.getOrNull()
        plan.setPurchase(
            productId = read("ProductId")?.toString().orEmpty(),
            orderId = read("OrderId")?.toString().orEmpty(),
            purchaseTime = (read("PurchaseTime") as? Number)?.toLong() ?: 0L
        )
    }

    private fun queryOwned(silent: Boolean) {
        val onResult = { owned: Boolean ->
            working = false
            plan.setVerified(owned)
            if (!silent) message = if (owned) "اشتراک پریمیوم شما فعال است." else "خرید فعالی برای این حساب پیدا نشد."
        }
        val onError = {
            working = false
            if (!silent) message = "بررسی خرید ناموفق بود. اتصال اینترنت و کافه‌بازار را بررسی کنید."
        }
        if (Config.USE_SUBSCRIPTION) {
            payment.getSubscribedProducts {
                queryFailed { _ -> onError() }
                querySucceed { list ->
                    list.firstOrNull { it.productId == Config.PREMIUM_SKU }?.let { savePurchase(it) }
                    onResult(list.any { it.productId == Config.PREMIUM_SKU })
                }
            }
        } else {
            payment.getPurchasedProducts {
                queryFailed { _ -> onError() }
                querySucceed { list ->
                    list.firstOrNull { it.productId == Config.PREMIUM_SKU }?.let { savePurchase(it) }
                    onResult(list.any { it.productId == Config.PREMIUM_SKU })
                }
            }
        }
    }
}
