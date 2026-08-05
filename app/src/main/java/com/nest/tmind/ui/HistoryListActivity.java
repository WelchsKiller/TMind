package com.nest.tmind.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nest.tmind.R;
import com.nest.tmind.util.EmaQuestionBank;
import com.nest.tmind.util.HistoryStore;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 이전 설문 / 이전 분석 — 시니어 친화 카드 UI */
public class HistoryListActivity extends BaseSeniorActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SURVEY = "survey";
    public static final String MODE_ANALYSIS = "analysis";

    private boolean surveyMode;
    private long selectedDayStart;
    private LinearLayout container;
    private TextView tvEmpty;
    private TextView tvDateChip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_list);

        surveyMode = MODE_SURVEY.equals(getIntent().getStringExtra(EXTRA_MODE));
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(surveyMode ? R.string.history_survey_title : R.string.history_analysis_title);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        setupTtsFromViews(R.id.btnTts, R.id.tvTitle);

        container = findViewById(R.id.listContainer);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvDateChip = findViewById(R.id.tvDateChip);

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        selectedDayStart = c.getTimeInMillis();

        if (tvDateChip != null) {
            refreshDateChip();
            tvDateChip.setOnClickListener(v -> openDatePicker());
        }

        bindList();
    }

    private void openDatePicker() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(selectedDayStart);
        new DatePickerDialog(this, (view, y, m, d) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(y, m, d, 0, 0, 0);
            picked.set(Calendar.MILLISECOND, 0);
            selectedDayStart = picked.getTimeInMillis();
            refreshDateChip();
            bindList();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void refreshDateChip() {
        if (tvDateChip == null) return;
        SimpleDateFormat fmt = new SimpleDateFormat("📅 yyyy년 M월 d일", Locale.KOREA);
        tvDateChip.setText(fmt.format(new Date(selectedDayStart)));
    }

    private void bindList() {
        container.removeAllViews();
        List<JSONObject> all = surveyMode
                ? HistoryStore.loadEma(this)
                : HistoryStore.loadAnalysis(this);

        long dayEnd = selectedDayStart + 24L * 60L * 60L * 1000L;
        List<JSONObject> dayRows = new ArrayList<>();
        for (JSONObject row : all) {
            long ts = row.optLong("ts", 0);
            if (ts >= selectedDayStart && ts < dayEnd) dayRows.add(row);
        }

        // 날짜 칩이 없으면(구레이아웃) 전체 표시
        if (tvDateChip == null) {
            dayRows = all;
        }

        if (dayRows.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        SimpleDateFormat timeFmt = new SimpleDateFormat("a h:mm", Locale.KOREA);
        SimpleDateFormat fullFmt = new SimpleDateFormat("yyyy.MM.dd(E) HH:mm", Locale.KOREA);
        float d = getResources().getDisplayMetrics().density;
        Map<String, String> labelMap = buildKeyLabels();

        for (JSONObject row : dayRows) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (12 * d);
            card.setLayoutParams(lp);
            card.setBackgroundResource(R.drawable.bg_metric_card);
            card.setPadding((int) (16 * d), (int) (14 * d), (int) (16 * d), (int) (14 * d));

            TextView tv = new TextView(this);
            tv.setTextSize(16f);
            tv.setTextColor(getColor(R.color.text_primary));
            tv.setLineSpacing(4 * d, 1f);
            tv.setText(surveyMode
                    ? formatSurveyFriendly(row, timeFmt, labelMap)
                    : formatAnalysisFriendly(row, fullFmt));
            card.addView(tv);

            if (surveyMode) {
                TextView btnAll = new TextView(this);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                blp.topMargin = (int) (12 * d);
                btnAll.setLayoutParams(blp);
                btnAll.setGravity(android.view.Gravity.CENTER);
                btnAll.setBackgroundResource(R.drawable.bg_btn_outline);
                btnAll.setPadding((int) (12 * d), (int) (12 * d), (int) (12 * d), (int) (12 * d));
                btnAll.setText(R.string.view_all_responses);
                btnAll.setTextColor(getColor(R.color.teal_dark));
                btnAll.setTextSize(16f);
                btnAll.setTypeface(null, android.graphics.Typeface.BOLD);
                long rowTs = row.optLong("ts", 0);
                View.OnClickListener openDetail = v -> {
                    android.content.Intent i = new android.content.Intent(this, SurveyDetailActivity.class);
                    i.putExtra(SurveyDetailActivity.EXTRA_TS, rowTs);
                    startActivity(i);
                };
                btnAll.setOnClickListener(openDetail);
                card.setOnClickListener(openDetail);
                card.addView(btnAll);
            }

            container.addView(card);
        }
    }

    private Map<String, String> buildKeyLabels() {
        Map<String, String> map = new HashMap<>();
        for (EmaQuestionBank.SessionType t : EmaQuestionBank.SessionType.values()) {
            for (EmaQuestionBank.Item item : EmaQuestionBank.itemsFor(t)) {
                map.put(item.key, item.prompt);
            }
        }
        return map;
    }

    private String formatSurveyFriendly(JSONObject row, SimpleDateFormat timeFmt, Map<String, String> labels) {
        StringBuilder sb = new StringBuilder();
        long ts = row.optLong("ts", 0);
        String session = row.optString("sessionType", "-");
        String sessionKo = "MORNING".equals(session) ? "오전 설문"
                : "AFTERNOON".equals(session) ? "오후 설문"
                : "EVENT".equals(session) ? "추가 설문" : session;
        sb.append(timeFmt.format(new Date(ts))).append(" · ").append(sessionKo).append('\n');

        JSONObject answers = row.optJSONObject("answers");
        if (answers == null) answers = row;
        int answered = 0;
        Iterator<String> keys = answers.keys();
        List<String> lines = new ArrayList<>();
        while (keys.hasNext()) {
            String k = keys.next();
            if ("sessionType".equals(k) || "feedbackReselect".equals(k) || "ts".equals(k) || "answers".equals(k)) {
                continue;
            }
            int score = answers.optInt(k, 0);
            if (score <= 0) continue;
            answered++;
            String q = labels.containsKey(k) ? labels.get(k) : k;
            String scale = scaleLabelFor(k, score, labels);
            // 이모지: 점수 5=첫 옵션 … 1=마지막
            int displayIdx = Math.max(0, Math.min(4, 5 - score));
            String emoji = "😐";
            EmaQuestionBank.Item matched = findItem(k);
            if (matched != null) {
                String[] em = EmaQuestionBank.emojisFor(matched);
                emoji = em[displayIdx];
                scale = matched.scaleLabels[displayIdx];
            }
            lines.add(answered + ". " + shortQuestion(q) + "\n   " + emoji + "  " + scale);
        }
        sb.append("✔ ").append(getString(R.string.survey_done_badge, answered)).append('\n');
        int show = Math.min(lines.size(), 4);
        for (int i = 0; i < show; i++) {
            sb.append(lines.get(i)).append('\n');
        }
        if (lines.size() > show) {
            sb.append("… 외 ").append(lines.size() - show).append("문항");
        }
        return sb.toString().trim();
    }

    private String shortQuestion(String q) {
        if (q == null) return "";
        if (q.length() <= 28) return q;
        return q.substring(0, 28) + "…";
    }

    private EmaQuestionBank.Item findItem(String key) {
        for (EmaQuestionBank.SessionType t : EmaQuestionBank.SessionType.values()) {
            for (EmaQuestionBank.Item item : EmaQuestionBank.itemsFor(t)) {
                if (item.key.equals(key)) return item;
            }
        }
        return null;
    }

    private String scaleLabelFor(String key, int score, Map<String, String> labels) {
        EmaQuestionBank.Item item = findItem(key);
        if (item == null) return String.valueOf(score);
        int idx = Math.max(0, Math.min(4, 5 - score));
        return item.scaleLabels[idx];
    }

    private String formatAnalysisFriendly(JSONObject row, SimpleDateFormat fmt) {
        long ts = row.optLong("ts", 0);
        int bpm = row.optInt("bpm", 0);
        int hrv = row.optInt("hrvMs", 0);
        StringBuilder sb = new StringBuilder();
        sb.append(fmt.format(new Date(ts))).append('\n');
        if (row.has("valence") && row.has("arousal")) {
            sb.append("정서 위치 기록됨").append('\n');
        }
        sb.append("심박수 ").append(bpm > 0 ? bpm + "회/분" : "--")
                .append(" · 심박변이도 ").append(hrv > 0 ? hrv + " ms" : "--");
        return sb.toString();
    }
}
