# VPN engine rules come from vpnlib/consumer-rules.pro.

# Room databases (WorkManager's, pulled in by the Ads SDK) are created reflectively
# through their generated *_Impl no-arg constructor; R8 full mode strips it otherwise
# ("Failed to create an instance of androidx.work.impl.WorkDatabase" at startup).
-keep class * extends androidx.room.RoomDatabase { <init>(); }


# ---- Harder to reverse engineer ----
# Every obfuscated class moved into one flat, unnamed package; no original file names.
-repackageclasses ''
-allowaccessmodification
-renamesourcefileattribute ''

# Logging compiled out of release builds (nothing about servers or the user ends up in logcat).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static boolean isLoggable(java.lang.String, int);
}

# Kotlin's null checks embed parameter and field names as strings; drop them.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
    public static void checkReturnedValueIsNotNull(...);
}
