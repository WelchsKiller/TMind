package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.nest.tmind.R;
import com.nest.tmind.api.MemberApiManager;
import com.nest.tmind.ecg.EcgConfig;
import com.nest.tmind.util.BlePermissionHelper;
import com.nest.tmind.util.MissionManager;

/** HRV 측정 안내 (패치 부착 설명 + BLE 권한/연결 준비) */
public class HrvGuideActivity extends BaseSeniorActivity {

    private static final String SKIP_REASON = "USER_SKIP";

    private ActivityResultLauncher<String[]> permLauncher;
    private MissionManager mission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hrv_guide);
        mission = new MissionManager(this);

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
        btnSkip.setOnClickListener(v -> confirmSkipHrv());
        btnStart.setOnClickListener(v -> requestBleAndStart());
    }

    private void confirmSkipHrv() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.hrv_skip_confirm)
                .setPositiveButton(R.string.hrv_skip, (d, w) -> skipHrvAndContinue())
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void skipHrvAndContinue() {
        boolean event = mission.isAdditionalMeasureMode();
        MemberApiManager.ensureSessionStarted(this, event, new MemberApiManager.ResultCallback<Long>() {
            @Override
            public void onSuccess(Long data) {
                runOnUiThread(() -> {
                    MemberApiManager.skipHrv(HrvGuideActivity.this, event, SKIP_REASON);
                    mission.setHrvDone();
                    Intent i = new Intent(HrvGuideActivity.this, EmaIntroActivity.class);
                    i.putExtra(EmaSurveyActivity.EXTRA_SESSION_TYPE,
                            event ? com.nest.tmind.util.EmaQuestionBank.SessionType.EVENT.name()
                                    : mapMainEmaSession().name());
                    startActivity(i);
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                        Toast.makeText(HrvGuideActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private com.nest.tmind.util.EmaQuestionBank.SessionType mapMainEmaSession() {
        switch (MissionManager.mainSessionByHour()) {
            case AFTERNOON:
                return com.nest.tmind.util.EmaQuestionBank.SessionType.AFTERNOON;
            default:
                return com.nest.tmind.util.EmaQuestionBank.SessionType.MORNING;
        }
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
        finish();
    }
}
