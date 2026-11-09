plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.voltavpn.app"
    compileSdk = 37
    // Same NDK as vpnlib, so release native libs get stripped.
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.voltavpn.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        // Signed server list produced by backend/, comma-separated mirrors tried in order
        // (empty = fetch VPN Gate directly).
        buildConfigField(
            "String", "SERVER_LIST_URLS",
            // jsDelivr first: a CDN built for heavy traffic (raw.githubusercontent is not). Its
            // Fastly and Gcore edges are separate networks, so one blocked CDN isn't the end;
            // GitHub Pages last. The list is signed, so no host has to be trusted.
            "\"https://cdn.jsdelivr.net/gh/OneXAPPS/volta-vpn@data/servers.json," +
                "https://fastly.jsdelivr.net/gh/OneXAPPS/volta-vpn@data/servers.json," +
                "https://gcore.jsdelivr.net/gh/OneXAPPS/volta-vpn@data/servers.json," +
                "https://raw.githubusercontent.com/OneXAPPS/volta-vpn/data/servers.json," +
                "https://onexapps.github.io/volta-vpn/servers.json\"",
        )
        // Base64 X.509 (SPKI) P-256 public keys, comma-separated: backend/keys/private.pem (in use),
        // then backend/keys/spare_private.pem (kept OFFLINE; switch the backend to it if the
        // first key is ever exposed, then ship an update that drops the old key).
        buildConfigField(
            "String", "SERVER_LIST_PUBKEY",
            "\"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEP2zrDaZHc9ushraLm4Z9G+2yVCOirF7Iw32fTaLXxCxkl3VlPnh8nVeABycmMUGIVfJ44eVfu5nLQvT6fzHfig==," +
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAESRNhhitE7ZNXLWy3iXMSpknRIwZPNB5R+/MFqhFfHjeP/KpPk2fbEZQ5zFMEN9sGU5nfzjeHY8yRMFNcZfB6tA==\"",
        )

        // SHA-256 of the certificate(s) allowed to sign this app, comma-separated; a copy signed
        // with any other key refuses to connect (security/Integrity.kt). Empty = check off.
        // Put the "App signing key certificate" SHA-256 from Play Console → Test and release →
        // App integrity, plus your upload key's if you also hand out APKs signed with it.
        buildConfigField("String", "SIGNER_SHA256", "\"\"")

        ndk {
            // Real phones only; x86_64 (emulators) is added for debug builds below.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    androidResources {
        // Lets users pick Volta's language in Android settings (Android 13+).
        generateLocaleConfig = true
        // English + Arabic + the main languages of the countries Volta targets.
        localeFilters += listOf(
            "en", "ar", "es", "fr", "de", "pt", "tr", "ru", "uk",
            "fa", "ur", "hi", "bn", "zh-rCN", "tl", "in",
        )
    }

    buildTypes {
        debug {
            ndk { abiFilters += "x86_64" }
            // Google's official test IDs. Never click real ads while developing.
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "REWARDED_AD_UNIT", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real AdMob IDs (Volta VPN / "24H Rewarded").
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3992840462159319~5850229995"
            buildConfigField("String", "REWARDED_AD_UNIT", "\"ca-app-pub-3992840462159319/7191231821\"")
        }
        // Release-optimized build for testing on your own phone: Google test ads
        // (never risk the AdMob account) and the debug signing key.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            // Debug-signed, so the release signer check would (rightly) refuse it.
            buildConfigField("String", "SIGNER_SHA256", "\"\"")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "REWARDED_AD_UNIT", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // The OpenVPN binary (libovpnexec.so) must be extracted to disk to be executed.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(project(":vpnlib"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Ads SDK pulls an old Fragment that breaks the Activity Result API.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.play.services.ads)
    implementation(libs.ump)
}
