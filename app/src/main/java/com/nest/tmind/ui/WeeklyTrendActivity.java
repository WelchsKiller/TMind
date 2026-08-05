package com.nest.tmind.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nest.tmind.R;
import com.nest.tmind.util.HistoryStore;
import com.nest.tmind.util.RussellEmotionCalculator;
import com.nest.tmind.view.RussellCircumplexView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 연구 종료 시 7일 정서 추이 (Russell 사분면, 언어 라벨 없음) */
public class WeeklyTrendActivity extends BaseSeniorActivity {

    private static final int[] DAY_COLORS = {
            0x552F9E7A, 0x662F9E7A, 0x772F9E7A, 0x882F9E7A,
            0x992F9E7A, 0xBB2F9E7A, 0xFF2F9E7A
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weekly_trend);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvSubtitle, R.id.tvLegend);

        RussellCircumplexView russell = findViewById(R.id.russellView);
        LinearLayout dayList = findViewById(R.id.dayList);
        TextView tvEmpty = findViewById(R.id.tvEmpty);

        List<JSONObject> days = HistoryStore.loadDailyLatestAnalysis(this, 7);
        if (days.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        float[] vs = new float[days.size()];
        float[] as = new float[days.size()];
        int[] colors = new int[days.size()];
        SimpleDateFormat fmt = new SimpleDateFormat("M/d (E)", Locale.KOREA);
        float d = getResources().getDisplayMetrics().density;

        for (int i = 0; i < days.size(); i++) {
            JSONObject row = days.get(i);
            float v = (float) row.optDouble("valence", Double.NaN);
            float a = (float) row.optDouble("arousal", Double.NaN);
            if (Double.isNaN(v) || Double.isNaN(a)) {
                int stressProxy = estimateStress(row.optInt("hrvMs", 0));
                RussellEmotionCalculator.Point p = RussellEmotionCalculator.fromHrvStress(
                        stressProxy, row.optInt("hrvMs", 0), row.optInt("bpm", 0));
                v = p.valence;
                a = p.arousal;
            }
            vs[i] = v;
            as[i] = a;
            int dayIdx = Math.min(DAY_COLORS.length - 1, row.optInt("dayIndex", i));
            colors[i] = DAY_COLORS[Math.max(0, dayIdx)];

            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (8 * d);
            tv.setLayoutParams(lp);
            tv.setBackgroundResource(R.drawable.bg_metric_card);
            tv.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (12 * d));
            tv.setTextSize(15f);
            tv.setTextColor(getColor(R.color.text_primary));
            long ts = row.optLong("ts", row.optLong("dayStart", 0));
            int bpm = row.optInt("bpm", 0);
            int hrv = row.optInt("hrvMs", 0);
            tv.setText(fmt.format(new Date(ts))
                    + "  ·  심박수 " + (bpm > 0 ? bpm + "회/분" : "--")
                    + "  ·  HRV " + (hrv > 0 ? hrv + " ms" : "--"));
            dayList.addView(tv);
        }

        russell.setTrail(vs, as, colors);
        if (vs.length > 0) {
            russell.setPoint(vs[vs.length - 1], as[as.length - 1]);
        }
    }

    private static int estimateStress(int hrvMs) {
        if (hrvMs <= 0) return 50;
        float hrvForScore = Math.max(20f, Math.min(160f, hrvMs));
        return Math.round(((160f - hrvForScore) / 140f) * 100f);
    }
}
