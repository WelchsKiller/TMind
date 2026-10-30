package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.nest.tmind.R;
import com.nest.tmind.api.ApiModels;
import com.nest.tmind.api.MemberApiManager;
import com.nest.tmind.ecg.EcgConfig;
import com.nest.tmind.ecg.EcgSampleGenerator;
import com.nest.tmind.ecg.EcgSurfaceView;
import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.util.MissionManager;
import com.nest.tmind.util.SessionManager;

import java.util.Arrays;

/** HRV 측정 결과 — 최신 세션 평균값 표시 (재측정 시마다 갱신) */
public class HrvResultActivity extends BaseSeniorActivity {

    private static final String TAG = "HrvResult";
    /** BLE 없이 Raw ECG 표시 확인용 테스트 진입 */
    public static final String EXTRA_SHOW_RAW_TEST = "show_raw_test";

    private EcgSurfaceView ecgView;
    private TextView tvBpm, tvHrvMs, tvDate;
    private Button btnNext;
    private boolean resultValid;
    private boolean navigating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hrv_result);

        ecgView = findViewById(R.id.ecgView);
        tvBpm = findViewById(R.id.tvBpm);
        tvHrvMs = findViewById(R.id.tvHrvMs);
        tvDate = findViewById(R.id.tvDate);
        btnNext = findViewById(R.id.btnNext);

        boolean rawTest = getIntent().getBooleanExtra(EXTRA_SHOW_RAW_TEST, false);

        // 측정·테스트 공통: Raw 파형 + 용지속도 25 mm/s. 테스트 안내는 테스트 진입만.
        ecgView.setDrawGrid(true);
        ecgView.setDrawAxisLabels(rawTest);
        ecgView.setSpeed(25f);
        ecgView.setGain(10f);
        ecgView.setWaveColor(ContextCompat.getColor(this, R.color.teal_primary));

        TextView tvRawCaption = findViewById(R.id.tvRawCaption);
        if (tvRawCaption != null) {
            if (rawTest) {
                tvRawCaption.setVisibility(View.VISIBLE);
                tvRawCaption.setText(R.string.hrv_result_raw_caption);
            } else {
                tvRawCaption.setVisibility(View.GONE);
            }
        }

        bindLatestResult();
        restoreAdditionalMode();

        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvBpmLabel, R.id.tvBpm,
                R.id.tvHrvLabel, R.id.tvHrvMs, R.id.tvExplain);

        btnNext.setBackgroundTintList(null);
        btnNext.setOnClickListener(v -> onConfirmOrRemeasure());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (resultValid) {
                    Toast.makeText(HrvResultActivity.this, R.string.confirm_result_hint, Toast.LENGTH_SHORT).show();
                } else {
                    goDashboard();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!navigating) {
            bindLatestResult();
        }
    }

    private void onConfirmOrRemeasure() {
        if (navigating) return;

        if (!resultValid) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.hrv_result_invalid)
                    .setPositiveButton(R.string.hrv_remeasure, (d, w) -> goRemeasure())
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
            return;
        }

        navigating = true;
        btnNext.setEnabled(false);
        MissionManager mission = new MissionManager(this);
        boolean additional = restoreAdditionalMode();
        SessionManager sm = new SessionManager(this);
        long sid = sm.getCurrentSessionId(additional);
        if (sid > 0 && sm.isPredictionSaved(sid)) {
            navigating = false;
            btnNext.setEnabled(true);
            Toast.makeText(this, R.string.hrv_cannot_remeasure_after_result, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "심박변이도를 서버에 저장하는 중입니다.", Toast.LENGTH_SHORT).show();

        try {
            MemberApiManager.ResultCallback<Long> afterSession =
                    new MemberApiManager.ResultCallback<Long>() {
                        @Override
                        public void onSuccess(Long data) {
                            if (additional && !isDistinctEventSession(data)) {
                                onError("추가 측정용 세션이 없습니다. 홈에서 추가 측정을 다시 눌러 주세요.");
                                return;
                            }
                            MemberApiManager.uploadHrv(HrvResultActivity.this, additional,
                                    LastEcgResult.measuredAtMs,
                                    new MemberApiManager.ResultCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void ignored) {
                                            runOnUiThread(() -> {
                                                mission.setHrvDone();
                                                AfterMissionSaved.afterHrv(HrvResultActivity.this, additional);
                                            });
                                        }

                                        @Override
                                        public void onError(String message) {
                                            runOnUiThread(() -> {
                                                navigating = false;
                                                btnNext.setEnabled(true);
                                                Toast.makeText(HrvResultActivity.this,
                                                        "심박변이도 서버 저장 실패: " + message,
                                                        Toast.LENGTH_LONG).show();
                                            });
                                        }
                                    });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                navigating = false;
                                btnNext.setEnabled(true);
                                Toast.makeText(HrvResultActivity.this,
                                        message != null ? message : "세션을 시작하지 못했습니다.",
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    };
            if (additional) {
                MemberApiManager.ensureSessionStarted(this, true, afterSession);
            } else {
                // 오늘 정규 세션 ID 에 맞춰 올린다. 다른 세션에 올리면 추가 측정이 E00205 로 막힌다.
                MemberApiManager.fetchToday(this, new MemberApiManager.ResultCallback<ApiModels.TodayResponse>() {
                    @Override
                    public void onSuccess(ApiModels.TodayResponse data) {
                        MemberApiManager.ensureSessionStarted(HrvResultActivity.this, false, afterSession);
                    }

                    @Override
                    public void onError(String message) {
                        MemberApiManager.ensureSessionStarted(HrvResultActivity.this, false, afterSession);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "confirm failed", e);
            navigating = false;
            btnNext.setEnabled(true);
            Toast.makeText(this, R.string.hrv_confirm_error, Toast.LENGTH_LONG).show();
        }
    }

    private boolean restoreAdditionalMode() {
        MissionManager mission = new MissionManager(this);
        boolean additional = getIntent().getBooleanExtra(AnalysisResultActivity.EXTRA_ADDITIONAL, false)
                || mission.isAdditionalMeasureMode();
        if (additional) {
            mission.setAdditionalMeasureMode(true);
        }
        return additional;
    }

    private boolean isDistinctEventSession(Long eventId) {
        SessionManager sm = new SessionManager(this);
        long id = eventId != null ? eventId : sm.getCurrentSessionId(true);
        if (id <= 0) return false;
        long mainId = sm.getCurrentSessionId(false);
        long todayId = sm.getServerTodaySessionId();
        if (mainId > 0 && id == mainId) return false;
        if (todayId > 0 && id == todayId) return false;
        return !sm.isPredictionSaved(id);
    }

    private void goRemeasure() {
        Intent i = new Intent(this, HrvGuideActivity.class);
        i.putExtra(AnalysisResultActivity.EXTRA_ADDITIONAL, restoreAdditionalMode());
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void bindLatestResult() {
        int hr = LastEcgResult.lastHrBpm;
        int hrv = LastEcgResult.lastHrvMs;
        long at = LastEcgResult.measuredAtMs;
        if (at <= 0) at = System.currentTimeMillis();

        resultValid = LastEcgResult.hasValid();
        if (!resultValid) {
            hr = 0;
            hrv = 0;
        }

        tvBpm.setText(hr > 0 ? String.valueOf(hr) : "--");
        tvHrvMs.setText(hrv > 0 ? String.valueOf(hrv) : "--");
        tvDate.setText(EcgConfig.formatMeasuredTime(at));

        if (btnNext != null && !navigating) {
            btnNext.setText(resultValid ? R.string.confirm_result : R.string.hrv_remeasure);
        }

        final float[] rawWave = pickRawDisplayWave();
        if (resultValid && rawWave != null && rawWave.length > 0) {
            final int fs = LastEcgResult.lastFs > 0 ? LastEcgResult.lastFs : 250;
            Runnable draw = () -> {
                ecgView.setSampleRateHz(fs);
                ecgView.showStaticWave(rawWave, fs);
            };
            ecgView.post(draw);
            // Surface/레이아웃 준비 후 한 번 더 (빈 그래프 방지)
            ecgView.postDelayed(draw, 120);
        } else {
            ecgView.clearStreaming();
        }
    }

    /** 결과 화면: 업로드용 Raw 우선, 없으면 분석이 남긴 Raw 구간. 합성 QRS 없음. */
    private static float[] pickRawDisplayWave() {
        float[] raw = LastEcgResult.lastRawSignal;
        int fs = LastEcgResult.lastFs > 0 ? LastEcgResult.lastFs : 250;
        if (raw != null && raw.length >= Math.max(16, fs / 2)) {
            float[] out = EcgSampleGenerator.tailSegment(raw, fs, 4);
            if (out.length > 0) {
                Log.i(TAG, "raw display samples=" + out.length + " fs=" + fs
                        + " fullRaw=" + raw.length);
                return out;
            }
        }
        if (LastEcgResult.lastSpike != null && LastEcgResult.lastSpike.length > 0) {
            Log.i(TAG, "raw display fallback lastSpike=" + LastEcgResult.lastSpike.length);
            return Arrays.copyOf(LastEcgResult.lastSpike, LastEcgResult.lastSpike.length);
        }
        Log.w(TAG, "raw display empty");
        return null;
    }

    private void goDashboard() {
        Intent i = new Intent(this, DashboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
