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

// Fail-closed server switching. Upstream closes the tunnel when the old OpenVPN process
// exits and only opens a new one once the next server answers, so for those seconds traffic
// goes out over the normal network. The service now keeps its own copy of the tunnel until
// the next one is up: in between, packets go into a tunnel nobody reads, so they're dropped,
// never leaked. An explicit stop releases it at once; an unexpected exit after 15 s, so a
// crashed engine can't leave the phone offline for good.
file("$ics/main/java/de/blinkt/openvpn/core/OpenVPNService.java").takeIf { it.exists() }?.let { svc ->
    val original = svc.readText()
    val crlf = "\r\n" in original
    var text = original.replace("\r\n", "\n")
    if ("mHeldTun" !in text) {
        fun patch(anchor: String, replacement: String) {
            check(anchor in text) { "OpenVPNService patch: anchor not found: $anchor" }
            text = text.replaceFirst(anchor, replacement)
        }
        patch(
            "    private TunConfig mLastTunCfg;\n",
            """    private TunConfig mLastTunCfg;
    private ParcelFileDescriptor mHeldTun;
    private final Runnable mReleaseHeldTun = this::releaseHeldTun;

    private synchronized void holdTun(ParcelFileDescriptor tun) {
        ParcelFileDescriptor old = mHeldTun;
        try { mHeldTun = tun.dup(); } catch (java.io.IOException e) { mHeldTun = null; }
        if (old != null) try { old.close(); } catch (java.io.IOException ignored) { }
    }

    public synchronized void releaseHeldTun() {
        if (mCommandHandler != null) mCommandHandler.removeCallbacks(mReleaseHeldTun);
        if (mHeldTun != null) try { mHeldTun.close(); } catch (java.io.IOException ignored) { }
        mHeldTun = null;
    }
""",
        )
        patch(
            "            if (tun == null)\n                throw new NullPointerException(\"Android establish() method returned null (Really broken network configuration?)\");\n            return tun;",
            "            if (tun == null)\n                throw new NullPointerException(\"Android establish() method returned null (Really broken network configuration?)\");\n            holdTun(tun);\n            return tun;",
        )
        patch(
            "        mStarting = true;\n",
            "        mStarting = true;\n        if (mCommandHandler != null) mCommandHandler.removeCallbacks(mReleaseHeldTun);\n",
        )
        patch(
            "        mOpenVPNThread = null;\n        if (!mStarting) {\n",
            "        mOpenVPNThread = null;\n        if (!mStarting) {\n            if (mCommandHandler != null) mCommandHandler.postDelayed(mReleaseHeldTun, 15000);\n",
        )
        patch(
            "    public boolean stopVPN(boolean replaceConnection) throws RemoteException {\n        if (getManagement() != null)",
            "    public boolean stopVPN(boolean replaceConnection) throws RemoteException {\n        if (!replaceConnection) releaseHeldTun();\n        if (getManagement() != null)",
        )
        patch(
            "    public void onRevoke() {\n",
            "    public void onRevoke() {\n        releaseHeldTun();\n",
        )
        patch(
            "    public void onDestroy() {\n",
            "    public void onDestroy() {\n        releaseHeldTun();\n",
        )
        svc.writeText(if (crlf) text.replace("\n", "\r\n") else text)
    }
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
