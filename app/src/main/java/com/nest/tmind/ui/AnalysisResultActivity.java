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

/** APP-USR-006: 정서상태 예측 결과 + 이전 분석 결과 표시 */
public class AnalysisResultActivity extends BaseSeniorActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_result);

        TextView tvResult = findViewById(R.id.tvResult);
        TextView tvBpm = findViewById(R.id.tvBpm);

        String message = buildEmotionText();
        tvResult.setText(message);
        int bpm = 0;
        int hrv = 0;
        if (LastEcgResult.hasValid()) {
            bpm = LastEcgResult.lastHrBpm;
            hrv = LastEcgResult.lastHrvMs;
            if (bpm > 0 && (bpm < 40 || bpm > 180)) {
                tvBpm.setText("BPM --");
            } else {
                tvBpm.setText("BPM " + bpm);
            }
        } else {
            tvBpm.setVisibility(View.GONE);
        }
        if (savedInstanceState == null) {
            long at = LastEcgResult.measuredAtMs > 0
                    ? LastEcgResult.measuredAtMs
                    : System.currentTimeMillis();
            HistoryStore.addAnalysis(this, message, bpm, hrv, at);
        }

        bindPreviousAnalysis();

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

    /** 현재 방금 저장한 항목(index 0)을 제외한 이전 기록 표시 */
    private void bindPreviousAnalysis() {
        LinearLayout prevList = findViewById(R.id.prevList);
        TextView tvEmpty = findViewById(R.id.tvPrevEmpty);
        prevList.removeAllViews();

        List<JSONObject> all = HistoryStore.loadAnalysis(this);
        // 0번은 방금 저장한 현재 결과 → 1번부터가 이전
        int start = all.isEmpty() ? 0 : 1;
        if (start >= all.size()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy.MM.dd(E) HH:mm", Locale.KOREA);
        float d = getResources().getDisplayMetrics().density;
        int maxShow = Math.min(all.size(), start + 5); // 최근 이전 5건

        for (int i = start; i < maxShow; i++) {
            JSONObject row = all.get(i);
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (10 * d);
            tv.setLayoutParams(lp);
            tv.setBackgroundResource(R.drawable.bg_info_box);
            tv.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (12 * d));
            tv.setTextSize(16f);
            tv.setTextColor(getColor(R.color.text_primary));
            tv.setLineSpacing(3 * d, 1f);
            long ts = row.optLong("ts", 0);
            tv.setText(fmt.format(new Date(ts)) + "\n"
                    + row.optString("message", "") + "\n"
                    + "BPM " + row.optInt("bpm", 0)
                    + " · HRV " + row.optInt("hrvMs", 0) + " ms");
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
        if (stress < 40) {
            return getString(R.string.result_comfortable);
        } else if (stress < 70) {
            return getString(R.string.result_moderate);
        }
        return getString(R.string.result_tense);
    }

    private void goFeedback(String choice) {
        Intent i = new Intent(this, FeedbackActivity.class);
        i.putExtra(FeedbackActivity.EXTRA_CHOICE, choice);
        startActivity(i);
        finish();
    }
}
