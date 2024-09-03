/*
 * Copyright © 2023–2024  Kynetics, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kynetics.androidethernetexampleapp;

import android.app.Application;
import android.util.Log;

import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.kynetics.android.sdk.ethernet.EthernetManager;
import com.kynetics.android.sdk.ethernet.EthernetManagerFactory;
import com.kynetics.android.sdk.ethernet.model.IpConfiguration;

import java.util.Arrays;
import java.util.List;

public class EthViewModel extends AndroidViewModel {

    private EthernetManager ethernetManager;
    private static final String TAG = "KyneticsAndroidEthernetExampleApp_AndroidViewModel";

    List<String> availableInterfaces;

    final private MutableLiveData<IpConfiguration> currentConfiguration = new MutableLiveData<>(new IpConfiguration());

    public EthViewModel(@NonNull Application application) {
        super(application);
        try {
            ethernetManager = EthernetManagerFactory.newInstance(application);
            availableInterfaces = Arrays.asList(ethernetManager.getAvailableInterfaces());
        } catch (Exception e) {
            String error = application.getString(R.string.error_hidden_api_access);
            Log.d(TAG, error);
            Toast.makeText(application, error, Toast.LENGTH_LONG).show();
            Log.e(TAG, e.getMessage());
        }
    }

    public void updateConfiguration(String interfaceName, IpConfiguration ipConfiguration){
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P){
            ethernetManager.setConfiguration(interfaceName, ipConfiguration);
        } else {
            ethernetManager.setConfiguration(ipConfiguration);
        }
        currentConfiguration.postValue(ipConfiguration);
    }

    public void readEthConfiguration(String iface){
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P){
            currentConfiguration.postValue(ethernetManager.getConfiguration(iface));
        } else {
            currentConfiguration.postValue(ethernetManager.getConfiguration());
        }
    }

    public LiveData<IpConfiguration> getEthConfiguration(){
        return currentConfiguration;
    }

    public void validateIpAddress(String ip) throws IllegalArgumentException {
        ethernetManager.validateIpAndMask(ip);
    }

    Boolean isIpValid(MainActivity activity, String ip) {
        try {
            ethernetManager.validateIp(ip);
        } catch (IllegalArgumentException e) {
            activity.showToast(e.getMessage());
            return false;
        }
        return true;
    }
}
