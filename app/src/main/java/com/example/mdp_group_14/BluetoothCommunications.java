package com.example.mdp_group_14;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class BluetoothCommunications extends Fragment {
    private static final String TAG = "BluetoothComms";

    private SharedPreferences sharedPreferences;
    private TextView messageReceivedTextView;
    private EditText typeBoxEditText;

    public static void updateMessageLog(Context context, String message) {
        Intent intent = new Intent("uiMessage");
        intent.putExtra("receivedMessage", message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        IntentFilter filter = new IntentFilter();
        filter.addAction("incomingMessage");
        filter.addAction("uiMessage");
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mReceiver, filter);
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.activity_communications, container, false);


        ImageButton send;
        send = root.findViewById(R.id.messageButton);

        // Message Box
        messageReceivedTextView = root.findViewById(R.id.messageReceivedTitleTextView);
        messageReceivedTextView.setMovementMethod(new ScrollingMovementMethod());
        typeBoxEditText = root.findViewById(R.id.typeBoxEditText);

        // get shared preferences
        sharedPreferences = requireActivity().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);

        send.setOnClickListener(view -> {
            showLog("Clicked sendTextBtn");
            String sentText = typeBoxEditText.getText().toString();

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("message", sharedPreferences.getString("message", "") + '\n' + sentText);
            editor.apply();
            messageReceivedTextView.append(sentText + "\n");
            typeBoxEditText.setText("");

            Home.printMessage(sentText);
            showLog("Exiting sendTextBtn");
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mReceiver);
    }

    private static void showLog(String message) {
        Log.d(TAG, message);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String text = intent.getStringExtra("receivedMessage");
            if (messageReceivedTextView != null) {
                messageReceivedTextView.append(text + "\n");
            }
        }
    };
}
