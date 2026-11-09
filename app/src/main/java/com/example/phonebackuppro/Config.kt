package com.example.phonebackuppro

/** Everything you need to edit before publishing on Cafe Bazaar lives here. */
object Config {
    /**
     * RSA public key of your app, copied from the Cafe Bazaar developer panel
     * (Developer panel > your app > In-app billing). Leave it blank while testing:
     * purchase verification is then done by Bazaar only (no local signature check).
     */
    const val BAZAAR_RSA_KEY = ""

    /** The product id you create in the Bazaar panel. */
    const val PREMIUM_SKU = "premium_access"

    /** true = the product is a subscription ("اشتراک"), false = one-time in-app product. */
    const val USE_SUBSCRIPTION = true

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

    /** How often (hours) the "buy premium" reminder notification may appear for free users. */
    const val REMINDER_HOURS = 6L

    const val DEVELOPER = "Mr.PC"
    const val BACKUP_FOLDER = "PhoneBackupPro"
}
