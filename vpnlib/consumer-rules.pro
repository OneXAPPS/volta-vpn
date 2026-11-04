# OpenVPN engine uses JNI and reflection on these packages.
-keep class de.blinkt.openvpn.** { *; }
-keep class net.openvpn.ovpn3.** { *; }
-dontwarn net.openvpn.ovpn3.**
