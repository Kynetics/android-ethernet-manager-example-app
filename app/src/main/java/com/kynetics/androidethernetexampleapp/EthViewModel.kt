/*
 * Copyright © 2023–2024  Kynetics, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.kynetics.androidethernetexampleapp

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kynetics.android.sdk.ethernet.EthernetManager
import com.kynetics.android.sdk.ethernet.EthernetManagerFactory.newInstance
import com.kynetics.android.sdk.ethernet.model.IpAssignment
import com.kynetics.android.sdk.ethernet.model.IpConfiguration
import com.kynetics.android.sdk.ethernet.model.StaticIpConfiguration
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket


class EthViewModel(application: Application) : AndroidViewModel(application) {
    private var ethernetManager: EthernetManager? = null
    private var weakContext = WeakReference(application)

    @JvmField
    var availableInterfaces: List<String>? = null

    private val currentConfiguration = MutableLiveData<IpConfiguration?>()

    fun onActivityStarted() {
        weakContext.get()?.runCatchingEthernetManagerException {
            if (!availableInterfaces.isNullOrEmpty()) {
                readEthConfiguration(availableInterfaces!!.first())
            }
        }
    }

    init {
        application.runCatchingEthernetManagerException {
            ethernetManager = newInstance(application)
            availableInterfaces = ethernetManager?.availableInterfaces?.toList()
        }
    }

    private fun Application.runCatchingEthernetManagerException(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            val error = getString(R.string.error_hidden_api_access)
            Log.d(TAG, error)
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            Log.e(TAG, e.message!!)
        }
    }

    fun updateConfiguration(interfaceName: String?, ipConfiguration: IpConfiguration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ethernetManager!!.setConfiguration(interfaceName!!, ipConfiguration)
        } else {
            ethernetManager!!.setConfiguration(ipConfiguration)
        }
        currentConfiguration.postValue(ipConfiguration)
    }

    fun readEthConfiguration(iface: String) {
        var configuration: IpConfiguration?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            configuration = ethernetManager?.getConfiguration(iface)
        } else {
            configuration = ethernetManager?.getConfiguration()
        }
        if (configuration != null && configuration.ipAssignment == IpAssignment.DHCP &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            configuration = getEthernetConfiguration(iface)
        }
        currentConfiguration.postValue(configuration)
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

    @RequiresApi(Build.VERSION_CODES.R)
    fun getEthernetConfiguration(eth: String): IpConfiguration? {
        return weakContext.get()?.let { ctx ->
            val manager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val ethNetwork = manager.allNetworks.map { manager.getLinkProperties(it) }.firstOrNull {
                it != null && it.interfaceName == eth &&
                        availableInterfaces?.contains(eth) == true
            } ?: return null

            return IpConfiguration(
                IpAssignment.DHCP,
                StaticIpConfiguration(
                    ethNetwork.linkAddresses.filter { isIpValid(it.address.hostAddress) == null }
                        .map { "${it.address.hostAddress}/${it.prefixLength}" }.firstOrNull(),
                    ethNetwork.dhcpServerAddress,
                    ethNetwork.dnsServers.filterIsInstance<Inet4Address>()
                ),
            )
        }
    }

    val ethConfiguration: LiveData<IpConfiguration?>
        get() = currentConfiguration

    @Throws(IllegalArgumentException::class)
    fun validateIpAddress(ip: String?) {
        ethernetManager!!.validateIpAndMask(ip!!)
    }

    fun isIpValid(ip: String?): String? {
        try {
            ethernetManager!!.validateIp(ip!!)
        } catch (e: IllegalArgumentException) {
            return e.message
        }
        return null
    }

    companion object {
        private const val TAG = "KyneticsAndroidEthernetExampleApp_AndroidViewModel"
    }
}
