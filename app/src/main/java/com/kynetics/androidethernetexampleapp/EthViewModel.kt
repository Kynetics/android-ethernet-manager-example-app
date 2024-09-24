/*
 * Copyright © 2023–2024  Kynetics, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.kynetics.androidethernetexampleapp

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kynetics.android.sdk.ethernet.EthernetManager
import com.kynetics.android.sdk.ethernet.EthernetManagerFactory.newInstance
import com.kynetics.android.sdk.ethernet.model.IpAssignment
import com.kynetics.android.sdk.ethernet.model.IpConfiguration
import com.kynetics.android.sdk.ethernet.model.StaticIpConfiguration
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket


class EthViewModel(application: Application) : AndroidViewModel(application) {
    private var ethernetManager: EthernetManager? = null
    private var weakContext = WeakReference(application)
    private val connectivityManager: ConnectivityManager by lazy {
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @JvmField
    var availableInterfaces: List<String>? = null
    private var _activeInterface = ""

    private val _currentConfiguration = MutableLiveData<IpConfiguration?>()
    private val _currentEthernetStatus = MutableLiveData<Boolean>()

    val ethConfiguration: LiveData<IpConfiguration?>
        get() = _currentConfiguration

    val ethStatus: LiveData<Boolean>
        get() = _currentEthernetStatus

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // network is available for use
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            _currentEthernetStatus.postValue(true)
        }

        // lost network connection
        override fun onLost(network: Network) {
            super.onLost(network)
            _currentEthernetStatus.postValue(false)
        }
    }

    init {
        application.runCatchingEthernetManagerException {
            ethernetManager = newInstance(application)
            availableInterfaces = ethernetManager?.availableInterfaces?.toList()
        }
    }

    fun onActivityStarted() {
        weakContext.get()?.runCatchingEthernetManagerException {
            if (!availableInterfaces.isNullOrEmpty()) {
                _activeInterface = availableInterfaces!!.first()
                readEthConfiguration(_activeInterface)
            }
        }
        observeEthernetStatus()
        checkConfigDirectory()
    }

    private fun checkConfigDirectory() {
        // This is a fix for Android 13, where the ethernet config directory is not created by the system
        // The fix is added in Android 14 :
        // https://cs.android.com/android/platform/superproject/+/android-14.0.0_r61:packages/modules/Connectivity/service-t/src/com/android/server/ethernet/EthernetConfigStore.java;l=119
        kotlin.runCatching {
            val configDir = File("/data/misc/apexdata/com.android.tethering/misc/ethernet/")
            if (!configDir.exists()) {
                val created = configDir.mkdirs()
                Log.d(TAG, "The AEPX directory was not present and is created? $created")
            }
        }.onFailure {
            Log.e(TAG, it.message!!)
        }
    }

    private fun observeEthernetStatus() {
        kotlin.runCatching {
            val networkRequest = NetworkRequest.Builder().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    addTransportType(NetworkCapabilities.TRANSPORT_USB)
                }
                addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            }.build()
            connectivityManager.requestNetwork(networkRequest, networkCallback)
        }.onFailure {
            Log.e(TAG, it.message!!)
        }
    }

    fun updateConfiguration(interfaceName: String?, ipConfiguration: IpConfiguration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ethernetManager?.setConfiguration(interfaceName!!, ipConfiguration)
        } else {
            ethernetManager?.setConfiguration(ipConfiguration)
        }
    }

    fun readEthConfiguration(iface: String) {
        var configuration: IpConfiguration?
        configuration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ethernetManager?.getConfiguration(iface)
        } else {
            ethernetManager?.getConfiguration()
        }
        if (configuration != null) {
            if (configuration.ipAssignment == IpAssignment.UNASSIGNED) {
                updateConfiguration(iface, IpConfiguration(IpAssignment.DHCP))
            }

            if (configuration.ipAssignment != IpAssignment.STATIC) {
                configuration = getEthernetConfiguration(iface, configuration)
            }
        }

        _currentConfiguration.postValue(configuration)
    }

    private fun getEthernetConfiguration(
        eth: String,
        configuration: IpConfiguration
    ): IpConfiguration {
        val ethNetwork =
            connectivityManager.allNetworks.map { connectivityManager.getLinkProperties(it) }
                .firstOrNull {
                    it != null && it.interfaceName == eth && availableInterfaces?.contains(eth) == true
                }

        val gateway = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ethNetwork?.dhcpServerAddress
        } else {
            ethNetwork?.routes?.filter { it.isDefaultRoute }?.map { it.gateway }
                ?.firstOrNull()
        }

        // If the gateway is not null, it means that the ethernet is connected in DHCP mode
        // regardless of the configuration returned by the system
        return if (gateway != null) {
            IpConfiguration(
                IpAssignment.DHCP,
                StaticIpConfiguration(
                    ethNetwork?.linkAddresses?.filter { validateIp(it.address.hostAddress) == null }
                        ?.map { "${it.address.hostAddress}/${it.prefixLength}" }?.firstOrNull()
                        ?: "N/A",
                    gateway,
                    ethNetwork?.dnsServers?.filterIsInstance<Inet4Address>() ?: emptyList(),
                ),
            )
        } else {
            configuration
        }
    }

    fun isInternetAvailable(): Boolean {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("www.google.com", 80), 2000)
                return true
            }
        } catch (e: IOException) {
            // Either we have a timeout or unreachable host or failed DNS lookup
            return false
        }
    }

    @Throws(IllegalArgumentException::class)
    fun validateIpAddress(ip: String?) {
        ethernetManager!!.validateIpAndMask(ip!!)
    }

    fun onSelectInterface(interfaceName: String) {
        _activeInterface = interfaceName
        readEthConfiguration(interfaceName)
    }

    fun validateIp(ip: String?): String? {
        try {
            ethernetManager!!.validateIp(ip!!)
        } catch (e: IllegalArgumentException) {
            return e.message
        }
        return null
    }

    private fun Application.runCatchingEthernetManagerException(block: Application.() -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            val error = getString(R.string.error_hidden_api_access)
            Log.d(TAG, error)
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            Log.e(TAG, e.message!!)
        }
    }

    companion object {
        private const val TAG = "KyneticsAndroidEthernetExampleApp_AndroidViewModel"
    }
}
