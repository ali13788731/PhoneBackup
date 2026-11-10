plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.phonebackuppro"
    compileSdk = 34
    defaultConfig {
        applicationId = "ir.mrpc.phonebackuppro"
        minSdk = 29
        targetSdk = 34
        versionCode = 4
        versionName = "2.2"
        // the UI is Persian (+ English fallback): drop the translations of every library
        resourceConfigurations += listOf("fa", "en")
        buildConfigField("String", "BAZAAR_RSA_KEY", "\"$bazaarRsaKey\"")
    }
    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin")
    }
    // Release signing: set these environment variables (see README) or sign in Android Studio.
    // Either environment variables (GitHub Actions) or a local keystore.properties file in the project root
    // (see README / store/PUBLISH-CHECKLIST-fa.md). keystore.properties and *.jks are git-ignored.
    val ksProps = java.util.Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun ksValue(env: String, prop: String): String? = System.getenv(env) ?: ksProps.getProperty(prop)
    val ksFile = ksValue("KEYSTORE_FILE", "storeFile")
    // Cafe Bazaar RSA public key: GitHub Secret BAZAAR_RSA_KEY (or bazaarRsaKey in keystore.properties)
    val bazaarRsaKey = (ksValue("BAZAAR_RSA_KEY", "bazaarRsaKey") ?: "").filterNot { it.isWhitespace() || it == '"' }
    signingConfigs {
        if (ksFile != null) {
            create("release") {
                storeFile = rootProject.file(ksFile)
                storePassword = ksValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = ksValue("KEY_ALIAS", "keyAlias")
                keyPassword = ksValue("KEY_PASSWORD", "keyPassword")
            }
        }
    }
    // Two builds of the same app:
    //  free    = the real store build (billing through Cafe Bazaar, premium locked until purchased)
    //  premium = TEST build: every premium feature unlocked + sample data, installs next to "free".
    flavorDimensions += "tier"
    productFlavors {
        create("free") {
            dimension = "tier"
            buildConfigField("boolean", "DEMO_PREMIUM", "false")
            resValue("string", "app_name", "Phone Backup")
        }
        create("premium") {
            dimension = "tier"
            applicationIdSuffix = ".premium"
            versionNameSuffix = "-premium"
            buildConfigField("boolean", "DEMO_PREMIUM", "true")
            resValue("string", "app_name", "Phone Backup Premium")
        }
    }
    buildTypes {
        // Debug APKs are shrunk too (R8 removes the unused part of material-icons-extended, which is
        // what made the APK ~54 MB). Still installable + debuggable.
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No keystore given -> the release build stays UNSIGNED on purpose (Bazaar must never receive a
            // debug-signed file; the signing key can't be changed later). Sign with Android Studio's
            // "Generate Signed Bundle / APK" or set the KEYSTORE_* environment variables.
            signingConfig = if (ksFile != null) signingConfigs.getByName("release") else null
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Cafe Bazaar in-app billing
    implementation("com.github.cafebazaar.Poolakey:poolakey:2.2.0")
}

// A store (free) release without the Bazaar RSA key must never be built
gradle.taskGraph.whenReady {
    val storeRelease = allTasks.any { it.name.endsWith("FreeRelease", ignoreCase = true) && it.name.startsWith("assemble") }
    val key = System.getenv("BAZAAR_RSA_KEY") ?: rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.let { f -> java.util.Properties().apply { f.inputStream().use { load(it) } }.getProperty("bazaarRsaKey") }
    if (storeRelease && key.isNullOrBlank()) {
        throw GradleException("BAZAAR_RSA_KEY is empty. Set the GitHub Secret BAZAAR_RSA_KEY (or bazaarRsaKey in keystore.properties).")
    }
}
