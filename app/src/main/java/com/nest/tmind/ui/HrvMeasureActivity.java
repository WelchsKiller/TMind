package com.nest.tmind.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;

import com.nest.tmind.R;
import com.nest.tmind.ecg.EcgBleService;
import com.nest.tmind.ecg.EcgResultAnalyzer;
import com.nest.tmind.ecg.EcgSurfaceView;
import com.nest.tmind.ecg.pref.AppVerifyStorage;
import com.nest.tmind.ecg.MetricsManager;
import com.nest.tmind.ecg.model.AppVerifyResult;
import com.nest.tmind.view.CircularProgressView;

/** APP-USR-010: BLE 연결 후 5분 HRV 측정 */
public class HrvMeasureActivity extends BaseSeniorActivity
        implements EcgBleService.EcgListener, EcgBleService.ConnectionListener {

    @Override
    protected boolean needsBleConnection() {
        return true;
    }

    private static final int MEASURE_SEC = 5 * 60;
    private static final int FS_PLOT = 250;

    private EcgSurfaceView ecgView;
    private CircularProgressView circularProgress;
    private TextView tvTimer, tvSignal, tvTimerLabel;
    private View step1, step2, step3;

    private EcgBleService svc;
    private boolean bound;
    private int remainingSec = MEASURE_SEC;
    private boolean countdownStarted;
    private boolean measurementStarted;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean waitingBleReady;
    private Runnable countdownRunnable;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            EcgBleService.EcgBinder b = (EcgBleService.EcgBinder) service;
            svc = b.getService();
            bound = true;
            svc.addListener(HrvMeasureActivity.this);
            svc.addConnectionListener(HrvMeasureActivity.this);
            resetMeasurementState();
            showWaitingForConnectionUi();
            svc.startFreshScanForMeasure();
            startWaitBleReady();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            cleanupServiceBinding();
        }
    };

    private final Runnable waitBleReadyRunnable = new Runnable() {
        @Override
        public void run() {
            if (!bound || svc == null) {
                waitingBleReady = false;
                return;
            }
            if (svc.isReadyForMeasure()) {
                waitingBleReady = false;
                beginMeasurementIfNeeded();
            } else {
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hrv_measure);

        ecgView = findViewById(R.id.ecgView);
        circularProgress = findViewById(R.id.circularProgress);
        tvTimer = findViewById(R.id.tvTimer);
        tvTimerLabel = findViewById(R.id.tvTimerLabel);
        tvSignal = findViewById(R.id.tvSignal);
        step1 = findViewById(R.id.step1);
        step2 = findViewById(R.id.step2);
        step3 = findViewById(R.id.step3);

        highlightStep(1);
        ecgView.setCornerRadiusDp(12f);
        ecgView.setSeniorGridStyle();
        ecgView.setSpeed(12.5f);
        ecgView.setGain(10f);
        ecgView.setWaveCenterRatio(0.5f);
        ecgView.setFilters(false, false);
        ecgView.setDrawGrid(true);
        ecgView.setDrawAxisLabels(false);
        ecgView.setWaveColor(Color.parseColor("#006666"));

        findViewById(R.id.btnBack).setOnClickListener(v -> exitMeasureScreen());
        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvInstruction);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                exitMeasureScreen();
            }
        });

        showWaitingForConnectionUi();
        resetMeasurementState();
    }

    private void resetMeasurementState() {
        measurementStarted = false;
        countdownStarted = false;
        remainingSec = MEASURE_SEC;
        handler.removeCallbacksAndMessages(null);
        countdownRunnable = null;
    }

    private void showWaitingForConnectionUi() {
        remainingSec = MEASURE_SEC;
        tvTimer.setText("5:00");
        tvTimerLabel.setText(R.string.connecting_device);
        tvSignal.setText(R.string.connecting_device);
        tvSignal.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        circularProgress.setProgress(1f);
    }

    private void showConnectingUi() {
        tvTimerLabel.setText(R.string.connecting_device);
        tvSignal.setText(R.string.connecting_device);
        tvSignal.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
    }

    private void beginMeasurementIfNeeded() {
        if (measurementStarted || svc == null) return;
        measurementStarted = true;
        // 연결 직후 여유를 두고 3→2→1 안내 후 측정 시작
        tvTimerLabel.setText(R.string.measure_ready_buffer);
        tvSignal.setText(R.string.measure_ready_buffer);
        tvSignal.setTextColor(ContextCompat.getColor(this, R.color.teal_primary));
        if (tts != null) tts.speak(getString(R.string.measure_ready_buffer));
        runPreStartCountdown(3);
    }

    /** 측정 시작 전 3-2-1 카운트다운 (연결 직후 여유) */
    private void runPreStartCountdown(int sec) {
        if (!bound || svc == null) {
            measurementStarted = false;
            return;
        }
        if (sec <= 0) {
            if (svc.startMeasurement()) {
                ecgView.clearStreaming();
                tvTimerLabel.setText(R.string.time_remaining);
                tvSignal.setText(R.string.signal_waiting);
                tvSignal.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                startCountdownOnce();
            } else {
                measurementStarted = false;
            }
            return;
        }
        String msg = getString(R.string.measure_countdown, sec);
        tvTimer.setText(String.valueOf(sec));
        tvTimerLabel.setText(msg);
        tvSignal.setText(msg);
        if (tts != null) tts.speak(String.valueOf(sec));
        handler.postDelayed(() -> runPreStartCountdown(sec - 1), 1000);
    }

    private void exitMeasureScreen() {
        stopWaitBleReady();
        resetMeasurementState();
        MetricsManager.getInstance().reset();
        ecgView.clearStreaming();
        stopBleCompletely();
        finish();
    }

    private void stopBleCompletely() {
        if (bound && svc != null) {
            svc.stopMeasurementAndDisconnect();
            cleanupServiceBinding();
        }
        EcgBleService.stopBleService(this);
    }

    private void highlightStep(int step) {
        step1.setSelected(step == 1);
        step2.setSelected(step == 2);
        step3.setSelected(step == 3);
    }

    private void startCountdownOnce() {
        if (countdownStarted) return;
        countdownStarted = true;
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (remainingSec <= 0) {
                    finishMeasure();
                    return;
                }
                remainingSec--;
                updateTimerUi();
                handler.postDelayed(this, 1000);
            }
        };
        updateTimerUi();
        handler.postDelayed(countdownRunnable, 1000);
    }

    private void updateTimerUi() {
        int min = remainingSec / 60;
        int sec = remainingSec % 60;
        tvTimer.setText(String.format("%d:%02d", min, sec));
        circularProgress.setProgress(remainingSec / (float) MEASURE_SEC);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MetricsManager.getInstance().startMeasure();
        Intent i = new Intent(this, EcgBleService.class);
        ContextCompat.startForegroundService(this, i);
        bindService(i, conn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopWaitBleReady();
    }

    @Override
    protected void onDestroy() {
        if (countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
        }
        cleanupServiceBinding();
        super.onDestroy();
    }

    private void cleanupServiceBinding() {
        stopWaitBleReady();
        if (bound && svc != null) {
            svc.removeListener(this);
            svc.removeConnectionListener(this);
            unbindService(conn);
            bound = false;
            svc = null;
        }
    }

    private void startWaitBleReady() {
        if (waitingBleReady) return;
        waitingBleReady = true;
        handler.post(waitBleReadyRunnable);
    }

    private void stopWaitBleReady() {
        waitingBleReady = false;
        handler.removeCallbacks(waitBleReadyRunnable);
    }

    private void finishMeasure() {
        if (countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
        }
        MetricsManager.getInstance().recordJobFinished(true);
        // BLE 종료 전에 분석·저장해야 재측정 시 결과가 갱신됨
        saveMetrics();
        EcgResultAnalyzer.analyze(this);
        stopBleCompletely();

        String boxMsg = getString(R.string.measure_complete_message);
        if (tts != null) tts.speak(boxMsg.replace("\n", " "));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.measure_complete_title)
                .setMessage(boxMsg)
                .setCancelable(false)
                .setPositiveButton(R.string.confirm_result, (d, w) -> {
                    startActivity(new Intent(this, HrvResultActivity.class));
                    finish();
                })
                .show();
    }

    private void saveMetrics() {
        AppVerifyResult result = MetricsManager.getInstance().buildResultRow();
        AppVerifyStorage.addResult(this, result);
        MetricsManager.getInstance().reset();
    }

    @Override
    public void onBleConnectionFailed() {
        runOnUiThread(() -> {
            resetMeasurementState();
            tvTimerLabel.setText(R.string.connect_failed);
            tvSignal.setText(R.string.connect_failed);
            tvSignal.setTextColor(ContextCompat.getColor(this, R.color.red_record));
            if (tts != null) {
                tts.speak(getString(R.string.connect_failed));
            }
        });
    }

    @Override
    public void onBleConnectionChanged(boolean ready) {
        runOnUiThread(() -> {
            if (ready) {
                beginMeasurementIfNeeded();
            } else if (!measurementStarted) {
                showConnectingUi();
            }
        });
    }

    @Override
    public void onSamples(float[] samples) {
        runOnUiThread(() -> {
            ecgView.setSampleRateHz(FS_PLOT);
            ecgView.addSamples(samples);
        });
    }

    @Override
    public void onHrRr(Integer hr, Integer hrvMs) {
        // 피드백: 신호 대기/양호 문구는 표시하지 않음
    }
}
