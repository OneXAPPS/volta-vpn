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
            "\"https://raw.githubusercontent.com/OneXAPPS/volta-vpn/data/servers.json," +
                "https://cdn.jsdelivr.net/gh/OneXAPPS/volta-vpn@data/servers.json\"",
        )
        // Base64 X.509 (SPKI) P-256 public key matching backend/keys/private.pem.
        buildConfigField("String", "SERVER_LIST_PUBKEY", "\"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEP2zrDaZHc9ushraLm4Z9G+2yVCOirF7Iw32fTaLXxCxkl3VlPnh8nVeABycmMUGIVfJ44eVfu5nLQvT6fzHfig==\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    androidResources {
        localeFilters += listOf("en", "ar")
    }

    buildTypes {
        debug {
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
