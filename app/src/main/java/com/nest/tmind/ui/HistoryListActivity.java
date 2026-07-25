package com.nest.tmind.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nest.tmind.R;
import com.nest.tmind.util.HistoryStore;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** 이전 설문 응답 / 이전 분석 결과 목록 */
public class HistoryListActivity extends BaseSeniorActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SURVEY = "survey";
    public static final String MODE_ANALYSIS = "analysis";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_list);

        boolean survey = MODE_SURVEY.equals(getIntent().getStringExtra(EXTRA_MODE));
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(survey ? R.string.history_survey_title : R.string.history_analysis_title);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        setupTtsFromViews(R.id.btnTts, R.id.tvTitle);

        LinearLayout container = findViewById(R.id.listContainer);
        TextView tvEmpty = findViewById(R.id.tvEmpty);
        List<JSONObject> rows = survey ? HistoryStore.loadEma(this) : HistoryStore.loadAnalysis(this);

        if (rows.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy.MM.dd(E) HH:mm", Locale.KOREA);
        float d = getResources().getDisplayMetrics().density;

        for (JSONObject row : rows) {
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (12 * d);
            tv.setLayoutParams(lp);
            tv.setBackgroundResource(R.drawable.bg_info_box);
            tv.setPadding((int) (16 * d), (int) (14 * d), (int) (16 * d), (int) (14 * d));
            tv.setTextSize(16f);
            tv.setTextColor(getColor(R.color.text_primary));
            tv.setLineSpacing(4 * d, 1f);
            tv.setText(survey ? formatSurvey(row, fmt) : formatAnalysis(row, fmt));
            container.addView(tv);
        }
    }

    private String formatSurvey(JSONObject row, SimpleDateFormat fmt) {
        StringBuilder sb = new StringBuilder();
        long ts = row.optLong("ts", 0);
        sb.append(fmt.format(new Date(ts))).append('\n');
        sb.append("세션: ").append(row.optString("sessionType", "-")).append('\n');
        JSONObject answers = row.optJSONObject("answers");
        if (answers != null) {
            Iterator<String> keys = answers.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if ("sessionType".equals(k) || "feedbackReselect".equals(k)) continue;
                sb.append(k).append(": ").append(answers.opt(k)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String formatAnalysis(JSONObject row, SimpleDateFormat fmt) {
        long ts = row.optLong("ts", 0);
        return fmt.format(new Date(ts)) + "\n"
                + row.optString("message", "") + "\n"
                + "BPM " + row.optInt("bpm", 0)
                + " · HRV " + row.optInt("hrvMs", 0) + " ms";
    }
}
