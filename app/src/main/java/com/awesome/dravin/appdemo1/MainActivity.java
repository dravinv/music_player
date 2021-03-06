package com.awesome.dravin.appdemo1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

//working
public class MainActivity extends AppCompatActivity implements WifiP2pManager.ChannelListener, DeviceListFragment.DeviceActionListener{
    //Declaring Objects
    private WifiP2pManager manager;
    private  boolean isWifiP2pEnabled=false;
    private WifiP2pManager.Channel channel;
    private boolean retryChannel=false;
    private final IntentFilter intentFilter=new IntentFilter();
    private BroadcastReceiver receiver=null;

    Uri u;
    Bundle b;
    String uri;
    int flag;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent i = getIntent();
        b=i.getExtras();
        uri = b.getString("uri");
        flag = b.getInt("flag");
        giveFlag(flag);
        getUri();

        //adding intent values to be matched
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        //initialising manager and channel
        manager=(WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        channel=manager.initialize(this,getMainLooper(),null);
    }

    /** register the BroadcastReceiver with the intent values to be matched */
    @Override
    public void onResume() {
        super.onResume();
        receiver = new WifiBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public void setIsWifiP2pEnabled(boolean b) {
        isWifiP2pEnabled=b;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.atn_direct_discover:
                if (!isWifiP2pEnabled) {
                    Toast.makeText(MainActivity.this, "Enable WIFI", Toast.LENGTH_SHORT).show();
                    return true;
                }
                final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.list_frag);
                fragment.onInitiateDiscovery();
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(MainActivity.this, "Discovery Failed : " + reasonCode, Toast.LENGTH_SHORT).show();
                    }
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
        DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.list_frag);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.detail_frag);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }
    }

    @Override
    public void showDetails(WifiP2pDevice device) {
        DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.detail_frag);
        fragment.showDetails(device);

    }

    public void connect(WifiP2pConfig config) {
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                //ignore
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this, "Connection failed,Retry.", Toast.LENGTH_SHORT).show();
            }
        });

    }

    @Override
    public void disconnect() {
        final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.detail_frag);
        fragment.resetViews();
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                //manager.
                fragment.getView().setVisibility(View.GONE);
            }

            @Override
            public void onFailure(int reason) {
            }
        });
    }

    @Override
    public void cancelDisconnect() {
        /** A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (manager != null) {
            final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.list_frag);
            if (fragment.getDevice() == null || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE || fragment.getDevice().status == WifiP2pDevice.INVITED) {
                manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, "Aborting connection", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(MainActivity.this, "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    //added because of ChannelListener
    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            resetData();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        }
        else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

    public void getUri()
    {
        DeviceDetailFragment deviceDetailFragment = (DeviceDetailFragment)getFragmentManager().findFragmentById(R.id.detail_frag);
        deviceDetailFragment.getStrPath(uri);

    }

    public void giveFlag(int temp)
    {
        DeviceDetailFragment deviceDetailFragment = (DeviceDetailFragment)getFragmentManager().findFragmentById(R.id.detail_frag);
        deviceDetailFragment.getFlag(temp);
    }


    public void hideButtons()
    {
        DeviceDetailFragment deviceDetailFragment = (DeviceDetailFragment)getFragmentManager().findFragmentById(R.id.detail_frag);
        deviceDetailFragment.hideButtons();
    }
    public void showButtons()
    {
        DeviceDetailFragment deviceDetailFragment = (DeviceDetailFragment)getFragmentManager().findFragmentById(R.id.detail_frag);
        deviceDetailFragment.showButtons();
    }

}