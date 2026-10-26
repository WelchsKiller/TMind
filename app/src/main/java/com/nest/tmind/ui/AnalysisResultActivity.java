package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.nest.tmind.R;
import com.nest.tmind.api.MemberApiManager;
import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.util.HistoryStore;
import com.nest.tmind.util.RussellEmotionCalculator;
import com.nest.tmind.util.SessionManager;
import com.nest.tmind.view.RussellCircumplexView;

/** 세 가지 미션이 끝난 뒤 Russell 사분면을 표시하고 예측을 저장한다. */
public class AnalysisResultActivity extends BaseSeniorActivity {

    public static final String EXTRA_FROM_HRV = "from_hrv";
    public static final String EXTRA_ADDITIONAL = "additional";
    public static final String EXTRA_REENTRY = "reentry";
    public static final String EXTRA_PREDICTED_VALENCE = "predicted_valence";
    public static final String EXTRA_PREDICTED_AROUSAL = "predicted_arousal";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_result);

        TextView tvBpm = findViewById(R.id.tvBpm);
        TextView tvHrv = findViewById(R.id.tvHrv);
        RussellCircumplexView russell = findViewById(R.id.russellView);

        LastEcgResult.loadFromPrefs(this);
        RussellEmotionCalculator.Point point = buildPredictedPoint();
        if (point != null) {
            russell.setPoint(point.valence, point.arousal);
        }

        int[] metrics = resolveHrvMetrics();
        int bpm = metrics[0];
        int hrv = metrics[1];
        if (bpm >= 40 && bpm <= 180) {
            tvBpm.setText(getString(R.string.bpm_unit_format, bpm));
        } else if (bpm > 0) {
            tvBpm.setText(getString(R.string.bpm_unit_format, bpm));
        } else {
            tvBpm.setText("-- 회/분");
        }
        tvHrv.setText(hrv > 0 ? getString(R.string.hrv_unit_format, hrv) : "-- ms");
        if (bpm > 0 || hrv > 0) {
            new SessionManager(this).setLastHrvMetrics(bpm, hrv);
        }

        if (savedInstanceState == null && point != null && !isReentry()) {
            long at = LastEcgResult.measuredAtMs > 0
                    ? LastEcgResult.measuredAtMs
                    : System.currentTimeMillis();
            HistoryStore.addAnalysis(this, "", bpm, hrv, at, point.valence, point.arousal);
            MemberApiManager.savePrediction(this,
                    getIntent().getBooleanExtra(EXTRA_ADDITIONAL, false),
                    point.valence, point.arousal);
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        setupTtsFromViews(R.id.btnTts, R.id.tvResultHint, R.id.tvDisclaimer, R.id.tvFeedbackQuestion);

        Button btnYes = findViewById(R.id.btnYes);
        Button btnNo = findViewById(R.id.btnNo);
        Button btnUnknown = findViewById(R.id.btnUnknown);
        stylePrimaryButton(btnYes);
        stylePrimaryButton(btnNo);
        stylePrimaryButton(btnUnknown);

        btnYes.setOnClickListener(v -> goFeedback("agree"));
        btnNo.setOnClickListener(v -> {
            Intent i = new Intent(this, EmotionEditActivity.class);
            i.putExtra(FeedbackActivity.EXTRA_ADDITIONAL, isAdditional());
            if (getIntent().getBooleanExtra(EXTRA_FROM_HRV, false)) {
                i.putExtra(FeedbackActivity.EXTRA_FROM_HRV, true);
            }
            startActivity(i);
            finish();
        });
        btnUnknown.setOnClickListener(v -> goFeedback("unknown"));
    }

    /** 메모리 → prefs → 세션 캐시 → 최근 분석 기록 순으로 심박·HRV를 찾는다. */
    private int[] resolveHrvMetrics() {
        int bpm = LastEcgResult.lastHrBpm;
        int hrv = LastEcgResult.lastHrvMs;
        if (bpm <= 0 && hrv <= 0) {
            SessionManager sm = new SessionManager(this);
            bpm = sm.getLastHrBpm();
            hrv = sm.getLastHrvMs();
        }
        if (bpm <= 0 && hrv <= 0) {
            org.json.JSONObject latest = HistoryStore.latestAnalysis(this);
            if (latest != null) {
                bpm = latest.optInt("bpm", 0);
                hrv = latest.optInt("hrvMs", 0);
            }
        }
        return new int[]{bpm, hrv};
    }

    private RussellEmotionCalculator.Point buildPredictedPoint() {
        if (getIntent().hasExtra(EXTRA_PREDICTED_VALENCE)
                && getIntent().hasExtra(EXTRA_PREDICTED_AROUSAL)) {
            return new RussellEmotionCalculator.Point(
                    getIntent().getFloatExtra(EXTRA_PREDICTED_VALENCE, 0f),
                    getIntent().getFloatExtra(EXTRA_PREDICTED_AROUSAL, 0f));
        }
        if (isReentry()) {
            SessionManager sm = new SessionManager(this);
            return new RussellEmotionCalculator.Point(
                    sm.getLastPredictionValence(), sm.getLastPredictionArousal());
        }
        if (!LastEcgResult.hasValid()) {
            return RussellEmotionCalculator.fromHrvStress(40);
        }
        return RussellEmotionCalculator.fromHrvStress(
                LastEcgResult.lastStressScore,
                LastEcgResult.lastHrvMs,
                LastEcgResult.lastHrBpm);
    }

    private boolean isReentry() {
        return getIntent().getBooleanExtra(EXTRA_REENTRY, false);
    }

    private void stylePrimaryButton(Button btn) {
        btn.setBackgroundResource(R.drawable.bg_btn_primary);
        btn.setTextColor(getColor(R.color.white));
        btn.setBackgroundTintList(null);
    }

    private boolean isAdditional() {
        return getIntent().getBooleanExtra(EXTRA_ADDITIONAL, false);
    }

    private void goFeedback(String choice) {
        Intent i = new Intent(this, FeedbackActivity.class);
        i.putExtra(FeedbackActivity.EXTRA_CHOICE, choice);
        i.putExtra(FeedbackActivity.EXTRA_ADDITIONAL, isAdditional());
        if (getIntent().getBooleanExtra(EXTRA_FROM_HRV, false)) {
            i.putExtra(FeedbackActivity.EXTRA_FROM_HRV, true);
        }
        startActivity(i);
        finish();
    }
}
