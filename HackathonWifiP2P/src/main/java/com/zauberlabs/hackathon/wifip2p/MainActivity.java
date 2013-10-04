package com.zauberlabs.hackathon.wifip2p;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends ListActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager.Channel channel;
    private WifiP2pManager manager;
    private final Receiver receiver = new Receiver();
    private final PeerListListener peerListener = new PeerListListener();
    private final List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    private final List<WifiP2pDevice> connected = new ArrayList<WifiP2pDevice>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupList();

        //  Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private void setupList() {
        ListAdapter adapter = new ArrayAdapter<WifiP2pDevice>(
                this,
                android.R.layout.simple_list_item_1,
                connected);

        // Bind to our new adapter.
        setListAdapter(adapter);

    }

    public void onClick(View view) {
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                log("Success Discovering");
            }

            @Override
            public void onFailure(int reason) {
                log("Fail Discovering");
            }
        });
    }

    private  void requestPeers() {
        if (manager == null) return;

        manager.requestPeers(channel, peerListener);
    }

    private void log(String text) {
        Log.d(TAG, text);
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    class PeerListListener implements WifiP2pManager.PeerListListener {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            // Out with the old, in with the new.
            peers.clear();
            peers.addAll(peerList.getDeviceList());

            if (peers.size() == 0) {
                log("No device found");
            } else {
                log(Integer.valueOf(peers.size()).toString() + "devices found");
                log(peers.toString());
                for (WifiP2pDevice device : peers) {
                    if (!connected.contains(device)) {
                        connect(device);
                    }
                }
            }

        }
    }

    private void connect(final WifiP2pDevice device) {
        log("connecting to device " + device.deviceName);
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                log("Connected to device " + device);
                connected.add(device);
                ((ArrayAdapter)getListAdapter()).notifyDataSetChanged();
            }

            @Override
            public void onFailure(int reason) {
                log("Conection fail: device " + device + " reasen " + reason);
            }
        });

    }

    class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                boolean enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                log("The Wifi P2P mode is enabled: " + enabled);
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                log("List of peers has changed");
                requestPeers();

            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                log("Connection state changed");

            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                log("This device has change his state");
            }

        }
    }
    
}
