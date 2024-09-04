package com.kynetics.androidethernetexampleapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kynetics.android.sdk.ethernet.model.IpAssignment
import com.kynetics.android.sdk.ethernet.model.IpConfiguration
import com.kynetics.android.sdk.ethernet.model.ProxySettings
import com.kynetics.android.sdk.ethernet.model.StaticIpConfiguration
import com.kynetics.androidethernetexampleapp.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Timer
import kotlin.concurrent.timer

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ethViewModel: EthViewModel by viewModels()
    private val backgroundScope = CoroutineScope(Dispatchers.IO)
    private var timer : Timer? = null
    private var firstStart = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupViewModel()
        initUI()

    }

    private fun setupViewModel() {
        ethViewModel.ethConfiguration.observe(this) { ipConfiguration ->
            onConfigurationUpdated(ipConfiguration)
        }
    }

    private fun onConfigurationUpdated(ipConfiguration: IpConfiguration?) {
        binding.configTextView.visibility = android.view.View.VISIBLE
        if (ipConfiguration == null) {
            binding.configTextView.setText(R.string.error_no_configuration)
            binding.configResultTextView.visibility = android.view.View.GONE
            return
        }
        binding.configResultTextView.visibility = android.view.View.VISIBLE
        val gateway = ipConfiguration.staticIpConfiguration?.gateway?.hostAddress
        val dnsServers = ipConfiguration.staticIpConfiguration?.dnsServers?.joinToString(", ") {
            it.hostAddress as CharSequence
        }
        val ipAddress = ipConfiguration.staticIpConfiguration?.ipAddress
        val configurationText = """
            ${ipConfiguration.ipAssignment}
            $ipAddress
            $gateway
            $dnsServers
        """.trimIndent()
        binding.configTextView.text = configurationText
        if (ipConfiguration.ipAssignment == IpAssignment.STATIC) {
            binding.ipaddress.setText(ipAddress)
            binding.gateway.setText(gateway)
            binding.dns.setText(dnsServers)
        }
    }

    override fun onStart() {
        super.onStart()
        ethViewModel.onActivityStarted()
        firstStart = false
    }

    private fun initUI() {
        setSupportActionBar(binding.toolbar)
        setupDevicesSectionViews()
        setupReadEthConfigurationSectionViews()
        setupSetEthConfigurationSectionViews()
    }

    private fun setupDevicesSectionViews() {
        setupInterfaceSpinner()
        setupInternetConnectivityViews()
        binding.interfaceSpinner
    }

    private fun setupInternetConnectivityViews() {
        binding.internetSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.internetStatus.visibility = android.view.View.VISIBLE
                timer = timer(name = "InternetConnectivityTimer",
                    initialDelay = 0,
                    period = 2000) {
                    checkInternetConnectivityStatus()
                }
            } else {
                binding.internetStatus.visibility = android.view.View.GONE
                timer?.purge()
                timer?.cancel()
            }
        }
    }

    private fun checkInternetConnectivityStatus() {
        backgroundScope.launch {
            val internetAvailable = ethViewModel.isInternetAvailable()
            runOnUiThread {
                binding.internetStatus.visibility = android.view.View.VISIBLE
                val colorId = if (internetAvailable) {
                    binding.internetStatus.setText(R.string.internet_connection_status_active)
                    R.color.green_700
                } else {
                    binding.internetStatus.setText(R.string.internet_connection_status_inactive)
                    R.color.error_color
                }
                val color = resources.getColor(colorId, theme)
                binding.internetStatus.setTextColor(color)
            }
        }
    }

    private fun setupInterfaceSpinner() {
        if (ethViewModel.availableInterfaces.isNullOrEmpty()) {
            binding.interfaceLayout.visibility = android.view.View.GONE
            binding.interfaceErrorMsg.visibility = android.view.View.VISIBLE
            binding.interfaceErrorMsg.setText(R.string.error_no_interfaces)
            return
        } else {
            binding.interfaceLayout.visibility = android.view.View.VISIBLE
            binding.interfaceErrorMsg.visibility = android.view.View.GONE

            ethViewModel.availableInterfaces?.let {
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item, it
                )
                binding.interfaceSpinner.adapter = adapter
            }
        }
    }

    private fun setupReadEthConfigurationSectionViews() {
        binding.readConfigButton.setOnClickListener {
            readConfiguration()
        }
        if (ethViewModel.availableInterfaces.isNullOrEmpty()) {
            binding.readConfigCard.visibility = android.view.View.GONE
        }
    }

    private fun readConfiguration() {
        val selectedEth = binding.interfaceSpinner.selectedItem?.toString()
        selectedEth?.let {
            ethViewModel.readEthConfiguration(it)
        }
    }

    private fun setupSetEthConfigurationSectionViews() {
        setupEthAssignmentRadioGroup()
        setupUpdateConfigButton()
        if (ethViewModel.availableInterfaces.isNullOrEmpty()) {
            binding.setConfigCard.visibility = android.view.View.GONE
        }
    }

    private fun setupUpdateConfigButton() {
        binding.updateConfigButton.setOnClickListener {
            backgroundScope.launch {
                setConfiguration()
            }
        }
    }

    private fun setupEthAssignmentRadioGroup() {
        binding.radioGroup4.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.dhcpRadioButton) {
                enableDisableTextForm(false)
            } else if (checkedId == R.id.staticRadioButton) {
                enableDisableTextForm(true)
                if (!firstStart && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    binding.scrollView.scrollToDescendant(binding.updateConfigButton)
                    firstStart =false
                }
            }
        }
        binding.radioGroup4.check(R.id.staticRadioButton)
        enableDisableTextForm(true)
    }

    private fun enableDisableTextForm(enable: Boolean) {
        binding.gatewayLayout.setEnabled(enable)
        binding.dnsLayout.setEnabled(enable)
        binding.ipaddressLayout.setEnabled(enable)
        if (!enable) {
            clearTextForm()
            binding.staticForm.visibility = android.view.View.GONE
        } else {
            binding.staticForm.visibility = android.view.View.VISIBLE
        }
    }

    private fun clearTextForm() {
        binding.gateway.setText("")
        binding.dns.setText("")
        binding.ipaddress.setText("")
    }

    private fun setConfiguration() {
        try {
            val selectedInterfaceName = binding.interfaceSpinner.getSelectedItem().toString()
            if (binding.staticRadioButton.isChecked) {
                var fieldsPresent: Boolean = validateEditTextEmptiness(binding.ipaddressLayout)
                fieldsPresent = fieldsPresent and validateEditTextEmptiness(binding.gatewayLayout)
                fieldsPresent = fieldsPresent and validateEditTextEmptiness(binding.dnsLayout)
                if (!fieldsPresent) {
                    return
                }

                var invalidIp = false
                val ipWithNetmask: String = binding.ipaddress.getText().toString()
                try {
                    ethViewModel.validateIpAddress(ipWithNetmask)
                    runOnUiThread { binding.ipaddressLayout.error = null }
                } catch (e: IllegalArgumentException) {
                    runOnUiThread { binding.ipaddressLayout.error = e.message }
                    invalidIp = true
                }

                val dnsText: String = binding.dns.getText().toString()
                val dnsList: List<InetAddress> = validateDnsString(dnsText)

                val gatewayInput: String = binding.gateway.getText().toString()
                val doesGatewayHasErrors = ethViewModel.isIpValid(gatewayInput)
                runOnUiThread { binding.gatewayLayout.error = doesGatewayHasErrors }

                if (doesGatewayHasErrors != null || dnsList.isEmpty() || invalidIp) {
                    return
                }
                ethViewModel.updateConfiguration(
                    selectedInterfaceName,
                    IpConfiguration(
                        IpAssignment.STATIC,
                        StaticIpConfiguration(
                            ipWithNetmask, InetAddress.getByName(gatewayInput),
                            dnsList, null
                        ),
                        ProxySettings.UNASSIGNED,
                        null
                    )
                )
            } else {
                ethViewModel.updateConfiguration(
                    selectedInterfaceName,
                    IpConfiguration(
                        IpAssignment.DHCP, null,
                        ProxySettings.UNASSIGNED, null
                    )
                )
            }
            showToast("Configuration set successfully")
            readConfiguration()
        } catch (e: Exception) {
            showToast("Error on setting configuration: " + e.message)
            Log.w(TAG, "Error on setting configuration", e)
        }
    }

    @Throws(java.lang.IllegalArgumentException::class, UnknownHostException::class)
    fun validateDnsString(dnsText: String): List<InetAddress> {
        val dnsList: MutableList<InetAddress> = mutableListOf()
        if (dnsText.contains(",")) {
            for (dnsItem in dnsText.split(",")
                .dropLastWhile { it.isEmpty() }) {
                val doesDnsHasErrors = ethViewModel.isIpValid(dnsItem)
                runOnUiThread { binding.dnsLayout.error = doesDnsHasErrors }
                if (doesDnsHasErrors == null) {
                    dnsList.add(InetAddress.getByName(dnsItem))
                }
            }
        } else {
            val doesDnsHasErrors = ethViewModel.isIpValid(dnsText)
            runOnUiThread { binding.dnsLayout.error = doesDnsHasErrors }
            if (doesDnsHasErrors == null) {
                dnsList.add(InetAddress.getByName(dnsText))
            }
        }
        return dnsList
    }

    private fun validateEditTextEmptiness(textInputLayout: TextInputLayout): Boolean {
        val editText = textInputLayout.editText as TextInputEditText
        if (editText.text.toString().isEmpty()) {
            runOnUiThread { textInputLayout.error = "This field cannot be empty" }
            return false
        } else {
            runOnUiThread { textInputLayout.error = null }
        }
        return true
    }

    private fun showToast(message: String?) {
        runOnUiThread {
            Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_about) {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}