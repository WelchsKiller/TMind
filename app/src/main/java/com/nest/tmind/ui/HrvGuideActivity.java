package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.nest.tmind.R;
import com.nest.tmind.ecg.EcgConfig;
import com.nest.tmind.util.BlePermissionHelper;

/** HRV 측정 안내 (패치 부착 설명 + BLE 권한/연결 준비) */
public class HrvGuideActivity extends BaseSeniorActivity {

    private ActivityResultLauncher<String[]> permLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hrv_guide);

        permLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (BlePermissionHelper.allGranted(result)) {
                        startMeasureIfReady();
                    } else {
                        Toast.makeText(this, R.string.ble_permission_denied, Toast.LENGTH_LONG).show();
                    }
                });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvGuide);

        Button btnSkip = findViewById(R.id.btnSkip);
        Button btnStart = findViewById(R.id.btnStart);
        btnSkip.setOnClickListener(v -> requestBleAndStart());
        btnStart.setOnClickListener(v -> requestBleAndStart());
    }

    private void requestBleAndStart() {
        if (BlePermissionHelper.hasAll(this)) {
            startMeasureIfReady();
        } else {
            permLauncher.launch(BlePermissionHelper.requiredPermissions());
        }
    }

    private void startMeasureIfReady() {
        if (!EcgConfig.isBluetoothOn(this)) {
            Toast.makeText(this, R.string.ble_turn_on, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception ignored) {
            }
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                && !EcgConfig.isLocationServiceOn(this)) {
            Toast.makeText(this, R.string.location_turn_on, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } catch (Exception ignored) {
            }
            return;
        }

        startActivity(new Intent(this, HrvMeasureActivity.class));
    }
}
