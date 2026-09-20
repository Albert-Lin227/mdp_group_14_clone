package com.example.mdp_group_14;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.RequiresPermission;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

public class BluetoothConnectionService {
    private static final String TAG = "Debugging Tag";
    private static final String appName = "MDP_Grp_14";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final long ACCEPT_RETRY_MS = 1000L;
    private static final long LINK_TIMEOUT_MS = 3000L;
    private static final long LINK_HEALTH_CHECK_MS = 250L;
    // Bring-up aid: send one line as soon as the link is up so the Mac terminal shows
    // "[BT RECEIVED]: HELLO_FROM_TABLET" without touching any button. Set to false once
    // the link is verified.
    private static final boolean SEND_HELLO_ON_CONNECT = true;

    private final BluetoothAdapter mBluetoothAdapter;
    Context mContext;

    private AcceptThread mInsecureAcceptThread;

    private ConnectThread mConnectThread;
    private BluetoothDevice mmDevice;
    private UUID deviceUUID;
    ProgressDialog mProgressDialog;
    Intent connectionStatus;
    //
    public static boolean BluetoothConnectionStatus=false;
    // Mirrors the RPi's /bluetooth_bridge/link_ok state. A socket may remain
    // connected while this is false if the peer has stopped sending data.
    public static volatile boolean BluetoothLinkOk = false;
    private static ConnectedThread mConnectedThread;
    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private volatile long lastSeenElapsedMs = 0L;
    private boolean linkHealthPublished = false;

