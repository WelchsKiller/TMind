package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nest.tmind.R;
import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.util.HistoryStore;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 분석 결과 (수정안 UI) */
public class AnalysisResultActivity extends BaseSeniorActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_result);

        TextView tvResult = findViewById(R.id.tvResult);
        TextView tvBpm = findViewById(R.id.tvBpm);
        TextView tvHrv = findViewById(R.id.tvHrv);

        String message = buildEmotionText();
        tvResult.setText(message);

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

        if (savedInstanceState == null) {
            long at = LastEcgResult.measuredAtMs > 0
                    ? LastEcgResult.measuredAtMs
                    : System.currentTimeMillis();
            HistoryStore.addAnalysis(this, message, bpm, hrv, at);
        }

        bindPreviousAnalysis();

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        setupTtsFromViews(R.id.btnTts, R.id.tvResult, R.id.tvDisclaimer, R.id.tvFeedbackQuestion);

        findViewById(R.id.btnViewAllAnalysis).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryListActivity.class)
                        .putExtra(HistoryListActivity.EXTRA_MODE, HistoryListActivity.MODE_ANALYSIS)));

        Button btnYes = findViewById(R.id.btnYes);
        Button btnNo = findViewById(R.id.btnNo);
        Button btnUnknown = findViewById(R.id.btnUnknown);
        stylePrimaryButton(btnYes);
        stylePrimaryButton(btnNo);
        stylePrimaryButton(btnUnknown);

        btnYes.setOnClickListener(v -> goFeedback("agree"));
        btnNo.setOnClickListener(v -> {
            Intent i = new Intent(this, EmaSurveyActivity.class);
            i.putExtra(EmaSurveyActivity.EXTRA_FEEDBACK_RESELECT, true);
            startActivity(i);
            finish();
        });
        btnUnknown.setOnClickListener(v -> goFeedback("unknown"));
    }

    private void bindPreviousAnalysis() {
        LinearLayout prevList = findViewById(R.id.prevList);
        TextView tvEmpty = findViewById(R.id.tvPrevEmpty);
        prevList.removeAllViews();

        List<JSONObject> all = HistoryStore.loadAnalysis(this);
        int start = all.isEmpty() ? 0 : 1;
        if (start >= all.size()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy.MM.dd(E) HH:mm", Locale.KOREA);
        float d = getResources().getDisplayMetrics().density;
        int maxShow = Math.min(all.size(), start + 3);

        for (int i = start; i < maxShow; i++) {
            JSONObject row = all.get(i);
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (10 * d);
            tv.setLayoutParams(lp);
            tv.setBackgroundResource(R.drawable.bg_metric_card);
            tv.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (12 * d));
            tv.setTextSize(15f);
            tv.setTextColor(getColor(R.color.text_primary));
            tv.setLineSpacing(3 * d, 1f);
            long ts = row.optLong("ts", 0);
            int b = row.optInt("bpm", 0);
            int h = row.optInt("hrvMs", 0);
            tv.setText(fmt.format(new Date(ts)) + "\n"
                    + row.optString("message", "") + "\n"
                    + "심박수 " + (b > 0 ? b + "회/분" : "--")
                    + " · 심박변이도 " + (h > 0 ? h + " ms" : "--"));
            tv.setOnClickListener(v ->
                    startActivity(new Intent(this, HistoryListActivity.class)
                            .putExtra(HistoryListActivity.EXTRA_MODE, HistoryListActivity.MODE_ANALYSIS)));
            prevList.addView(tv);
        }
    }

    private void stylePrimaryButton(Button btn) {
        btn.setBackgroundResource(R.drawable.bg_btn_primary);
        btn.setTextColor(getColor(R.color.white));
        btn.setBackgroundTintList(null);
    }

    private String buildEmotionText() {
        if (!LastEcgResult.hasValid()) {
            return getString(R.string.result_default);
        }
        int stress = LastEcgResult.lastStressScore;
        if (stress < 40) return getString(R.string.result_comfortable);
        if (stress < 70) return getString(R.string.result_moderate);
        return getString(R.string.result_tense);
    }

    private void goFeedback(String choice) {
        Intent i = new Intent(this, FeedbackActivity.class);
        i.putExtra(FeedbackActivity.EXTRA_CHOICE, choice);
        startActivity(i);
        finish();
    }
}
