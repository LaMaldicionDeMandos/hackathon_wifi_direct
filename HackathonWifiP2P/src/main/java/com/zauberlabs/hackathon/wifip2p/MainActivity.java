package com.zauberlabs.hackathon.wifip2p;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends ListActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    private final static int PORT = 8988;
    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager.Channel channel;
    private WifiP2pManager manager;
    private final Receiver receiver = new Receiver();
    private final PeerListListener peerListener = new PeerListListener();
    private final List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    private final List<WifiP2pDevice> connected = new ArrayList<WifiP2pDevice>();

    private final Random rnd = new Random();
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

    class Receiver extends BroadcastReceiver implements WifiP2pManager.ConnectionInfoListener {

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
                NetworkInfo networkInfo = (NetworkInfo) intent
                        .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

                if (networkInfo.isConnected()) {

                    // We are connected with the other device, request connection
                    // info to find group owner IP

                    manager.requestConnectionInfo(channel, this);
                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                log("This device has change his state");
            }

        }

        @Override
        public void onConnectionInfoAvailable(final WifiP2pInfo info) {

            // InetAddress from WifiP2pInfo struct.
            InetAddress groupOwnerAddress = info.groupOwnerAddress;

            // After the group negotiation, we can determine the group owner.
            if (info.groupFormed && info.isGroupOwner) {
                // Do whatever tasks are specific to the group owner.
                // One common case is creating a server thread and accepting
                // incoming connections.
                try {
                    ServerSocket serverSocket = new ServerSocket(PORT);
                    log("Server: Socket opened");
                    final Socket client = serverSocket.accept();
                    log("Server: connection done");
                    InputStream in = client.getInputStream();
                    AsyncTask<InputStream, Void, Void> task = new AsyncTask<InputStream, Void, Void>() {
                        @Override
                        protected Void doInBackground(InputStream... params) {
                            InputStream in = params[0];
                            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                            while(true) {
                                try {
                                    String line = reader.readLine();
                                } catch (IOException e) {
                                    log("An error occurred");
                                    try {
                                        client.close();
                                    } catch (IOException e1) {
                                        log("An error occurred");
                                    }
                                    return null;
                                }
                            }
                        }
                    };
                    task.execute(in);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (info.groupFormed) {
                // The other device acts as the client. In this case,
                // you'll want to create a client thread that connects to the group
                // owner.
                String host = groupOwnerAddress.getHostAddress();

                log("Client: open socket at address: " + host);
                final Socket socket = new Socket();
                try {
                    socket.bind(null);
                    socket.connect((new InetSocketAddress(host, PORT)), 5000);
                    final OutputStream stream = socket.getOutputStream();
                    AsyncTask<OutputStream, Void, Void> task = new AsyncTask<OutputStream, Void, Void>() {
                        @Override
                        protected Void doInBackground(OutputStream... params) {
                            OutputStream out = params[0];
                            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
                            while(true) {
                                try {
                                    String data = Integer.valueOf(rnd.nextInt(1000)).toString();
                                    writer.write(data, 0, data.length());
                                    SystemClock.sleep(5000);
                                } catch (IOException e) {
                                    log("An error occurred");
                                    try {
                                        socket.close();
                                    } catch (IOException e1) {
                                        log("An error occurred");
                                    }
                                    return null;
                                }
                            }
                        }
                    };
                } catch (IOException e) {
                    log("An error occurred");
                }
            }
        }
    }
    
}
