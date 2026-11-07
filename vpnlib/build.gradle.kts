// OpenVPN engine: upstream ics-openvpn (GPLv2) built as a library.
// Upstream sources live untouched in ./ics (except a one-line CMake switch);
// only the "skeleton" (no UI) + OpenVPN 2 parts are compiled.
plugins {
    alias(libs.plugins.android.library)
}

val ics = "ics/main/src"

// ics is a git submodule; re-apply our one-line CMake switch (skip OpenVPN 3 / SWIG) after checkout.
file("$ics/main/cpp/CMakeLists.txt").takeIf { it.exists() }?.let { cmake ->
    val text = cmake.readText()
    if ("OVPN_SKELETON" !in text) cmake.writeText(
        text.replace(
            "if (NOT \${CMAKE_LIBRARY_OUTPUT_DIRECTORY} MATCHES",
            "if (NOT OVPN_SKELETON AND NOT \${CMAKE_LIBRARY_OUTPUT_DIRECTORY} MATCHES",
        )
    )
}

android {
    namespace = "de.blinkt.openvpn"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    defaultConfig {
        minSdk = 28
        // Upstream code checks these flavor-generated fields.
        buildConfigField("String", "FLAVOR", "\"skeleton\"")
        buildConfigField("boolean", "openvpn3", "false")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                // CMake 4 rejects the upstream cmake_minimum_required(3.4.1) without this.
                arguments += listOf(
                    "-DOVPN_SKELETON=ON", "-DCMAKE_POLICY_VERSION_MINIMUM=3.5",
                    // Size: drop unreferenced functions/data and fold identical code at link time.
                    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections -Wl,--icf=safe",
                    "-DCMAKE_EXE_LINKER_FLAGS=-Wl,--gc-sections -Wl,--icf=safe",
                )
                cFlags += listOf("-ffunction-sections", "-fdata-sections")
                cppFlags += listOf("-ffunction-sections", "-fdata-sections")
            }
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("$ics/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.directories.addAll(listOf("$ics/main/java", "$ics/skeleton/java"))
            kotlin.directories.addAll(listOf("$ics/main/java", "$ics/skeleton/java"))
            aidl.directories.add("$ics/main/aidl")
            res.directories.addAll(listOf("$ics/main/res", "$ics/skeleton/res"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
}
