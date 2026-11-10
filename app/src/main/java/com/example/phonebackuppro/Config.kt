package com.example.phonebackuppro

/** Everything you need to edit before publishing on Cafe Bazaar lives here. */
object Config {
    /**
     * RSA public key of your app, copied from the Cafe Bazaar developer panel
     * (Developer panel > your app > In-app billing). It is NOT written here: put it in the GitHub
     * Secret BAZAAR_RSA_KEY (or bazaarRsaKey in keystore.properties) and the build injects it.
     * Debug builds may leave it blank (Bazaar-only verification).
     */
    val BAZAAR_RSA_KEY: String = BuildConfig.BAZAAR_RSA_KEY

    /** The product id you create in the Bazaar panel. */
    const val PREMIUM_SKU = "premium_access"

    /** true = the product is a subscription ("اشتراک"), false = one-time in-app product. */
    const val USE_SUBSCRIPTION = false

    /** Only used for display; the real price is the one you set in the Bazaar panel. */
    const val PRICE_TOMAN = 200_000

    /**
     * Address of a small JSON file YOU host (GitHub raw, your site...) that controls updates:
     *   {"latestVersionCode": 4, "minVersionCode": 3, "latestVersionName": "2.2", "notes": "..."}
     * - latestVersionCode > installed  -> notification "new version available"
     * - minVersionCode    > installed  -> the app refuses to work until it is updated (forced update)
     * Leave blank to disable the check.
     */
    const val UPDATE_JSON_URL = ""

    /** How often (hours) the background update check runs and re-shows the update notification. */
    const val UPDATE_CHECK_HOURS = 3L

    /** How often (hours) the "buy premium" reminder notification may appear for free users. */
    const val REMINDER_HOURS = 6L

    const val DEVELOPER = "Mr.PC"
    const val BACKUP_FOLDER = "PhoneBackupPro"
}
