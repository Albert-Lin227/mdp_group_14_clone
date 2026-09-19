package com.example.mdp_group_14;

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

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

/** Owns the RFCOMM socket; BluetoothReconnectService owns its process lifetime. */
public class BluetoothConnectionService {
    private static final String TAG = "BluetoothConnection";
    private static final String APP_NAME = "MDP_Grp_14";
    public static final UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String PREFS = "bluetooth_connection";
    private static final String PREF_DEVICE_ADDRESS = "device_address";
    private static final long RECONNECT_DELAY_MS = 5_000L;
    private static final long ACCEPT_RETRY_DELAY_MS = 1_000L;
    private static final long LINK_TIMEOUT_MS = 3_000L;
    private static final long LINK_WATCHDOG_INTERVAL_MS = 250L;

    public static volatile boolean BluetoothConnectionStatus = false;
    private static volatile BluetoothConnectionService instance;
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private BluetoothDevice device;
    private UUID uuid = DEFAULT_UUID;
    private ConnectThread connectThread;
    private AcceptThread acceptThread;
    private ConnectedThread connectedThread;
    private boolean reconnectRequested;
    private boolean acceptRetryScheduled;
    private volatile long lastSeenElapsedMs;

    private final Runnable reconnectRunnable = new Runnable() {
        @Override public void run() {
            synchronized (lock) {
                if (!reconnectRequested || BluetoothConnectionStatus || device == null) return;
            }
            connect(device, uuid, false);
        }
    };

    private final Runnable linkWatchdog = new Runnable() {
        @Override public void run() {
            ConnectedThread staleThread = null;
            synchronized (lock) {
                if (connectedThread != null && !isLinkOkLocked()) staleThread = connectedThread;
            }
            if (staleThread != null) {
                Log.w(TAG, "Bluetooth link timed out waiting for inbound data");
                staleThread.cancel();
                onDisconnected(staleThread);
            }
            handler.postDelayed(this, LINK_WATCHDOG_INTERVAL_MS);
        }
    };

    public BluetoothConnectionService(Context context) {
        this.context = context.getApplicationContext();
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        instance = this;
        handler.post(linkWatchdog);
        startAcceptThread();
    }

    public static BluetoothConnectionService getInstance(Context context) {
        BluetoothConnectionService current = instance;
        return current != null ? current : new BluetoothConnectionService(context);
    }

    public static String getConnectedDeviceName() {
        BluetoothConnectionService current = instance;
        return current != null && current.device != null ? current.device.getName() : null;
    }

    /** Returns the Android counterpart to the RPi bridge's /link_ok state. */
    public static boolean isLinkOk() {
        BluetoothConnectionService current = instance;
        if (current == null) return false;
        synchronized (current.lock) { return current.isLinkOkLocked(); }
    }

    /** Explicitly connects and persists the device for future launches/reconnects. */
    public void startClientThread(BluetoothDevice device, UUID uuid) { connect(device, uuid, true); }

