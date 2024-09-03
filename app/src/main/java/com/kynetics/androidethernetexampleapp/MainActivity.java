/*
 * Copyright © 2023–2024  Kynetics, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kynetics.androidethernetexampleapp;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.kynetics.android.sdk.ethernet.model.IpAssignment;
import com.kynetics.android.sdk.ethernet.model.IpConfiguration;
import com.kynetics.android.sdk.ethernet.model.ProxySettings;
import com.kynetics.android.sdk.ethernet.model.StaticIpConfiguration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private EthViewModel ethViewModel;

    private TextInputEditText gateway;
    private TextInputEditText dns;
    private TextInputEditText ipaddress;
    private TextInputLayout gatewayLayout;
    private TextInputLayout dnsLayout;
    private TextInputLayout ipaddressLayout;

    private Button actionButton;
    private RadioButton dhcRadio;
    private RadioButton staticRadio;
    private Spinner interfacesSpinner;
    private Spinner modes;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();
    }

    private void initUI(){
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ethViewModel = new ViewModelProvider(this).get(EthViewModel.class);

        gateway = findViewById(R.id.gatewayText);
        dns = findViewById(R.id.dnsText);
        ipaddress = findViewById(R.id.ipaddressText);
        gatewayLayout  = findViewById(R.id.gateway);
        dnsLayout = findViewById(R.id.dns);
        ipaddressLayout = findViewById(R.id.ipaddress);
        actionButton = findViewById(R.id.actionButton);
        dhcRadio = findViewById(R.id.dhcpRadioButton);
        staticRadio = findViewById(R.id.staticRadioButton);
        interfacesSpinner = findViewById(R.id.interfaceSpinner);
        modes = findViewById(R.id.modeSpinner);

        final List<String> modeItems = new ArrayList<>(2);
        modeItems.add("GET");
        modeItems.add("SET");
        final ArrayAdapter<String> modesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modeItems);
        final ArrayAdapter<String> interfacesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ethViewModel.availableInterfaces);

        modes.setAdapter(modesAdapter);
        interfacesSpinner.setAdapter(interfacesAdapter);

        dhcRadio.setOnCheckedChangeListener((buttonView, isChecked) -> enableDisableTextForm(!isChecked));
        staticRadio.setOnCheckedChangeListener((buttonView, isChecked) -> enableDisableTextForm(isChecked));

        modes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(position == 0){
                    initSetGet(false);
                } else {
                    initSetGet(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });


        ethViewModel.getEthConfiguration().observe(this, this::showConfiguration);
    }

    final View.OnClickListener setAction = v -> {
        // Start a new thread to avoid blocking the UI
        Thread thread = new Thread(this::setConfiguration);
        thread.start();
    };

    private void setConfiguration() {
        try {
            if (dhcRadio.isChecked()) {
                ethViewModel.updateConfiguration(
                        interfacesSpinner.getSelectedItem().toString(),
                        new IpConfiguration(IpAssignment.DHCP, null, ProxySettings.UNASSIGNED, null));
            } else if (staticRadio.isChecked()) {
                boolean fieldsPresent = validateEditTextEmptiness(ipaddressLayout);
                fieldsPresent &= validateEditTextEmptiness(gatewayLayout);
                fieldsPresent &= validateEditTextEmptiness(dnsLayout);
                if (!fieldsPresent) {
                    return;
                }

                String ipWithNetmask = ipaddress.getText().toString();
                try {
                    ethViewModel.validateIpAddress(ipWithNetmask);
                } catch (IllegalArgumentException e) {
                    showToast(e.getMessage());
                    return;
                }

                final String dnsText = dns.getText().toString();
                final List<InetAddress> dnsList = validateDnsString(this, dnsText);
                if (dnsList.isEmpty()) {
                    return;
                }

                String gatewayInput = gateway.getText().toString();
                if (!ethViewModel.isIpValid(this, gatewayInput)) {
                    return;
                }

                ethViewModel.updateConfiguration(
                        interfacesSpinner.getSelectedItem().toString(),
                        new IpConfiguration(
                                IpAssignment.STATIC,
                                new StaticIpConfiguration(
                                        ipWithNetmask, InetAddress.getByName(gatewayInput),
                                        dnsList, null),
                                ProxySettings.UNASSIGNED,
                                null
                        )
                );
            }
            showToast("Configuration set successfully");
        } catch (Exception e){
            showToast("Error on setting configuration: " +e.getMessage());
            Log.w(TAG, "Error on setting configuration", e);
        }
    }

    public List<InetAddress> validateDnsString(MainActivity activity, String dnsText) throws IllegalArgumentException, UnknownHostException {
        final List<InetAddress> dnsList = new ArrayList();
        if (dnsText.contains(",")) {
            for (String dnsItem : dnsText.split(",")) {
                if (ethViewModel.isIpValid(activity, dnsItem)) {
                    dnsList.add(InetAddress.getByName(dnsItem));
                }
            }
        } else {
            if (ethViewModel.isIpValid(activity, dnsText)) {
                dnsList.add(InetAddress.getByName(dnsText));
            }
        }
        return dnsList;
    }

    void showToast(String message){
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private Boolean validateEditTextEmptiness(TextInputLayout textInputLayout) {
        TextInputEditText editText = (TextInputEditText) textInputLayout.getEditText();
        if (editText.getText().toString().isEmpty()) {
            runOnUiThread(() -> textInputLayout.setError("This field cannot be empty"));
            return false;
        } else {
            runOnUiThread(() -> textInputLayout.setError(null));
        }
        return true;
    }

    final View.OnClickListener getAction = v -> {
        ethViewModel.readEthConfiguration(interfacesSpinner.getSelectedItem().toString());
    };

    private void enableDisableTextForm(boolean enable){
        gateway.setEnabled(enable);
        dns.setEnabled(enable);
        ipaddress.setEnabled(enable);
        clearTextForm();
    }

    private void clearTextForm(){
        gateway.setText("");
        dns.setText("");
        ipaddress.setText("");
    }
    private void initSetGet(boolean isSet){
        clearTextForm();
        gateway.setEnabled(isSet);
        dns.setEnabled(isSet);
        ipaddress.setEnabled(isSet);
        dhcRadio.setEnabled(isSet);
        staticRadio.setEnabled(isSet);
        if(isSet){
            actionButton.setOnClickListener(setAction);
            actionButton.setText("SET");
        } else {
            actionButton.setOnClickListener(getAction);
            actionButton.setText("GET");

        }
    }
    private void showConfiguration(IpConfiguration ipConfiguration){
        final boolean isDHCP = ipConfiguration.getIpAssignment() == IpAssignment.DHCP;
        dhcRadio.setChecked(isDHCP);
        staticRadio.setChecked(!isDHCP);
        final StaticIpConfiguration staticIpConfiguration = ipConfiguration.getStaticIpConfiguration();
        if(staticIpConfiguration != null) {
            gateway.setText(staticIpConfiguration.getGateway().getHostAddress());
            ipaddress.setText(staticIpConfiguration.getIpAddress());
            dns.setText(
                    staticIpConfiguration.getDnsServers().stream().map(InetAddress::getHostAddress).collect(Collectors.joining(", "))
            );
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_about) {
            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static final String TAG = MainActivity.class.getSimpleName();
}