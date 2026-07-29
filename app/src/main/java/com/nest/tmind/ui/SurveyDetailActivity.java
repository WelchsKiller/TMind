package com.nest.tmind.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nest.tmind.R;
import com.nest.tmind.util.EmaQuestionBank;
import com.nest.tmind.util.HistoryStore;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 이전 설문 — 전체 문항·응답 상세 */
public class SurveyDetailActivity extends BaseSeniorActivity {

    public static final String EXTRA_TS = "survey_ts";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_survey_detail);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        long ts = getIntent().getLongExtra(EXTRA_TS, 0L);
        JSONObject row = HistoryStore.findEmaByTs(this, ts);
        TextView tvHeader = findViewById(R.id.tvHeader);
        LinearLayout container = findViewById(R.id.listContainer);

        if (row == null) {
            tvHeader.setText(R.string.history_empty);
            setupTtsFromViews(R.id.btnTts, R.id.tvTitle);
            return;
        }

        String session = row.optString("sessionType", "");
        String sessionKo = sessionLabel(session);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy.MM.dd(E) a h:mm", Locale.KOREA);
        tvHeader.setText(fmt.format(new Date(row.optLong("ts", ts))) + " · " + sessionKo);

        JSONObject answers = row.optJSONObject("answers");
        if (answers == null) answers = row;

        Map<String, Integer> ordered = orderedAnswers(session, answers);
        float d = getResources().getDisplayMetrics().density;
        int n = 0;
        StringBuilder speakBuf = new StringBuilder();
        speakBuf.append(tvHeader.getText()).append(". ");

        for (Map.Entry<String, Integer> e : ordered.entrySet()) {
            n++;
            EmaQuestionBank.Item item = findItem(e.getKey());
            String prompt = item != null ? item.prompt : e.getKey();
            int score = e.getValue();
            int displayIdx = Math.max(0, Math.min(4, 5 - score));
            String scale = item != null ? item.scaleLabels[displayIdx] : String.valueOf(score);
            int emojiRes = item != null
                    ? EmaQuestionBank.drawableResFor(item, displayIdx)
                    : R.drawable.emoji_3;

            LinearLayout rowView = new LinearLayout(this);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (10 * d);
            rowView.setLayoutParams(lp);
            rowView.setBackgroundResource(R.drawable.bg_metric_card);
            rowView.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (12 * d));

            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams((int) (40 * d), (int) (40 * d));
            iv.setLayoutParams(ilp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageResource(emojiRes);

            TextView tv = new TextView(this);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tlp.setMarginStart((int) (12 * d));
            tv.setLayoutParams(tlp);
            tv.setTextSize(16f);
            tv.setTextColor(getColor(R.color.text_primary));
            tv.setLineSpacing(3 * d, 1f);
            tv.setText(n + ". " + prompt + "\n→ " + scale);

            rowView.addView(iv);
            rowView.addView(tv);
            container.addView(rowView);

            speakBuf.append(n).append("번. ").append(prompt).append(". ").append(scale).append(". ");
        }

        TextView badge = new TextView(this);
        badge.setText(getString(R.string.survey_done_badge, n));
        badge.setTextColor(getColor(R.color.teal_primary));
        badge.setTextSize(16f);
        badge.setPadding(0, (int) (8 * d), 0, (int) (8 * d));
        container.addView(badge, 0);

        final String speakText = speakBuf.toString();
        findViewById(R.id.btnTts).setOnClickListener(v -> {
            if (this.tts != null) this.tts.speak(speakText);
        });
    }

    private static String sessionLabel(String session) {
        if ("MORNING".equals(session)) return "오전 설문";
        if ("AFTERNOON".equals(session)) return "오후 설문";
        if ("EVENT".equals(session)) return "추가 설문";
        return session.isEmpty() ? "설문" : session;
    }

    /** 세션 문항 순서 우선, 없으면 answers 키 순서 */
    private static Map<String, Integer> orderedAnswers(String sessionType, JSONObject answers) {
        Map<String, Integer> out = new LinkedHashMap<>();
        EmaQuestionBank.SessionType type;
        try {
            type = EmaQuestionBank.SessionType.valueOf(sessionType);
        } catch (Exception e) {
            type = null;
        }
        if (type != null) {
            for (EmaQuestionBank.Item item : EmaQuestionBank.itemsFor(type)) {
                if (answers.has(item.key)) {
                    int s = answers.optInt(item.key, 0);
                    if (s > 0) out.put(item.key, s);
                }
            }
        }
        Iterator<String> keys = answers.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if ("sessionType".equals(k) || "feedbackReselect".equals(k)
                    || "ts".equals(k) || "answers".equals(k)) continue;
            if (out.containsKey(k)) continue;
            int s = answers.optInt(k, 0);
            if (s > 0) out.put(k, s);
        }
        return out;
    }

    private static EmaQuestionBank.Item findItem(String key) {
        for (EmaQuestionBank.SessionType t : EmaQuestionBank.SessionType.values()) {
            for (EmaQuestionBank.Item item : EmaQuestionBank.itemsFor(t)) {
                if (item.key.equals(key)) return item;
            }
        }
        return null;
    }
}
