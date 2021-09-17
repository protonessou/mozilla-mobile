package org.mozilla.firefox.vpn.main.vpn

import org.mozilla.firefox.vpn.CoreComponent
import org.mozilla.firefox.vpn.GuardianComponent

interface VpnComponent {
    val viewModel: VpnViewModel
}

class VpnComponentImpl(
    private val coreComponent: CoreComponent,
    private val guardianComponent: GuardianComponent
) : VpnComponent, GuardianComponent by guardianComponent, CoreComponent by coreComponent {

    override val viewModel: VpnViewModel
        get() = VpnViewModel(
            vpnManager = VpnManager(app, prefs),
            userRepository = userRepo,
            deviceRepository = deviceRepo,
            serverRepository = serverRepo
        )
}
