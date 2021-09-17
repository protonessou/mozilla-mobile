package org.mozilla.firefox.vpn.main.vpn

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.model.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import kotlinx.android.synthetic.main.bottom_sheet_servers.*
import kotlinx.android.synthetic.main.fragment_vpn.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.firefox.vpn.R
import org.mozilla.firefox.vpn.coreComponent
import org.mozilla.firefox.vpn.device.data.DeviceRepository
import org.mozilla.firefox.vpn.guardianComponent
import org.mozilla.firefox.vpn.servers.domain.GetServersUseCase
import org.mozilla.firefox.vpn.util.Result
import org.mozilla.firefox.vpn.util.viewModel
import java.net.InetAddress

class VpnFragment : Fragment() {

    private lateinit var behavior: BottomSheetBehavior<View>
    private var pendingTunnel: Tunnel? = null
    private var config: Config? = null

    private val deviceRepository: DeviceRepository by lazy {
        activity!!.guardianComponent.deviceRepo
    }

    private val userRepository by lazy {
        activity!!.guardianComponent.userRepo
    }

    private val serverRepository by lazy {
        activity!!.guardianComponent.serverRepo
    }

    private val getServerList: GetServersUseCase by lazy {
        GetServersUseCase(userRepository, serverRepository)
    }

    private val vpnManager by lazy {
        val appContext = activity!!.applicationContext
        VpnManager(
            appContext,
            PreferenceManager.getDefaultSharedPreferences(appContext)
        )
    }

    private val component by lazy {
        VpnComponentImpl(context!!.coreComponent, context!!.guardianComponent)
    }

    private val vpnViewModel by viewModel { component.viewModel }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        config = prepareConfig()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_vpn, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vpn_switch.setOnCheckedChangeListener { _, isChecked ->
            GlobalScope.launch(Dispatchers.IO) {
                if (isChecked) {
                    val intent = GoBackend.VpnService.prepare(context)
                    if (intent != null) {
                        withContext(Dispatchers.Main){
                            vpn_switch.isChecked = false

                            startActivityForResult(intent, 0)
                        }
                    } else {
                        switchState(Tunnel.State.UP)
                        vpnManager.connect("aaa", config!!)
                    }
                } else {
                    switchState(Tunnel.State.DOWN)
                    vpnManager.disconnect()
                }
            }
        }
        vpn_switch.isChecked = vpnManager.isConnected()

        behavior = BottomSheetBehavior.from(bottom_sheet)

        vpn_server_switch.setOnClickListener {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        btn_cancel.setOnClickListener {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        getServers()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            vpn_switch.isChecked = true
        } else {
            Toast.makeText(context, "Permission denied by user", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun switchState(state: Tunnel.State) {
        withContext(Dispatchers.Main){
            vpn_state_offline.visibility = View.GONE
            vpn_state_connecting.visibility = View.GONE
            vpn_state_online.visibility = View.GONE
            vpn_state_switching.visibility = View.GONE
            when (state) {
                Tunnel.State.UP -> {
                    vpn_state_online.visibility = View.VISIBLE
                }
                Tunnel.State.DOWN -> {
                    vpn_state_offline.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun prepareConfig(): Config? {
        val currentDevice = deviceRepository.getDevice() ?: return null
        val device = currentDevice.device
        val privateKey = currentDevice.privateKeyBase64

        val inetInterface = Interface.Builder().apply {
            val ipv4Address = device.ipv4Address
            Log.d(TAG, "device=$device")
            Log.d(TAG, "private key=$privateKey")

            setKeyPair(KeyPair(Key.fromBase64(privateKey)))
            addAddress(InetNetwork.parse(ipv4Address))
            addDnsServer(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)))
        }.build()

        return Config.Builder().apply {
            setInterface(inetInterface)

            val peers = ArrayList<Peer>(1)
            peers.add(Peer.Builder().apply {
                setPublicKey(Key.fromBase64("Wy2FhqDJcZU03O/D9IUG/U5BL0PLbF06nvsfgIwrmGk="))
                parseEndpoint("185.232.22.58:32768")
                setPersistentKeepalive(60)
                parseAllowedIPs("0.0.0.0/0")
            }.build())
            peers.add(Peer.Builder().apply {
                setPublicKey(Key.fromBase64("Rzh64qPcg8W8klJq0H4EZdVCH7iaPuQ9falc99GTgRA="))
                parseEndpoint("103.231.88.2:32768")
                setPersistentKeepalive(60)
                parseAllowedIPs("0.0.0.0/0")
            }.build())

            addPeers(peers)
        }.build()
    }

    private fun getServers() {
        // TODO: 1. Do not use GlobalScope
        // TODO: 2. Performance tuning
        GlobalScope.launch(Dispatchers.Main) {
            val result = withContext(Dispatchers.IO) {
                getServerList(GetServersUseCase.FilterStrategy.ByCountry)
            }
            if (result is Result.Success) {
                val adapter = ServerListAdapter(result.value.countries)
                server_list?.adapter = adapter
                city_name?.text = result.value.countries[0].name
            }
        }
    }

    companion object {
        private const val TAG = "VpnFragment"
    }
}