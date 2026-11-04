package com.voltavpn.app

import android.app.Application
import com.voltavpn.app.vpn.VpnController
import de.blinkt.openvpn.core.ICSOpenVPNApplication

class VoltaApp : ICSOpenVPNApplication() {
    override fun onCreate() {
        super.onCreate()
        // The VPN engine runs in its own ":openvpn" process; UI state lives in the main one.
        if (Application.getProcessName() == packageName) VpnController.init()
    }
}
