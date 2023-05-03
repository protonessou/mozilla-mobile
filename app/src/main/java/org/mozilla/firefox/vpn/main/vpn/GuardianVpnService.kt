package org.mozilla.firefox.vpn.main.vpn

import android.content.Context
import android.content.Intent
import android.util.Log
import com.wireguard.android.backend.TunnelManager
import com.wireguard.android.backend.WireGuardVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus
import kotlinx.coroutines.launch
import org.mozilla.firefox.vpn.GuardianApp
import org.mozilla.firefox.vpn.ext.connectVpnIfPossible
import org.mozilla.firefox.vpn.util.NotificationUtil

class GuardianVpnService : WireGuardVpnService() {
    private val logTag = "GuardianVpnService"

    private val coroutineScope by lazy { CoroutineScope(Dispatchers.IO) + SupervisorJob() }

    private val component by lazy {
        (applicationContext as GuardianApp).guardianComponent
    }

    override val tunnelManager: TunnelManager<*> by lazy {
        component.tunnelManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val turnOn = {
            startForeground(NotificationUtil.DEFAULT_NOTIFICATION_ID, NotificationUtil.createBaseBuilder(this).build())
        }
        when (intent?.getStringExtra(EXTRA_COMMAND)) {
            COMMAND_TURN_OFF -> {
                Log.d(logTag, "Handling 'turn off' command")
                // Service being turned off manually by the user.
                // We don't stop the service here ourselves (e.g. stopSelf()); below we pass along
                // the intent over to wireguard's 'onStartCommand', which will turn itself off,
                // and notify any VPN state listeners.
                stopForeground(false)
            }
            COMMAND_TURN_ON -> {
                Log.d(logTag, "Handling 'turn on' command")
                // Service being started manually by the user.
                turnOn()
            }
            null -> {
                Log.d(logTag, "Handling system startup")
                // If Always-On mode is turned on, OS will start us without any extra flags.
                // Below we'll "properly" start ourselves, setting up a tunnel with a selected server, etc.
                // We'll end-up circling back to this method, since while it's turning on, wireguard's
                // TunnelManager ends up starting this service with a COMMAND_TURN_ON extra.
                coroutineScope.launch {
                    this@GuardianVpnService.connectVpnIfPossible(logTag)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        coroutineScope.cancel()
    }

    companion object {
        fun getPermissionIntent(context: Context): Intent? {
            return prepare(context)
        }
    }
}
