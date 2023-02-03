package com.kynetics.androidethernetexampleapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.kynetics.ethernetmanager.model.IpConfiguration;
import com.kynetics.ethernetmanager.model.StaticIpConfiguration;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private EthViewModel model;

    private TextInputEditText gateway;
    private TextInputEditText dns;
    private TextInputEditText ipaddress;

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
        model = new ViewModelProvider(this).get(EthViewModel.class);

        gateway = findViewById(R.id.gatewayText);
        dns = findViewById(R.id.dnsText);
        ipaddress = findViewById(R.id.ipaddressText);
        actionButton = findViewById(R.id.actionButton);
        dhcRadio = findViewById(R.id.dhcpRadioButton);
        staticRadio = findViewById(R.id.staticRadioButton);
        interfacesSpinner = findViewById(R.id.interfaceSpinner);
        modes = findViewById(R.id.modeSpinner);

        final List<String> modeItems = new ArrayList<>(2);
        modeItems.add("GET");
        modeItems.add("SET");
        final ArrayAdapter<String> modesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modeItems);
        final ArrayAdapter<String> interfacesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, model.availableInterfaces);

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


        model.getEthConfiguration().observe(this, this::showConfiguration);
    }

    final View.OnClickListener setAction = v -> {
        try {
            if (dhcRadio.isChecked()) {
                model.updateConfiguration(
                        interfacesSpinner.getSelectedItem().toString(),
                        new IpConfiguration(IpConfiguration.IpAssignment.DHCP, null, IpConfiguration.ProxySettings.UNASSIGNED, null));
            } else if (staticRadio.isChecked()) {
                final List<InetAddress> dnsList = new ArrayList();
                dnsList.add(InetAddress.getByName(dns.getText().toString()));
                model.updateConfiguration(
                        interfacesSpinner.getSelectedItem().toString(),
                        new IpConfiguration(
                                IpConfiguration.IpAssignment.STATIC,
                                new StaticIpConfiguration(
                                        ipaddress.getText().toString(),
                                        InetAddress.getByName(gateway.getText().toString()),
                                            dnsList,
                                        null
                                        ),
                                IpConfiguration.ProxySettings.UNASSIGNED,
                                null
                        )
                );
            }
        }catch (Exception e){
            Toast.makeText(this, "Error on setting configuration: " +e.getMessage(), Toast.LENGTH_LONG).show();
            Log.w(TAG, "Error on setting configuration", e);
        }
    };

    final View.OnClickListener getAction = v -> {
        model.readEthConfiguration(interfacesSpinner.getSelectedItem().toString());
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
//        gateway.setFocusable(isSet);
//        dns.setFocusable(isSet);
//        ipaddress.setFocusable(isSet);
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
        final boolean isDHCP = ipConfiguration.getIpAssignment() == IpConfiguration.IpAssignment.DHCP;
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

    private static final String TAG = MainActivity.class.getSimpleName();
}