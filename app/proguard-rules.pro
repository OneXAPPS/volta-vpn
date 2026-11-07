# VPN engine rules come from vpnlib/consumer-rules.pro.

# Room databases (WorkManager's, pulled in by the Ads SDK) are created reflectively
# through their generated *_Impl no-arg constructor; R8 full mode strips it otherwise
# ("Failed to create an instance of androidx.work.impl.WorkDatabase" at startup).
-keep class * extends androidx.room.RoomDatabase { <init>(); }

