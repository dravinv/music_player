package com.awesome.dravin.appdemo1;


import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class DeviceDetailFragment extends Fragment implements WifiP2pManager.ConnectionInfoListener, View.OnClickListener {
    //protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;

    ProgressDialog progressDialog = null;
    private static ProgressDialog mProgressDialog;

    String strPath;

    private int flag = 1;

    public static String WiFiServerIp = "";
    public static String WiFiClientIp = "";
    static Boolean ClientCheck = false;
    public static String GroupOwnerAddress = "";
    static long ActualFilelength = 0;
    static int Percentage = 0;

    public DeviceDetailFragment() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.fragment_device_detail, container, false);
        if (savedInstanceState == null) {
            SharedPreferencesHandler.setStringValues(getActivity(), "cnt", "0");
        }
        Button connectButton = (Button) mContentView.findViewById(R.id.btn_connect);
        connectButton.setOnClickListener(this);
        Button disconnectButton = (Button) mContentView.findViewById(R.id.btn_disconnect);
        disconnectButton.setOnClickListener(this);
        Button send = (Button) mContentView.findViewById(R.id.send);
        send.setOnClickListener(this);
        Button next = (Button) mContentView.findViewById(R.id.test);
        next.setOnClickListener(this);
        return mContentView;
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);
        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        //view.setText("Am I the Group Owner?" + ((info.isGroupOwner == true) ? "Yes" : "No"));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
        //view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        String GroupOwner = info.groupOwnerAddress.getHostAddress();
        if (GroupOwner != null && !GroupOwner.equals(""))
            SharedPreferencesHandler.setStringValues(getActivity(), "GroupOwnerAddress", GroupOwner);

        if (info.groupFormed && info.isGroupOwner) {
            /*
             * set shared preferences which remember that device is server.
        	 */
            SharedPreferencesHandler.setStringValues(getActivity(), "ServerBoolean", "true");
            FileServerAsyncTask FileServerobj = new FileServerAsyncTask(getActivity(), FileTransferService.PORT);
            if (FileServerobj != null) {
                FileServerobj.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new String[]{null});
            }
        } else {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            if (!ClientCheck) {
                firstConnectionMessage firstObj = new firstConnectionMessage(GroupOwnerAddress);
                if (firstObj != null) {
                    firstObj.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new String[]{null});
                    //firstObj.execute();
                }
            }

            FileServerAsyncTask FileServerobj = new FileServerAsyncTask(getActivity(), FileTransferService.PORT);
            if (FileServerobj != null) {
                FileServerobj.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new String[]{null});
            }
        }
        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
    }

    /*
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText("");
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText("");
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText("");
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText("");
        this.getView().setVisibility(View.GONE);
        /*
         * Remove All the prefrences here
         */
        SharedPreferencesHandler.setStringValues(getActivity(), "GroupOwnerAddress", "");
        SharedPreferencesHandler.setStringValues(getActivity(), "ServerBoolean", "");
        SharedPreferencesHandler.setStringValues(getActivity(), "WiFiClientIp", "");
    }

    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        //view.setText(device.deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        //view.setText(device.toString());
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_connect:
                WifiP2pConfig config = new WifiP2pConfig();
                config.groupOwnerIntent = 0;
                if (device.deviceAddress == null) {
                    Toast.makeText(getActivity(), "Select Device First", Toast.LENGTH_SHORT).show();
                } else {
                    config.deviceAddress = device.deviceAddress;
                    config.wps.setup = WpsInfo.PBC;
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel", "Connecting to :" + device.deviceAddress, true, true);
                    ((DeviceListFragment.DeviceActionListener) getActivity()).connect(config);
                }
                break;
            case R.id.btn_disconnect:
                ((DeviceListFragment.DeviceActionListener) getActivity()).disconnect();
                break;
            case R.id.send:
                try {
                    sendFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.test:
                Intent intent = new Intent(getActivity(), TestActivity.class);
                startActivity(intent);
                break;
        }
    }

    private void sendFile() throws IOException {
        File f;
        if (strPath == "null") {
            f = new File(Environment.getExternalStorageDirectory() + "/" + "Testing" + "/" + "abc.txt");
        } else {
            f = new File(strPath);
        }
        File dirs = new File(f.getParent());
        if (!dirs.exists())
            dirs.mkdirs();
        if (!f.exists())
            f.createNewFile();
        String Extension = f.getName();
        //Toast.makeText(getActivity(),Extension,Toast.LENGTH_LONG).show();
        Long FileLength = f.length();
        ActualFilelength = FileLength;
        Uri uri = Uri.fromFile(f);
        Toast.makeText(getActivity(), uri.toString(), Toast.LENGTH_LONG).show();
        Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
        /**************************************************************************************/
        /*
         * Choose on which device file has to send whether its server or client
    	 */
        String OwnerIp = SharedPreferencesHandler.getStringValues(getActivity(), "GroupOwnerAddress");
        if (OwnerIp != null && OwnerIp.length() > 0) {
            String host = null;
            int sub_port = -1;

            String ServerBool = SharedPreferencesHandler.getStringValues(getActivity(), "ServerBoolean");
            if (ServerBool != null && !ServerBool.equals("") && ServerBool.equalsIgnoreCase("true")) {

                int count = Integer.parseInt(SharedPreferencesHandler.getStringValues(getActivity(), "count"));
                char ch = 'A';
                for (int i = 0; i < count; i++) {
                    String Ip = SharedPreferencesHandler.getStringValues(getActivity(), ch + "");
                    Toast.makeText(getActivity(), "wifiClientIP:" + Ip, Toast.LENGTH_SHORT).show();
                    if (Ip != null && !Ip.equals("")) {
                        // Get Client Ip Address and send data
                        host = Ip;
                        sub_port = FileTransferService.PORT;
                        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS, Ip);
                    }
                    /*******************************************************************/
                    serviceIntent.putExtra(FileTransferService.Extension, Extension);
                    serviceIntent.putExtra(FileTransferService.Filelength, ActualFilelength + "");
                    serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, FileTransferService.PORT);
                    if (host != null && sub_port != -1) {
                        showprogress("Sending.....");
                        getActivity().startService(serviceIntent);
                    } else {
                        DismissProgressDialog();
                        Toast.makeText(getActivity(), "Host Address not found, Please Re-Connect", Toast.LENGTH_SHORT).show();
                    }
                    ch++;
                }
            } else {
                FileTransferService.PORT = 8888;
                host = OwnerIp;
                sub_port = FileTransferService.PORT;
                /*******************************************************/
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS, OwnerIp);
                serviceIntent.putExtra(FileTransferService.Extension, Extension);
                serviceIntent.putExtra(FileTransferService.Filelength, ActualFilelength + "");
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, FileTransferService.PORT);
                if (host != null && sub_port != -1) {
                    showprogress("Sending.....");
                    getActivity().startService(serviceIntent);
                } else {
                    DismissProgressDialog();
                    Toast.makeText(getActivity(), "Host Address not found, Please Re-Connect", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            DismissProgressDialog();
            Toast.makeText(getActivity(), "Host Address not found, Please Re-Connect", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    static Handler handler;

    public class FileServerAsyncTask extends AsyncTask<String, String, String> {

        private Context mFilecontext;
        private String Extension, Key;
        private File EncryptedFile;
        private long ReceivedFileLength;
        private int PORT;

        public FileServerAsyncTask(Context context, int port) {
            this.mFilecontext = context;
            handler = new Handler();
            this.PORT = port;
            if (mProgressDialog == null)
                mProgressDialog = new ProgressDialog(mFilecontext, ProgressDialog.THEME_HOLO_LIGHT);
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                // init handler for progressdialog
                ServerSocket serverSocket = new ServerSocket(PORT);
                Socket client = serverSocket.accept();
                WiFiClientIp = client.getInetAddress().getHostAddress();
                ObjectInputStream ois = new ObjectInputStream(client.getInputStream());
                WifiTransferModel obj = null;
                String InetAddress;
                try {
                    obj = (WifiTransferModel) ois.readObject();
                    InetAddress = obj.getInetAddress();
                    if (!InetAddress.equals("")) {//&& InetAddress.equalsIgnoreCase(FileTransferService.inetaddress)) {
                        int count = Integer.parseInt(SharedPreferencesHandler.getStringValues(mFilecontext, "count"));
                        char ch = 'A';
                        for (int i = 0; i < count; i++) ch++;
                        SharedPreferencesHandler.setStringValues(mFilecontext, ch + "", WiFiClientIp);
                        count++;
                        SharedPreferencesHandler.setStringValues(mFilecontext, "count", count + "");
                        //set boolean true which identify that this device will act as server.
                        SharedPreferencesHandler.setStringValues(mFilecontext, "ServerBoolean", "true");
                        ois.close(); // close the ObjectOutputStream object
                        // after saving
                        serverSocket.close();
                        return "Demo";
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }

                final Runnable r = new Runnable() {
                    public void run() {
                        mProgressDialog.setMessage("Receiving...");
                        mProgressDialog.setIndeterminate(false);
                        mProgressDialog.setMax(100);
                        mProgressDialog.setProgress(0);
                        mProgressDialog.setProgressNumberFormat(null);
                        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        mProgressDialog.show();
                    }
                };
                handler.post(r);

                final File f = new File(Environment.getExternalStorageDirectory() + "/" + "toBeCopiedHere" + "/" + obj.getFileName());
                String uri = f.getAbsolutePath();
                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();
                /*
                 * Receive file length and copy after it
				 */
                this.ReceivedFileLength = obj.getFileLength();
                InputStream inputstream = client.getInputStream();
                copyReceivedFile(inputstream, new FileOutputStream(f), ReceivedFileLength);
                ois.close(); // close the ObjectOutputStream object after saving
                // file to storage.
                serverSocket.close();
                /*
                 * Set file related data and decrypt file in postExecute.
				 */
                if (flag == 0) {
                    if (uri.endsWith(".mp4")) {
                        Intent intent = new Intent(getActivity(), VideoPlayer.class).putExtra("uri", uri);
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(getActivity(), MusicPlayerReceiver.class).putExtra("uri", uri);
                        startActivity(intent);
                    }
                }
                return "Demo";
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                if (!result.equalsIgnoreCase("Demo")) {
                    //transfer successful
                } else {
                    /*
					 * To initiate socket again we are intiating async task
					 * in this condition.
					 */
                    FileServerAsyncTask FileServerobj = new FileServerAsyncTask(mFilecontext, FileTransferService.PORT);
                    if (FileServerobj != null) {
                        FileServerobj.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new String[]{null});
                    }
                }
            }
        }

        @Override
        protected void onPreExecute() {
            if (mProgressDialog == null) {
                mProgressDialog = new ProgressDialog(mFilecontext);
            }
        }

    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        long total = 0;
        long test = 0;
        byte buf[] = new byte[FileTransferService.ByteSize];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
                try {
                    total += len;
                    if (ActualFilelength > 0) {
                        Percentage = (int) ((total * 100) / ActualFilelength);
                    }
                    mProgressDialog.setProgress(Percentage);
                } catch (Exception e) {
                    e.printStackTrace();
                    Percentage = 0;
                    ActualFilelength = 0;
                }
            }
            if (mProgressDialog != null) {
                if (mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static boolean copyReceivedFile(InputStream inputStream, OutputStream out, Long length) {

        byte buf[] = new byte[FileTransferService.ByteSize];
        byte Decryptedbuf[] = new byte[FileTransferService.ByteSize];
        String Decrypted;
        int len;
        long total = 0;
        int progresspercentage = 0;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                try {
                    out.write(buf, 0, len);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                try {
                    total += len;
                    if (length > 0) {
                        progresspercentage = (int) ((total * 100) / length);
                    }
                    mProgressDialog.setProgress(progresspercentage);
                } catch (Exception e) {
                    e.printStackTrace();
                    if (mProgressDialog != null) {
                        if (mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                        }
                    }
                }
            }
            // dismiss progress after sending
            if (mProgressDialog != null) {
                if (mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public void showprogress(final String task) {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(getActivity(), ProgressDialog.THEME_HOLO_LIGHT);
        }
        Handler handle = new Handler();
        final Runnable send = new Runnable() {
            public void run() {
                mProgressDialog.setMessage(task);
                mProgressDialog.setIndeterminate(false);
                mProgressDialog.setMax(100);
                mProgressDialog.setProgressNumberFormat(null);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.show();
            }
        };
        handle.post(send);
    }

    public static void DismissProgressDialog() {
        try {
            if (mProgressDialog != null) {
                if (mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
    }

    /*
     * Async class that has to be called when connection establish first time. Its main motive is to send blank message
     * to server so that server knows the IP address of client to send files Bi-Directional.
     */
    class firstConnectionMessage extends AsyncTask<String, Void, String> {
        String GroupOwnerAddress = "";

        public firstConnectionMessage(String owner) {
            this.GroupOwnerAddress = owner;
        }

        @Override
        protected String doInBackground(String... params) {

            Intent serviceIntent = new Intent(getActivity(), WiFiClientIpTransferService.class);
            serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
            if (info.groupOwnerAddress.getHostAddress() != null) {
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS, info.groupOwnerAddress.getHostAddress());
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, FileTransferService.PORT);
                serviceIntent.putExtra(FileTransferService.inetaddress, FileTransferService.inetaddress);
            }
            getActivity().startService(serviceIntent);
            return "success";
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result != null) {
                if (result.equalsIgnoreCase("success")) {
                    ClientCheck = true;
                }
            }
        }
    }

    public void getStrPath(String temp) {
        strPath = temp;
    }

    public void getFlag(int temp) {
        flag = temp;
        if (flag == 0) {
            Button send = (Button) mContentView.findViewById(R.id.send);
            send.setVisibility(View.GONE);
        }
    }

    public void hideButtons() {
        Button connectButton = (Button) mContentView.findViewById(R.id.btn_connect);
        Button disconnectButton = (Button) mContentView.findViewById(R.id.btn_disconnect);
        Button send = (Button) mContentView.findViewById(R.id.send);
        Button next = (Button) mContentView.findViewById(R.id.test);

        connectButton.setVisibility(View.GONE);
        disconnectButton.setVisibility(View.GONE);
        send.setVisibility(View.GONE);
        next.setVisibility(View.GONE);
    }

    public void showButtons() {
        Button connectButton = (Button) mContentView.findViewById(R.id.btn_connect);
        Button disconnectButton = (Button) mContentView.findViewById(R.id.btn_disconnect);
        Button send = (Button) mContentView.findViewById(R.id.send);
        Button next = (Button) mContentView.findViewById(R.id.test);

        connectButton.setVisibility(View.VISIBLE);
        disconnectButton.setVisibility(View.VISIBLE);
        send.setVisibility(View.VISIBLE);
        next.setVisibility(View.VISIBLE);
    }

}