package com.example.mdp_group_14;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class BluetoothSetUpActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new BluetoothSetUp())
                    .commit();
        }
    }
}