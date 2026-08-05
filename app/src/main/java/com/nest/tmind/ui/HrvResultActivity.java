package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.nest.tmind.R;
import com.nest.tmind.ecg.EcgConfig;
import com.nest.tmind.ecg.EcgSurfaceView;
import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.ecg.MeasureSessionStats;
import com.nest.tmind.util.HistoryStore;
import com.nest.tmind.util.MissionManager;
import com.nest.tmind.util.RussellEmotionCalculator;

import java.util.Arrays;

/** HRV 측정 결과 — 최신 세션 평균값 표시 (재측정 시마다 갱신) */
public class HrvResultActivity extends BaseSeniorActivity {

    private EcgSurfaceView ecgView;
    private TextView tvBpm, tvHrvMs, tvDate;
    private Button btnNext;
    private boolean resultValid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hrv_result);

        ecgView = findViewById(R.id.ecgView);
        tvBpm = findViewById(R.id.tvBpm);
        tvHrvMs = findViewById(R.id.tvHrvMs);
        tvDate = findViewById(R.id.tvDate);
        btnNext = findViewById(R.id.btnNext);

        ecgView.setDrawGrid(false);
        ecgView.setDrawAxisLabels(false);
        ecgView.setSpeed(25f);
        ecgView.setGain(10f);
        ecgView.setWaveColor(ContextCompat.getColor(this, R.color.teal_primary));

        bindLatestResult();

        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvBpmLabel, R.id.tvBpm,
                R.id.tvHrvLabel, R.id.tvHrvMs, R.id.tvExplain);

        btnNext.setOnClickListener(v -> onConfirmOrRemeasure());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (resultValid) {
                    Toast.makeText(HrvResultActivity.this, R.string.confirm_result_hint, Toast.LENGTH_SHORT).show();
                } else {
                    // 비정상 결과는 미션 미완료로 두고 홈으로
                    goDashboard();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 재측정 직후 진입 시 prefs 기준으로 다시 바인딩
        bindLatestResult();
    }

    private void onConfirmOrRemeasure() {
        if (!resultValid) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.hrv_result_invalid)
                    .setPositiveButton(R.string.hrv_remeasure, (d, w) -> goRemeasure())
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
            return;
        }
        new MissionManager(this).setHrvDone();
        // 측정 확인 후 HRV 기반 4사분면(분석 결과) 화면으로 이동
        startActivity(new Intent(this, AnalysisResultActivity.class));
        finish();
    }

    private void goRemeasure() {
        Intent i = new Intent(this, HrvGuideActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void bindLatestResult() {
        // 분석 직후 메모리 값이 있으면 prefs 재로드로 덮어쓰지 않음
        if (!LastEcgResult.hasValid()) {
            LastEcgResult.loadFromPrefs(this);
        }

        int hr = LastEcgResult.lastHrBpm;
        int hrv = LastEcgResult.lastHrvMs;
        long at = LastEcgResult.measuredAtMs;

        // 세션 평균이 남아 있으면(분석 직후) 우선 반영
        int sessionHr = MeasureSessionStats.averageHr();
        int sessionHrv = MeasureSessionStats.averageHrvMs();
        if (sessionHr > 0) hr = sessionHr;
        if (sessionHrv > 0) hrv = sessionHrv;
        if (at <= 0) at = System.currentTimeMillis();

        // 생리 범위 밖이면 표시 보정 (비정상 순간값 방지)
        if (hr > 0 && (hr < 40 || hr > 180)) hr = 0;
        if (hrv < 0) hrv = 0;

        // 세션/메모리에 값이 있는데 LastEcgResult가 비어 있으면 보강 저장
        if ((hr > 0 || hrv > 0) && !LastEcgResult.hasValid()) {
            int stress = 0;
            if (hrv > 0) {
                int hrvForScore = Math.max(20, Math.min(160, hrv));
                stress = Math.round(((160f - hrvForScore) / 140f) * 100f);
            }
            LastEcgResult.updateAndSave(this, LastEcgResult.lastSpike, 250, hr, hrv,
                    hr > 0 ? Math.round(60000f / hr) : 0, stress, at);
        }

        resultValid = hr > 0 && hrv > 0;

        tvBpm.setText(hr > 0 ? String.valueOf(hr) : "--");
        tvHrvMs.setText(hrv > 0 ? String.valueOf(hrv) : "--");
        tvDate.setText(EcgConfig.formatMeasuredTime(at));

        if (btnNext != null) {
            btnNext.setText(resultValid ? R.string.confirm_result : R.string.hrv_remeasure);
        }

        // 정상 결과만 분석 히스토리 저장
        if (resultValid) {
            RussellEmotionCalculator.Point point = RussellEmotionCalculator.fromHrvStress(
                    LastEcgResult.lastStressScore, hrv, hr);
            HistoryStore.addAnalysis(this, "", hr, hrv, at, point.valence, point.arousal);
        }

        if (LastEcgResult.lastSpike != null && LastEcgResult.lastSpike.length > 0) {
            float[] spike = LastEcgResult.lastSpike;
            int fs = LastEcgResult.lastFs > 0 ? LastEcgResult.lastFs : 250;
            ecgView.post(() -> {
                int w = Math.max(200, ecgView.getWidth());
                float[] wave = resizeWave(spike, w);
                normalizeWave(wave);
                ecgView.setSampleRateHz(fs);
                ecgView.showStaticWave(wave, fs);
            });
        } else {
            ecgView.clearStreaming();
        }
    }

    private void goDashboard() {
        Intent i = new Intent(this, DashboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private static float[] resizeWave(float[] src, int outLen) {
        if (src == null || src.length == 0 || outLen <= 0) return new float[0];
        if (src.length == outLen) return Arrays.copyOf(src, src.length);
        float[] out = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            double t = i * (src.length - 1) / (double) (outLen - 1);
            int i0 = (int) Math.floor(t);
            int i1 = Math.min(src.length - 1, i0 + 1);
            double frac = t - i0;
            out[i] = (float) (src[i0] * (1.0 - frac) + src[i1] * frac);
        }
        return out;
    }

    private static void normalizeWave(float[] wave) {
        float maxAbs = 0f;
        for (float v : wave) maxAbs = Math.max(maxAbs, Math.abs(v));
        if (maxAbs > 0f) {
            float s = 0.6f / maxAbs;
            for (int i = 0; i < wave.length; i++) wave[i] *= s;
        }
    }
}
