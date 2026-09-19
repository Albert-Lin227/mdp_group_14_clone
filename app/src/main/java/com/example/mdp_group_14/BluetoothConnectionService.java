package com.example.mdp_group_14;

<<<<<<< HEAD
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
=======
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
>>>>>>> 01d0f92 (Amended code for retry loop around startAcceptThread in BluetoothConnectionService.java file, along with a simple heartbeat/last seen timestamp check, similar to the RPI's link_ok pattern.)
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
<<<<<<< HEAD
=======
import android.os.SystemClock;
>>>>>>> 01d0f92 (Amended code for retry loop around startAcceptThread in BluetoothConnectionService.java file, along with a simple heartbeat/last seen timestamp check, similar to the RPI's link_ok pattern.)
import android.util.Log;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;
<<<<<<< HEAD

/** Owns the RFCOMM socket; BluetoothReconnectService owns its process lifetime. */
public class BluetoothConnectionService {
    private static final String TAG = "BluetoothConnection";
    public static final UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String PREFS = "bluetooth_connection";
    private static final String PREF_DEVICE_ADDRESS = "device_address";
    private static final long RECONNECT_DELAY_MS = 5_000L;

    public static volatile boolean BluetoothConnectionStatus = false;
    private static volatile BluetoothConnectionService instance;
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private BluetoothDevice device;
    private UUID uuid = DEFAULT_UUID;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private boolean reconnectRequested;

    private final Runnable reconnectRunnable = new Runnable() {
        @Override public void run() {
            synchronized (lock) {
                if (!reconnectRequested || BluetoothConnectionStatus || device == null) return;
            }
            connect(device, uuid, false);
=======
//original code
public class BluetoothConnectionService {
    private static final String TAG = "Debugging Tag";
    private static final String appName = "MDP_Grp_14";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // Keep these aligned with the RPi bridge defaults.
    private static final long ACCEPT_RETRY_DELAY_MS = 1000L;
    private static final long LINK_TIMEOUT_MS = 3000L;
    private static final long LINK_WATCHDOG_INTERVAL_MS = 250L;

    private final BluetoothAdapter mBluetoothAdapter;
    Context mContext;

    private AcceptThread mInsecureAcceptThread;
    private boolean acceptRetryScheduled;

    private ConnectThread mConnectThread;
    private BluetoothDevice mmDevice;
    private UUID deviceUUID;
    ProgressDialog mProgressDialog;
    Intent connectionStatus;
//
    public static volatile boolean BluetoothConnectionStatus=false;
    private static ConnectedThread mConnectedThread;
    // The setup screen can construct a replacement service.  Only the latest
    // instance may own the shared connection/watchdog state.
    private static volatile BluetoothConnectionService activeService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile long lastSeenElapsedMs;
    private final Runnable linkWatchdog = new Runnable() {
        @Override
        public void run() {
            if (activeService != BluetoothConnectionService.this) {
                return;
            }
            final ConnectedThread connectedThread = mConnectedThread;
            final long lastSeen = lastSeenElapsedMs;
            final boolean linkOk = connectedThread != null
                    && lastSeen > 0
                    && SystemClock.elapsedRealtime() - lastSeen < LINK_TIMEOUT_MS;

            if (connectedThread != null && !linkOk) {
                Log.w(TAG, "Bluetooth link timed out waiting for inbound data");
                connectedThread.cancel();
                onConnectionLost();
            }
            mainHandler.postDelayed(this, LINK_WATCHDOG_INTERVAL_MS);
>>>>>>> 01d0f92 (Amended code for retry loop around startAcceptThread in BluetoothConnectionService.java file, along with a simple heartbeat/last seen timestamp check, similar to the RPI's link_ok pattern.)
        }
    };

    public BluetoothConnectionService(Context context) {
<<<<<<< HEAD
        this.context = context.getApplicationContext();
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        instance = this;
    }

    public static BluetoothConnectionService getInstance(Context context) {
        BluetoothConnectionService current = instance;
        return current != null ? current : new BluetoothConnectionService(context);
    }

    public static String getConnectedDeviceName() {
        BluetoothConnectionService current = instance;
        return current != null && current.device != null ? current.device.getName() : null;
    }

    /** Explicitly connects and persists the device for future launches/reconnects. */
    public void startClientThread(BluetoothDevice device, UUID uuid) { connect(device, uuid, true); }

    public void restoreLastConnection() {
        if (!isAdapterEnabled()) return;
        String address = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_DEVICE_ADDRESS, null);
        if (address == null) return;
        try { connect(adapter.getRemoteDevice(address), DEFAULT_UUID, false); }
        catch (IllegalArgumentException e) {
            Log.w(TAG, "Saved Bluetooth address is invalid", e);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PREF_DEVICE_ADDRESS).apply();
        }
    }

    private void connect(BluetoothDevice target, UUID targetUuid, boolean userInitiated) {
        if (target == null || !isAdapterEnabled()) { notifyStatus("disconnected", target); return; }
        synchronized (lock) {
            device = target;
            uuid = targetUuid == null ? DEFAULT_UUID : targetUuid;
            reconnectRequested = true;
            if (userInitiated) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PREF_DEVICE_ADDRESS, target.getAddress()).apply();
            handler.removeCallbacks(reconnectRunnable);
            if (connectThread != null) connectThread.cancel();
            if (connectedThread != null) connectedThread.cancel();
            BluetoothConnectionStatus = false;
            connectThread = new ConnectThread(target, uuid);
            connectThread.start();
        }
    }

    private final class ConnectThread extends Thread {
        private final BluetoothDevice target;
        private final UUID targetUuid;
        private BluetoothSocket socket;
        ConnectThread(BluetoothDevice target, UUID targetUuid) { this.target = target; this.targetUuid = targetUuid; setName("BluetoothConnectThread"); }
        @Override public void run() {
            try {
                adapter.cancelDiscovery();
                socket = target.createRfcommSocketToServiceRecord(targetUuid);
                socket.connect();
                onConnected(socket, target, this);
            } catch (IOException | SecurityException e) {
                Log.w(TAG, "Bluetooth connection failed", e);
                closeSocket(socket);
                scheduleReconnect();
            }
        }
        void cancel() { closeSocket(socket); }
    }

    private void onConnected(BluetoothSocket socket, BluetoothDevice connectedDevice, ConnectThread source) {
        synchronized (lock) {
            // A cancelled/older attempt completed after a newer request. Do not
            // let it replace the socket selected by the newer attempt.
            if (connectThread != source) { closeSocket(socket); return; }
            if (connectedThread != null) connectedThread.cancel();
            device = connectedDevice;
            connectThread = null;
            connectedThread = new ConnectedThread(socket);
            BluetoothConnectionStatus = true;
            handler.removeCallbacks(reconnectRunnable);
        }
        notifyStatus("connected", connectedDevice);
        connectedThread.start();
    }

    private final class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream input;
        private final OutputStream output;
        ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream in = null; OutputStream out = null;
            try { in = socket.getInputStream(); out = socket.getOutputStream(); }
            catch (IOException e) { Log.w(TAG, "Could not open Bluetooth streams", e); }
            input = in; output = out; setName("BluetoothConnectedThread");
        }
        @Override public void run() {
            if (input == null) { onDisconnected(this); return; }
            byte[] buffer = new byte[1024];
            try {
                while (!isInterrupted()) {
                    int bytes = input.read(buffer);
                    if (bytes < 0) break;
                    Intent message = new Intent("incomingMessage");
                    message.putExtra("receivedMessage", new String(buffer, 0, bytes, Charset.defaultCharset()));
                    LocalBroadcastManager.getInstance(context).sendBroadcast(message);
                }
            } catch (IOException e) { Log.i(TAG, "Bluetooth connection closed", e); }
            onDisconnected(this);
        }
        void write(byte[] bytes) throws IOException {
            if (output == null) throw new IOException("Bluetooth output stream is unavailable");
            output.write(bytes); output.flush();
        }
        void cancel() { closeSocket(socket); }
    }

    private void onDisconnected(ConnectedThread source) {
        synchronized (lock) {
            // Closing an old socket during a device switch must not mark the
            // replacement connection disconnected.
            if (connectedThread != source) return;
            BluetoothConnectionStatus = false;
            connectedThread = null;
        }
        notifyStatus("disconnected", device);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        synchronized (lock) {
            if (!reconnectRequested || device == null || !isAdapterEnabled()) return;
            handler.removeCallbacks(reconnectRunnable);
            handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
        }
    }

    public static void write(byte[] bytes) {
        BluetoothConnectionService current = instance;
        if (current == null || current.connectedThread == null || !BluetoothConnectionStatus) {
            Log.w(TAG, "Ignoring write while Bluetooth is disconnected"); return;
        }
        try { current.connectedThread.write(bytes); }
        catch (IOException e) { Log.w(TAG, "Bluetooth write failed", e); current.onDisconnected(current.connectedThread); }
    }

    private void notifyStatus(final String status, final BluetoothDevice statusDevice) {
        Intent intent = new Intent("ConnectionStatus");
        intent.putExtra("Status", status); intent.putExtra("Device", statusDevice);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        handler.post(new Runnable() {
            @Override public void run() {
                TextView statusView = Home.getBluetoothStatus();
                if (statusView != null) {
                    statusView.setText("connected".equals(status) ? "Connected" : "Disconnected");
                    statusView.setTextColor("connected".equals(status) ? Color.GREEN : Color.RED);
                }
                TextView deviceView = Home.getConnectedDevice();
                if (deviceView != null && statusDevice != null && "connected".equals(status)) deviceView.setText(statusDevice.getName());
            }
        });
    }

    private static void closeSocket(BluetoothSocket socket) {
        if (socket == null) return;
        try { socket.close(); } catch (IOException ignored) { }
    }

    private boolean isAdapterEnabled() {
        try { return adapter != null && adapter.isEnabled(); }
        catch (SecurityException e) {
            // Android 12+ BLUETOOTH_CONNECT is runtime permission. The next
            // service start after it is granted will restore the saved device.
            Log.i(TAG, "Bluetooth permission has not been granted yet");
            return false;
=======
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mContext = context;
        activeService = this;
        mainHandler.post(linkWatchdog);
        startAcceptThread();
    }

    //This thread will be running while listening for an incoming connection. Behaves like a
    //server-side client. Runs until connection is accepted or cancelled.
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket ServerSocket;
        private volatile boolean cancelled;

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
        public void run(){
            Log.d(TAG, "run: AcceptThread Running. ");
            BluetoothSocket socket =null;
            boolean accepted = false;
            try {
                Log.d(TAG, "run: RFCOM server socket start here...");

                if (ServerSocket == null) {
                    throw new IOException("RFCOMM server socket was not created");
                }
                socket = ServerSocket.accept();
                accepted = socket != null;
            }catch (IOException e){
                Log.e(TAG, "run: IOException: " + e.getMessage());
            }
            if(socket!=null){
                connected(socket, socket.getRemoteDevice());
            }
            Log.i(TAG, "END AcceptThread");
            onAcceptThreadFinished(this, accepted && !cancelled);
        }
        public void cancel(){
            Log.d(TAG, "cancel: Cancelling AcceptThread");
            cancelled = true;
            try{
                if (ServerSocket != null) {
                    ServerSocket.close();
                }
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

            if (mmSocket == null) {
                Log.e(TAG, "RUN: ConnectThread could not create a client socket.");
                return;
            }

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
            if (mmSocket != null) {
                try{
                    mmSocket.close();
                } catch(IOException e){
                    Log.e(TAG, "cancel: Failed to close ConnectThread mSocket " + e.getMessage());
                }
            }
        }
    }

    public synchronized void startAcceptThread(){
        Log.d(TAG, "start");

        //Cancel any thread attempting to make a connection
        if(mConnectThread!=null){
            mConnectThread.cancel();
            mConnectThread=null;
        }

        // Do not listen for a second connection while a live one is being watched.
        if (mConnectedThread != null) {
            return;
        }

        //If accept thread is null we want to start a new one
        if(mInsecureAcceptThread == null){
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }
    }

    private void onAcceptThreadFinished(AcceptThread thread, boolean accepted) {
        synchronized (this) {
            if (mInsecureAcceptThread == thread) {
                mInsecureAcceptThread = null;
            }
            if (activeService == this && !accepted && mConnectedThread == null) {
                scheduleAcceptRetryLocked();
            }
        }
    }

    private void scheduleAcceptRetryLocked() {
        if (acceptRetryScheduled) {
            return;
        }
        acceptRetryScheduled = true;
        mainHandler.postDelayed(() -> {
            synchronized (BluetoothConnectionService.this) {
                acceptRetryScheduled = false;
            }
            startAcceptThread();
        }, ACCEPT_RETRY_DELAY_MS);
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
            lastSeenElapsedMs = 0;

            mainHandler.post(() -> {
                TextView status = Home.getBluetoothStatus();
                if (status != null) {
                    status.setText("Connected");
                    status.setTextColor(Color.GREEN);
                }
                TextView device = Home.getConnectedDevice();
                if (device != null && mmDevice != null) {
                    device.setText(mmDevice.getName());
                }
            });

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

            while (true) {
                try {
                    bytes = inStream.read(buffer);
                    if (bytes <= 0) {
                        throw new IOException("Bluetooth input stream closed");
                    }
                    // This mirrors the RPi's last_rx_ns_: any received application data
                    // proves the RFCOMM peer is still alive.
                    lastSeenElapsedMs = SystemClock.elapsedRealtime();
                    String incomingMessage = new String(buffer, 0, bytes);
                    Log.d(TAG, "InputStream: " + incomingMessage);

                    Intent incomingMessageIntent = new Intent("incomingMessage");
                    incomingMessageIntent.putExtra("receivedMessage", incomingMessage);

                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(incomingMessageIntent);
                } catch (IOException e) {
                    Log.e(TAG, "Error reading input stream. " + e.getMessage());

                    onConnectionLost();
                    break;
                }
            }
        }
        public void write(byte[] bytes){
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "write: Writing to output stream: "+text);
            try {
                outStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Error writing to output stream. "+e.getMessage());
                onConnectionLost();
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

    private void connected(BluetoothSocket mSocket, BluetoothDevice device) {
        Log.d(TAG, "connected: Starting.");
        if (activeService != this) {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "connected: Could not close obsolete socket", e);
            }
            return;
        }
        mmDevice =  device;
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        mConnectedThread = new ConnectedThread(mSocket);
        mConnectedThread.start();
    }

    private synchronized void onConnectionLost() {
        if (activeService != this) {
            return;
        }
        if (mConnectedThread == null && !BluetoothConnectionStatus) {
            return;
        }
        mConnectedThread = null;
        lastSeenElapsedMs = 0;
        BluetoothConnectionStatus = false;

        connectionStatus = new Intent("ConnectionStatus");
        connectionStatus.putExtra("Status", "disconnected");
        connectionStatus.putExtra("Device", mmDevice);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(connectionStatus);
        mainHandler.post(() -> {
            TextView status = Home.getBluetoothStatus();
            if (status != null) {
                status.setText("Disconnected");
                status.setTextColor(Color.RED);
            }
        });
        scheduleAcceptRetryLocked();
    }

    public static void write(byte[] out){
        Log.d(TAG, "write: Write is called." );
        ConnectedThread connectedThread = mConnectedThread;
        if (connectedThread != null) {
            connectedThread.write(out);
        } else {
            Log.w(TAG, "write: Dropping message because Bluetooth is not connected");
>>>>>>> 01d0f92 (Amended code for retry loop around startAcceptThread in BluetoothConnectionService.java file, along with a simple heartbeat/last seen timestamp check, similar to the RPI's link_ok pattern.)
        }
    }
}
