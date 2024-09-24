package com.kynetics.androidethernetexampleapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.UnknownHostException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val INTERNET_CONNECTIVITY_CHECK_INTERVAL = 5000L
        private const val READ_CONFIGURATION_CHECK_INTERVAL = 2000L
        private val TAG = MainActivity::class.java.simpleName
    }

    private lateinit var binding: ActivityMainBinding
    private val ethViewModel: EthViewModel by viewModels()
    private var backgroundScope = CoroutineScope(Dispatchers.IO)
    private var oneTimeRadioButtonSet = false
    private var internetConnectivityCheckJob: Deferred<Unit>? = null
    private var readConfigurationJob: Deferred<Unit>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupViewModel()
        initUI()
    }

    override fun onStart() {
        super.onStart()
        ethViewModel.onActivityStarted()
        backgroundScope = CoroutineScope(Dispatchers.IO)
        startReadingConfiguration()
        readConfiguration()
    }

    override fun onStop() {
        super.onStop()
        kotlin.runCatching {
            backgroundScope.cancel()
        }
    }

    private fun setupViewModel() {
        ethViewModel.ethConfiguration.observe(this) { ipConfiguration ->
            onConfigurationUpdated(ipConfiguration)
        }
        ethViewModel.ethStatus.observe(this) { connected ->
            binding.ethernetStatus.isChecked = connected
        }
    }

    private fun onConfigurationUpdated(ipConfiguration: IpConfiguration?) {
        binding.configTextView.visibility = View.VISIBLE
        if (ipConfiguration == null) {
            binding.configTextView.setText(R.string.error_no_configuration)
            binding.configResultTextView.visibility = View.GONE
            return
        }
        binding.configResultTextView.visibility = View.VISIBLE
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

        if (!binding.readAutoSwitch.isChecked) {
            updateStaticIpConfigurationFields(ipAddress, gateway, dnsServers)
        }

        if (!oneTimeRadioButtonSet) {
            if (ipConfiguration.ipAssignment == IpAssignment.STATIC) {
                updateStaticIpConfigurationFields(ipAddress, gateway, dnsServers)
                binding.staticRadioButton.isChecked = true
            } else if (ipConfiguration.ipAssignment == IpAssignment.DHCP) {
                binding.dhcpRadioButton.isChecked = true
                enableDisableTextForm(false)
            }
            oneTimeRadioButtonSet = true
        }
    }

    private fun updateStaticIpConfigurationFields(
        ipAddress: String?,
        gateway: String?, dnsServers: String?
    ) {
        binding.ipaddress.setText(ipAddress)
        binding.gateway.setText(gateway)
        binding.dns.setText(dnsServers)
    }

    private fun initUI() {
        setSupportActionBar(binding.toolbar)
        setupDevicesSectionViews()
        setupReadEthConfigurationSectionViews()
        setupSetEthConfigurationSectionViews()
    }

    private fun setupDevicesSectionViews() {
        setupInterfaceSpinner()
        setupInternetConnectivityStatus()
    }

    private fun setupInternetConnectivityStatus() {
        binding.internetStatusAutoSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startConnectivityStatusCheck()
            } else {
                kotlin.runCatching {
                    internetConnectivityCheckJob?.cancel()
                }
            }
            binding.internetStatus.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun startConnectivityStatusCheck() {
        kotlin.runCatching {
            internetConnectivityCheckJob = backgroundScope.async {
                while (true) {
                    val internetAvailable = ethViewModel.isInternetAvailable()
                    runOnUiThread {
                        binding.internetStatus.isChecked = internetAvailable
                    }
                    delay(INTERNET_CONNECTIVITY_CHECK_INTERVAL)
                }
            }
        }.onFailure {
            Log.e(TAG, "Error on Internet connectivity check", it)
        }
    }

    private fun setupInterfaceSpinner() {
        if (ethViewModel.availableInterfaces.isNullOrEmpty()) {
            binding.interfaceLayout.visibility = View.GONE
            binding.interfaceErrorMsg.visibility = View.VISIBLE
            binding.interfaceErrorMsg.setText(R.string.error_no_interfaces)
            return
        } else {
            binding.interfaceLayout.visibility = View.VISIBLE
            binding.interfaceErrorMsg.visibility = View.GONE

            ethViewModel.availableInterfaces?.let {
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item, it
                )

                binding.interfaceSpinner.adapter = adapter
                binding.interfaceSpinner.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?, view: View?,
                            position: Int, id: Long
                        ) {
                            val selectedEth = parent?.getItemAtPosition(position).toString()
                            ethViewModel.onSelectInterface(selectedEth)
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {
                            // Do nothing
                        }
                    }
            }
        }
    }

    private fun setupReadEthConfigurationSectionViews() {
        binding.readConfigButton.setOnClickListener {
            readConfiguration()
        }
        if (ethViewModel.availableInterfaces.isNullOrEmpty()) {
            binding.readConfigCard.visibility = View.GONE
        }
        binding.readAutoSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.readConfigButton.visibility = View.GONE
                startReadingConfiguration()
            } else {
                binding.readConfigButton.visibility = View.VISIBLE
                readConfigurationJob?.cancel()
            }
        }
        binding.readAutoSwitch.isChecked = true
    }

    private fun startReadingConfiguration() {
        if (readConfigurationJob?.isActive == true) {
            return
        }

        readConfigurationJob = backgroundScope.async {
            while (true) {
                readConfiguration()
                delay(READ_CONFIGURATION_CHECK_INTERVAL)
            }
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
            binding.setConfigCard.visibility = View.GONE
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
            }
        }
        enableDisableTextForm(true)
    }

    private fun enableDisableTextForm(enable: Boolean) {
        binding.gatewayLayout.setEnabled(enable)
        binding.dnsLayout.setEnabled(enable)
        binding.ipaddressLayout.setEnabled(enable)
        if (!enable) {
            clearTextForm()
            binding.staticForm.visibility = View.GONE
        } else {
            binding.staticForm.visibility = View.VISIBLE
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
                val doesGatewayHasErrors = ethViewModel.validateIp(gatewayInput)
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
                val doesDnsHasErrors = ethViewModel.validateIp(dnsItem)
                runOnUiThread { binding.dnsLayout.error = doesDnsHasErrors }
                if (doesDnsHasErrors == null) {
                    dnsList.add(InetAddress.getByName(dnsItem))
                }
            }
        } else {
            val doesDnsHasErrors = ethViewModel.validateIp(dnsText)
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
}