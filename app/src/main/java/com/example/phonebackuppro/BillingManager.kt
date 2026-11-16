package com.example.phonebackuppro

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.Handler
import android.os.Looper
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

    /** What the user asked for while the connection to Bazaar was still being opened. */
    private var pending: (() -> Unit)? = null
    private var pendingIsUserAction = false

    private var querying = false
    private var loudRequested = false
    private var lastQueryAt = 0L
    private val main = Handler(Looper.getMainLooper())

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
                fetchPrice()
                val job = pending
                pending = null
                pendingIsUserAction = false
                if (job != null) job() else queryOwned(silent = true)
            }
            connectionFailed {
                connected = false
                connection = null
                val wasUser = pendingIsUserAction
                pending = null
                pendingIsUserAction = false
                working = false
                if (wasUser) message = "اتصال به کافه‌بازار برقرار نشد. مطمئن شوید کافه‌بازار نصب و وارد حساب شده است."
            }
            disconnected {
                connected = false
                connection = null
            }
        }
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        runCatching { connection?.disconnect() }
        connection = null
        connected = false
        pending = null
        querying = false
    }

    /** Called when the app comes back to the foreground: quietly re-checks (e.g. after buying inside Bazaar). */
    fun refresh() {
        if (BuildConfig.DEMO_PREMIUM) return
        if (!connected) { start(); return }
        if (System.currentTimeMillis() - lastQueryAt < 30_000L) return
        queryOwned(silent = true)
    }

    /** Starts the purchase screen of Cafe Bazaar. */
    fun buy() {
        working = true
        runWhenConnected { launchPurchase() }
    }

    /** "Restore purchases": asks Bazaar again whether this account owns the product. */
    fun restore() {
        working = true
        runWhenConnected { queryOwned(silent = false) }
    }

    /** Runs [job] now, or right after the connection to Bazaar is ready (also when it is still being opened). */
    private fun runWhenConnected(job: () -> Unit) {
        if (connected) { job(); return }
        pending = job
        pendingIsUserAction = true
        start()
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

    /** A refunded / cancelled record must not count as "owned". Unknown state = owned. */
    private fun isActive(info: Any): Boolean {
        val state = runCatching { info.javaClass.getMethod("getPurchaseState").invoke(info)?.toString() }.getOrNull()
        return state == null || !(state.contains("REFUND", true) || state.contains("CANCEL", true))
    }

    /** Reads the current price of [Config.PREMIUM_SKU] from Cafe Bazaar and stores it in [PlanManager.price]. */
    private fun fetchPrice() {
        val ids = listOf(Config.PREMIUM_SKU)
        fun handle(list: List<Any>) {
            val item = list.firstOrNull() ?: return
            val price = runCatching { item.javaClass.getMethod("getPrice").invoke(item)?.toString() }.getOrNull()
            if (!price.isNullOrBlank()) plan.updatePrice(price.trim())
        }
        if (Config.USE_SUBSCRIPTION) {
            payment.getSubscriptionSkuDetails(skuIds = ids) {
                getSkuDetailsSucceed { list -> handle(list) }
                getSkuDetailsFailed { _ -> }
            }
        } else {
            payment.getInAppSkuDetails(skuIds = ids) {
                getSkuDetailsSucceed { list -> handle(list) }
                getSkuDetailsFailed { _ -> }
            }
        }
    }

    /**
     * Asks Bazaar whether the account owns the premium product.
     *
     * Both lists are asked (in-app purchases AND subscriptions), whatever [Config.USE_SUBSCRIPTION] says: the
     * product type chosen in the Bazaar panel and the one in Config can differ, and asking only one of them
     * was the reason a real purchase was "not found" by the re-check button.
     *
     * An answer is only trusted as "not owned" when BOTH lists answered successfully and neither has the
     * product. A failed / timed-out query never removes premium.
     */
    private fun queryOwned(silent: Boolean) {
        if (querying) { if (!silent) loudRequested = true; return }
        querying = true
        lastQueryAt = System.currentTimeMillis()

        var answered = 0
        var failed = false
        var owned = false
        var finished = false

        fun finish() {
            if (finished) return
            finished = true
            main.removeCallbacksAndMessages(null)
            querying = false
            val again = loudRequested
            loudRequested = false
            when {
                owned -> {
                    plan.setVerified(true)
                    if (!silent || again) { working = false; message = "اشتراک پریمیوم شما فعال است." } else working = false
                }
                failed -> {
                    working = false
                    if (!silent || again) message = "بررسی خرید ناموفق بود. اتصال اینترنت و کافه‌بازار را بررسی کنید."
                }
                else -> {
                    plan.reportNotOwned(explicit = !silent || again)
                    working = false
                    if (!silent || again) message = "خرید فعالی برای این حساب پیدا نشد. مطمئن شوید با همان حسابی وارد کافه‌بازار هستید که خرید را با آن انجام داده‌اید."
                }
            }
        }
        fun oneDone() { answered++; if (answered == 2) finish() }

        // safety net: a callback that never comes must not leave the button spinning forever
        main.postDelayed({ failed = true; finish() }, 20_000L)

        payment.getPurchasedProducts {
            queryFailed { _ -> failed = true; oneDone() }
            querySucceed { list ->
                list.firstOrNull { it.productId == Config.PREMIUM_SKU && isActive(it) }?.let { savePurchase(it); owned = true }
                oneDone()
            }
        }
        payment.getSubscribedProducts {
            queryFailed { _ -> failed = true; oneDone() }
            querySucceed { list ->
                list.firstOrNull { it.productId == Config.PREMIUM_SKU && isActive(it) }?.let { savePurchase(it); owned = true }
                oneDone()
            }
        }
    }
}
