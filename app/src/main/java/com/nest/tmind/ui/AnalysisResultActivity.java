package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.nest.tmind.R;
import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.util.HistoryStore;
import com.nest.tmind.util.RussellEmotionCalculator;
import com.nest.tmind.view.RussellCircumplexView;

/** 분석 결과 — HRV 예측을 Russell 사분면으로 표시 (언어 라벨 없음) */
public class AnalysisResultActivity extends BaseSeniorActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_result);

        TextView tvBpm = findViewById(R.id.tvBpm);
        TextView tvHrv = findViewById(R.id.tvHrv);
        RussellCircumplexView russell = findViewById(R.id.russellView);

        RussellEmotionCalculator.Point point = buildPredictedPoint();
        if (point != null) {
            russell.setPoint(point.valence, point.arousal);
        }

        int bpm = 0;
        int hrv = 0;
        if (LastEcgResult.hasValid()) {
            bpm = LastEcgResult.lastHrBpm;
            hrv = LastEcgResult.lastHrvMs;
            if (bpm > 0 && (bpm < 40 || bpm > 180)) {
                tvBpm.setText("-- 회/분");
            } else if (bpm > 0) {
                tvBpm.setText(getString(R.string.bpm_unit_format, bpm));
            } else {
                tvBpm.setText("-- 회/분");
            }
            tvHrv.setText(hrv > 0 ? getString(R.string.hrv_unit_format, hrv) : "-- ms");
        } else {
            tvBpm.setText("-- 회/분");
            tvHrv.setText("-- ms");
        }

        if (savedInstanceState == null && point != null) {
            long at = LastEcgResult.measuredAtMs > 0
                    ? LastEcgResult.measuredAtMs
                    : System.currentTimeMillis();
            HistoryStore.addAnalysis(this, "", bpm, hrv, at, point.valence, point.arousal);
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
            startActivity(new Intent(this, EmotionEditActivity.class));
            finish();
        });
        btnUnknown.setOnClickListener(v -> goFeedback("unknown"));
    }

    private RussellEmotionCalculator.Point buildPredictedPoint() {
        if (!LastEcgResult.hasValid()) {
            return RussellEmotionCalculator.fromHrvStress(40);
        }
        return RussellEmotionCalculator.fromHrvStress(
                LastEcgResult.lastStressScore,
                LastEcgResult.lastHrvMs,
                LastEcgResult.lastHrBpm);
    }

    private void stylePrimaryButton(Button btn) {
        btn.setBackgroundResource(R.drawable.bg_btn_primary);
        btn.setTextColor(getColor(R.color.white));
        btn.setBackgroundTintList(null);
    }

    private void goFeedback(String choice) {
        Intent i = new Intent(this, FeedbackActivity.class);
        i.putExtra(FeedbackActivity.EXTRA_CHOICE, choice);
        startActivity(i);
        finish();
    }
}