    public void restoreLastConnection() {
        if (!isAdapterEnabled()) return;
        String address = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREF_DEVICE_ADDRESS, null);
        if (address == null) return;
        try {
            connect(adapter.getRemoteDevice(address), DEFAULT_UUID, false);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Saved Bluetooth address is invalid", e);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(PREF_DEVICE_ADDRESS).apply();
        }
    }

    /** Starts one RFCOMM server accept attempt. Failed attempts are retried. */
    public void startAcceptThread() {
        synchronized (lock) {
            if (connectedThread != null || acceptThread != null || !isAdapterEnabled()) return;
            acceptThread = new AcceptThread();
            acceptThread.start();
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

    private final class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket;
        private volatile boolean cancelled;

        AcceptThread() { setName("BluetoothAcceptThread"); }

        @Override public void run() {
            BluetoothSocket socket = null;
            boolean accepted = false;
            try {
                serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, DEFAULT_UUID);
                socket = serverSocket.accept();
                accepted = socket != null;
                if (socket != null) onConnected(socket, socket.getRemoteDevice(), null);
            } catch (IOException | SecurityException e) {
                if (!cancelled) Log.w(TAG, "RFCOMM accept failed", e);
            } finally {
                closeServerSocket(serverSocket);
                onAcceptThreadFinished(this, accepted && !cancelled);
            }
        }

        void cancel() { cancelled = true; closeServerSocket(serverSocket); }
    }

    private final class ConnectThread extends Thread {
        private final BluetoothDevice target;
        private final UUID targetUuid;
        private BluetoothSocket socket;

        ConnectThread(BluetoothDevice target, UUID targetUuid) {
            this.target = target;
            this.targetUuid = targetUuid;
            setName("BluetoothConnectThread");
        }

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

    /** source is null for a connection accepted by the RFCOMM server socket. */
    private void onConnected(BluetoothSocket socket, BluetoothDevice connectedDevice, ConnectThread source) {
        synchronized (lock) {
            if ((source != null && connectThread != source) || connectedThread != null) {
                closeSocket(socket);
                return;
            }
            if (acceptThread != null) {
                acceptThread.cancel();
                acceptThread = null;
            }
            device = connectedDevice;
            connectThread = null;
            connectedThread = new ConnectedThread(socket);
            // The successful RFCOMM handshake is an initial liveness event.
            lastSeenElapsedMs = SystemClock.elapsedRealtime();
            BluetoothConnectionStatus = true;
            handler.removeCallbacks(reconnectRunnable);
        }
        notifyStatus("connected", connectedDevice);
        connectedThread.start();
    }

    private void onAcceptThreadFinished(AcceptThread source, boolean accepted) {
        synchronized (lock) {
            if (acceptThread == source) acceptThread = null;
            if (!accepted && connectedThread == null) scheduleAcceptRetryLocked();
        }
    }

    private void scheduleAcceptRetryLocked() {
        if (acceptRetryScheduled) return;
        acceptRetryScheduled = true;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                synchronized (lock) { acceptRetryScheduled = false; }
                startAcceptThread();
            }
        }, ACCEPT_RETRY_DELAY_MS);
    }

    private final class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream input;
        private final OutputStream output;

        ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream in = null;
            OutputStream out = null;
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
            } catch (IOException e) {
                Log.w(TAG, "Could not open Bluetooth streams", e);
            }
            input = in;
            output = out;
            setName("BluetoothConnectedThread");
        }

        @Override public void run() {
            if (input == null) { onDisconnected(this); return; }
            byte[] buffer = new byte[1024];
            try {
                while (!isInterrupted()) {
                    int bytes = input.read(buffer);
                    if (bytes <= 0) break;
                    // Equivalent to the RPi bridge updating last_rx_ns_ on
                    // inbound data: peer activity is liveness.
                    lastSeenElapsedMs = SystemClock.elapsedRealtime();
                    Intent message = new Intent("incomingMessage");
                    message.putExtra("receivedMessage",
                            new String(buffer, 0, bytes, Charset.defaultCharset()));
                    LocalBroadcastManager.getInstance(context).sendBroadcast(message);
                }
            } catch (IOException e) {
                Log.i(TAG, "Bluetooth connection closed", e);
            }
            onDisconnected(this);
        }

        void write(byte[] bytes) throws IOException {
            if (output == null) throw new IOException("Bluetooth output stream is unavailable");
            output.write(bytes);
            output.flush();
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
            lastSeenElapsedMs = 0L;
        }
        notifyStatus("disconnected", device);
        scheduleReconnect();
        startAcceptThread();
    }

    private boolean isLinkOkLocked() {
        return connectedThread != null
                && lastSeenElapsedMs > 0L
                && SystemClock.elapsedRealtime() - lastSeenElapsedMs < LINK_TIMEOUT_MS;
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
        ConnectedThread thread = current == null ? null : current.connectedThread;
        if (thread == null || !BluetoothConnectionStatus) {
            Log.w(TAG, "Ignoring write while Bluetooth is disconnected");
            return;
        }
        try {
            thread.write(bytes);
        } catch (IOException e) {
            Log.w(TAG, "Bluetooth write failed", e);
            current.onDisconnected(thread);
        }
    }

    private void notifyStatus(final String status, final BluetoothDevice statusDevice) {
        Intent intent = new Intent("ConnectionStatus");
        intent.putExtra("Status", status);
        intent.putExtra("Device", statusDevice);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        handler.post(new Runnable() {
            @Override public void run() {
                TextView statusView = Home.getBluetoothStatus();
                if (statusView != null) {
                    statusView.setText("connected".equals(status) ? "Connected" : "Disconnected");
                    statusView.setTextColor("connected".equals(status) ? Color.GREEN : Color.RED);
                }
                TextView deviceView = Home.getConnectedDevice();
                if (deviceView != null && statusDevice != null && "connected".equals(status)) {
                    deviceView.setText(statusDevice.getName());
                }
            }
        });
    }

    private static void closeSocket(BluetoothSocket socket) {
        if (socket == null) return;
        try { socket.close(); } catch (IOException ignored) { }
    }

    private static void closeServerSocket(BluetoothServerSocket serverSocket) {
        if (serverSocket == null) return;
        try { serverSocket.close(); } catch (IOException ignored) { }
    }

    private boolean isAdapterEnabled() {
        try {
            return adapter != null && adapter.isEnabled();
        } catch (SecurityException e) {
            // Android 12+ BLUETOOTH_CONNECT is runtime permission. The next
            // service start after it is granted will restore the saved device.
            Log.i(TAG, "Bluetooth permission has not been granted yet");
            return false;
        }
    }
}
