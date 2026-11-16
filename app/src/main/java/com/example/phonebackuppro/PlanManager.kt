package com.example.phonebackuppro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Single place that decides what each plan may do.
 *
 * Free:    export EVERYTHING of a section (all contacts / all calls / all messages) into
 *          Downloads/PhoneBackupPro, import, browse and search.
 * Premium: pick exactly which people to export (checkboxes, filters) and save the file
 *          anywhere through the system file picker.
 *
 * The flag is written only by [BillingManager] after Cafe Bazaar confirmed the purchase.
 * If Bazaar cannot be reached the last confirmed state stays valid for [GRACE_MS] (30 days).
 */
class PlanManager(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("plan", Context.MODE_PRIVATE)

    var isPaid by mutableStateOf(compute())
        private set
    val canSelect get() = isPaid
    val canChooseLocation get() = isPaid

    private fun compute(): Boolean {
        if (BuildConfig.DEMO_PREMIUM) return true   // premium test build: everything unlocked
        val paid = prefs.getBoolean("paid", false)
        // last time Bazaar really confirmed the purchase (old installs only had "verifiedAt")
        val at = prefs.getLong("okAt", prefs.getLong("verifiedAt", 0L))
        val age = System.currentTimeMillis() - at
        // age < 0 = the phone clock was moved back: do not lock the user out because of that
        return paid && (age < GRACE_MS)
    }

    /**
     * Price text exactly as Cafe Bazaar shows it (e.g. "۲۰۰٬۰۰۰ تومان"). Read from Bazaar every time
     * the app connects and cached, so a price change in the Bazaar panel appears in the app by itself.
     * Until Bazaar answers for the first time, [Config.PRICE_TOMAN] is shown.
     */
    var price by mutableStateOf(cachedPrice(ctx))
        private set

    fun updatePrice(text: String) {
        if (text.isBlank() || text == price) return
        prefs.edit().putString("price", text).apply()
        price = text
    }

    /** Called with the verified state coming from Cafe Bazaar (a real purchase, or an explicit "restore"). */
    fun setVerified(paid: Boolean) {
        if (BuildConfig.DEMO_PREMIUM) return
        val now = System.currentTimeMillis()
        val e = prefs.edit().putBoolean("paid", paid).putLong("verifiedAt", now).putInt("misses", 0)
        if (paid) e.putLong("okAt", now)
        e.apply()
        isPaid = paid
        info = readInfo()
    }

    /**
     * Bazaar answered "this account owns nothing". An explicit user check removes premium at once; a silent
     * background check does NOT (Bazaar sometimes answers with an empty list while it is starting, logged out or
     * offline-ish). A silent "no" only counts after 3 of them in a row AND no confirmation for 2 days.
     */
    fun reportNotOwned(explicit: Boolean) {
        if (BuildConfig.DEMO_PREMIUM) return
        if (explicit) { setVerified(false); return }
        if (!prefs.getBoolean("paid", false)) return
        val misses = prefs.getInt("misses", 0) + 1
        prefs.edit().putInt("misses", misses).apply()
        val okAt = prefs.getLong("okAt", prefs.getLong("verifiedAt", 0L))
        if (misses >= 3 && System.currentTimeMillis() - okAt > 2L * 24 * 60 * 60 * 1000) setVerified(false)
    }

    /** What Cafe Bazaar told us about the purchase (shown in the plan screen). */
    data class Info(
        val productId: String = "",
        val orderId: String = "",
        val purchaseTime: Long = 0L,
        val verifiedAt: Long = 0L
    )

    var info by mutableStateOf(readInfo())
        private set

    private fun readInfo() = if (BuildConfig.DEMO_PREMIUM) DemoData.purchaseInfo() else Info(
        prefs.getString("pid", "") ?: "",
        prefs.getString("order", "") ?: "",
        prefs.getLong("ptime", 0L),
        prefs.getLong("verifiedAt", 0L)
    )

    /** Saves the purchase record that Bazaar returned. Unknown fields stay empty. */
    fun setPurchase(productId: String, orderId: String, purchaseTime: Long) {
        prefs.edit().putString("pid", productId).putString("order", orderId).putLong("ptime", purchaseTime).apply()
        info = readInfo()
        addHistory(productId, orderId, purchaseTime)
    }

    /** One line of the subscription history screen. */
    data class Entry(val productId: String, val orderId: String, val time: Long)

    /** Every purchase record Bazaar ever returned on this phone (newest first). */
    var history by mutableStateOf(readHistory())
        private set

    private fun readHistory(): List<Entry> {
        if (BuildConfig.DEMO_PREMIUM) return DemoData.history()
        return (prefs.getString("history", "") ?: "").lines().mapNotNull { l ->
            val a = l.split("|")
            if (a.size == 3) Entry(a[0], a[1], a[2].toLongOrNull() ?: 0L) else null
        }.sortedByDescending { it.time }
    }

    private fun addHistory(productId: String, orderId: String, time: Long) {
        if (BuildConfig.DEMO_PREMIUM || (orderId.isBlank() && time == 0L)) return
        val all = history
        if (all.any { it.orderId == orderId && it.time == time }) return
        val line = (all + Entry(productId, orderId, time)).joinToString("\n") { "${it.productId}|${it.orderId}|${it.time}" }
        prefs.edit().putString("history", line).apply()
        history = readHistory()
    }

    companion object {
        private const val GRACE_MS = 30L * 24 * 60 * 60 * 1000

        /** Last price read from Bazaar (or the fallback from Config). Also used by background notifications. */
        fun cachedPrice(ctx: Context): String =
            ctx.getSharedPreferences("plan", Context.MODE_PRIVATE).getString("price", null)?.takeIf { it.isNotBlank() }
                ?: "${Config.PRICE_TOMAN.fa()} تومان"
    }
}