    private final Runnable acceptRetryRunnable = new Runnable() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void run() {
            synchronized (BluetoothConnectionService.this) {
                if (!BluetoothConnectionStatus) {
                    startAcceptThread();
                }
            }
        }
    };

    private final Runnable linkHealthRunnable = new Runnable() {
        @Override
        public void run() {
            final long lastSeen = lastSeenElapsedMs;
            final boolean linkOk = BluetoothConnectionStatus
                    && lastSeen > 0L
                    && SystemClock.elapsedRealtime() - lastSeen < LINK_TIMEOUT_MS;
            publishLinkHealth(linkOk);
            retryHandler.postDelayed(this, LINK_HEALTH_CHECK_MS);
        }
    };

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public BluetoothConnectionService(Context context) {
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mContext = context;
        startAcceptThread();
        retryHandler.post(linkHealthRunnable);
    }

    //This thread will be running while listening for an incoming connection. Behaves like a
    //server-side client. Runs until connection is accepted or cancelled.
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket ServerSocket;

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, MY_UUID);
                Log.d(TAG, "Accept Thread: Setting up Server using: " + MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Accept Thread: IOException: " + e.getMessage());
            }
            ServerSocket = tmp;
        }
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public void run(){
            Log.d(TAG, "run: AcceptThread Running. ");
            BluetoothSocket socket =null;
            if (ServerSocket == null) {
                Log.e(TAG, "run: RFCOMM server socket was not created");
                acceptThreadFinished(this);
                return;
            }
            try {
                Log.d(TAG, "run: RFCOM server socket start here...");

                socket = ServerSocket.accept();
            }catch (IOException e){
                Log.e(TAG, "run: IOException: " + e.getMessage());
            }
            if(socket!=null){
                connected(socket, socket.getRemoteDevice());
            }
            Log.i(TAG, "END AcceptThread");
            acceptThreadFinished(this);
        }
        public void cancel(){
            Log.d(TAG, "cancel: Cancelling AcceptThread");
            try{
                ServerSocket.close();
            } catch(IOException e){
                Log.e(TAG, "cancel: Failed to close AcceptThread ServerSocket " + e.getMessage());
            }
        }
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            Log.d(TAG, "ConnectThread: started.");
            mmDevice = device;
            deviceUUID = uuid;
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public void run() {
            BluetoothSocket tmp = null;
            Log.d(TAG, "RUN: mConnectThread");

            try {
                Log.d(TAG, "ConnectThread: Trying to create InsecureRfcommSocket using UUID: " + MY_UUID);
                tmp = mmDevice.createRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread: Could not create InsecureRfcommSocket " + e.getMessage());
            }
            mmSocket = tmp;
            //mBluetoothAdapter.cancelDiscovery();

            try {
                mmSocket.connect();

                Log.d(TAG, "RUN: ConnectThread connected.");

                connected(mmSocket, mmDevice);

            } catch (IOException e) {
                try {
                    mmSocket.close();
                    Log.d(TAG, "RUN: ConnectThread socket closed.");
                } catch (IOException e1) {
                    Log.e(TAG, "RUN: ConnectThread: Unable to close connection in socket." + e1.getMessage());
                }
                Log.d(TAG, "RUN: ConnectThread: could not connect to UUID." + MY_UUID);
                try {



//                        BluetoothSetUp mBluetoothPopUpActivity = new Intent("");
//                        mBluetoothPopUpActivity.runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                Toast.makeText(mContext, "Failed to connect to the Device.", Toast.LENGTH_LONG).show();
//                            }
//                        });

                } catch (Exception z) {
                    z.printStackTrace();
                    Log.e(TAG,"error here");
                }

            }
            try {
                mProgressDialog.dismiss();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        public void cancel(){
            Log.d(TAG, "cancel: Closing Client Socket");
            try{
                mmSocket.close();
            } catch(IOException e){
                Log.e(TAG, "cancel: Failed to close ConnectThread mSocket " + e.getMessage());
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public synchronized void startAcceptThread(){
        Log.d(TAG, "start");

        //Cancel any thread attempting to make a connection
        if(mConnectThread!=null){
            mConnectThread.cancel();
            mConnectThread=null;
        }

        // If no listener is active, start one. AcceptThread schedules a retry
        // when accept() fails or returns without a socket.
        if(mInsecureAcceptThread == null || !mInsecureAcceptThread.isAlive()){
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }
    }

    private synchronized void acceptThreadFinished(AcceptThread finishedThread) {
        if (mInsecureAcceptThread != finishedThread) {
            return;
        }
        mInsecureAcceptThread = null;
        if (!BluetoothConnectionStatus) {
            Log.d(TAG, "AcceptThread ended; retrying listener in " + ACCEPT_RETRY_MS + " ms");
            retryHandler.removeCallbacks(acceptRetryRunnable);
            retryHandler.postDelayed(acceptRetryRunnable, ACCEPT_RETRY_MS);
        }
    }

    public void startClientThread(BluetoothDevice device, UUID uuid){
        Log.d(TAG, "startClient: Started.");
        try {
            mProgressDialog = ProgressDialog.show(mContext, "Connecting Bluetooth", "Please Wait...", true);
        } catch (Exception e) {
            Log.d(TAG, "StartClientThread Dialog show failure");
        }
        mConnectThread = new ConnectThread(device, uuid);
        mConnectThread.start();
    }

    private class ConnectedThread extends Thread{
        private final BluetoothSocket mSocket;
        private final InputStream inStream;
        private final OutputStream outStream;

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "ConnectedThread: Starting.");

            this.mSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            connectionStatus = new Intent("ConnectionStatus");
            connectionStatus.putExtra("Status", "connected");
            connectionStatus.putExtra("Device", mmDevice);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(connectionStatus);
            BluetoothConnectionStatus = true;
            lastSeenElapsedMs = SystemClock.elapsedRealtime();
            publishLinkHealth(true);

            // This constructor runs on the accept/connect worker thread. Touching a TextView
            // here throws CalledFromWrongThreadException before the streams are set up, so
            // the reader thread never starts. Hand the UI update to the main thread instead.
            String deviceName = null;
            try {
                deviceName = mmDevice.getName();
            } catch (SecurityException e) {
                Log.e(TAG, "Missing BLUETOOTH_CONNECT permission: " + e.getMessage());
            }
            updateStatusViews(true, deviceName);

            try {
                tmpIn = mSocket.getInputStream();
                tmpOut = mSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            inStream = tmpIn;
            outStream = tmpOut;
        }
        // Logic is a bit wonky - good to fix if possible (sometimes messages are sent 1 char at a time)
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            if (SEND_HELLO_ON_CONNECT && outStream != null) {
                write("HELLO_FROM_TABLET\n".getBytes(Charset.defaultCharset()));
            }

            while (true) {
                try {
                    if (inStream == null) {
                        throw new IOException("Bluetooth input stream unavailable");
                    }
                    bytes = inStream.read(buffer);
                    if (bytes < 0) {
                        throw new IOException("Bluetooth stream closed");
                    }
                    if (bytes == 0) {
                        continue;
                    }
                    lastSeenElapsedMs = SystemClock.elapsedRealtime();
                    publishLinkHealth(true);
                    String incomingMessage = new String(buffer, 0, bytes);
                    Log.d(TAG, "InputStream: " + incomingMessage);

                    Intent incomingMessageIntent = new Intent("incomingMessage");
                    incomingMessageIntent.putExtra("receivedMessage", incomingMessage);

                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(incomingMessageIntent);
                } catch (IOException e) {
                    Log.e(TAG, "Error reading input stream. " + e.getMessage());

                    cancel();   // release the dead socket so the peer sees EOF promptly

                    connectionStatus = new Intent("ConnectionStatus");
                    connectionStatus.putExtra("Status", "disconnected");
                    updateStatusViews(false, null);
                    connectionStatus.putExtra("Device", mmDevice);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(connectionStatus);
                    BluetoothConnectionStatus = false;
                    lastSeenElapsedMs = 0L;
                    publishLinkHealth(false);
                    scheduleAcceptRetry();

                    break;
                }
            }
        }

        /*
        public void write(byte[] bytes){
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "write: Writing to output stream: "+text);
            try {
                outStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Error writing to output stream. "+e.getMessage());
            }
        }
        */

        //updated write method to ensure every outgoing message ends with a newline character (\n). This is to ensure proper display on foxglove
        // Inside ConnectedThread in BluetoothConnectionService.java

        public void write(byte[] bytes) {
            String text = new String(bytes, Charset.defaultCharset());
            if (!text.endsWith("\n")) {
                text = text + "\n";
                bytes = text.getBytes(Charset.defaultCharset());
            }
            Log.d(TAG, "write: Writing to output stream: " + text);
            try {
                outStream.write(bytes);
                outStream.flush(); // FORCE IMMEDIATE TRANSMISSION OVER BLUETOOTH
            } catch (IOException e) {
                Log.e(TAG, "Error writing to output stream. " + e.getMessage());
            }
        }

        public void cancel(){
            Log.d(TAG, "cancel: Closing Client Socket");
            try{
                mSocket.close();
            } catch(IOException e){
                Log.e(TAG, "cancel: Failed to close ConnectThread mSocket " + e.getMessage());
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connected(BluetoothSocket mSocket, BluetoothDevice device) {
        Log.d(TAG, "connected: Starting.");
        mmDevice =  device;
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        mConnectedThread = new ConnectedThread(mSocket);
        mConnectedThread.start();
    }

    // Safe to call from any thread: the views are only touched on the main looper, and a
    // missing Home fragment (views not created yet, or already destroyed) is tolerated.
    private void updateStatusViews(final boolean connected, final String deviceName) {
        retryHandler.post(new Runnable() {
            @Override
            public void run() {
                TextView status = Home.getBluetoothStatus();
                if (status != null) {
                    status.setText(connected ? "Connected" : "Disconnected");
                    status.setTextColor(connected ? Color.GREEN : Color.RED);
                }
                if (connected && deviceName != null) {
                    TextView device = Home.getConnectedDevice();
                    if (device != null) {
                        device.setText(deviceName);
                    }
                }
            }
        });
    }

    private void scheduleAcceptRetry() {
        retryHandler.removeCallbacks(acceptRetryRunnable);
        retryHandler.postDelayed(acceptRetryRunnable, ACCEPT_RETRY_MS);
    }

    private void publishLinkHealth(boolean linkOk) {
        if (linkHealthPublished && BluetoothLinkOk == linkOk) {
            return;
        }
        linkHealthPublished = true;
        BluetoothLinkOk = linkOk;
        Intent linkStatus = new Intent("BluetoothLinkStatus");
        linkStatus.putExtra("link_ok", linkOk);
        linkStatus.putExtra("last_seen_elapsed_ms", lastSeenElapsedMs);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(linkStatus);
    }

    public static void write(byte[] out){
        Log.d(TAG, "write: Write is called." );
        ConnectedThread thread = mConnectedThread;
        if (thread == null || !BluetoothConnectionStatus) {
            Log.w(TAG, "write: no active Bluetooth connection, dropping message");
            return;
        }
        thread.write(out);
    }
}
