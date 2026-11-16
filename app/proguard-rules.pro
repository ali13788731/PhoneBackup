# Cafe Bazaar billing (Poolakey)
-keep class ir.cafebazaar.poolakey.** { *; }
-dontwarn ir.cafebazaar.poolakey.**

# Compose / coroutines / WorkManager are covered by their own consumer rules.
-dontwarn kotlinx.coroutines.debug.**
-dontwarn org.slf4j.**
