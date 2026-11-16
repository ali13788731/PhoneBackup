package com.example.phonebackuppro

/**
 * Sample purchase info used ONLY by the "premium" test build (BuildConfig.DEMO_PREMIUM = true), so every
 * premium screen can be reviewed with full lists. The store (free) build never touches this.
 */
object DemoData {
    private const val DAY = 24L * 60 * 60 * 1000

    fun purchaseInfo(): PlanManager.Info {
        val now = System.currentTimeMillis()
        return PlanManager.Info(
            productId = Config.PREMIUM_SKU,
            orderId = "BAZAAR-DEMO-8841207",
            purchaseTime = now - 12 * DAY,
            verifiedAt = now
        )
    }

    fun history(): List<PlanManager.Entry> {
        val now = System.currentTimeMillis()
        return listOf(12L, 42L, 72L, 102L).map {
            PlanManager.Entry(Config.PREMIUM_SKU, "BAZAAR-DEMO-" + (8841207 - it * 313), now - it * DAY)
        }
    }
}
