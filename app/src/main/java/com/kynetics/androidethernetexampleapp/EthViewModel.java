package com.kynetics.androidethernetexampleapp;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.kynetics.ethernetmanager.SystemEthernetManager;
import com.kynetics.ethernetmanager.model.IpConfiguration;

import java.util.Arrays;
import java.util.List;

public class EthViewModel extends AndroidViewModel {

    private final SystemEthernetManager ethernetManager;

    List<String> availableInterfaces;

    final private MutableLiveData<IpConfiguration> currentConfiguration = new MutableLiveData<>(new IpConfiguration());

    public EthViewModel(@NonNull Application application) {
        super(application);
        ethernetManager = SystemEthernetManager.newInstance(application);
        availableInterfaces = Arrays.asList(ethernetManager.getAvailableInterfaces());

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
}
