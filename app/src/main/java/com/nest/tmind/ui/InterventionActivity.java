package com.nest.tmind.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nest.tmind.R;
import com.nest.tmind.util.DataQueueManager;
import com.nest.tmind.util.InterventionClassifier;
import com.nest.tmind.util.VibrationHelper;

import org.json.JSONObject;

import java.util.List;

/**
 * 분석 결과 기반 맞춤 개입: 심호흡·명상·요가·스트레스 관리 영상 등.
 */
public class InterventionActivity extends BaseSeniorActivity {

    // 공개 안내 영상 (연구용 플레이스홀더 — 실제 배포 시 교체)
    private static final String URL_STRESS =
            "https://www.youtube.com/results?search_query=%EC%8A%A4%ED%8A%B8%EB%A0%88%EC%8A%A4+%EA%B4%80%EB%A6%AC+%ED%98%B8%ED%9D%A1";
    private static final String URL_MEDITATION =
            "https://www.youtube.com/results?search_query=%EB%AA%85%EC%83%81+%EC%95%88%EB%82%B4";
    private static final String URL_YOGA =
            "https://www.youtube.com/results?search_query=%EC%B4%88%EB%B3%B4%EC%9E%90+%EC%9A%94%EA%B0%80";
    private static final String URL_STRETCH =
            "https://www.youtube.com/results?search_query=%EC%8A%A4%ED%8A%B8%EB%A0%88%EC%B9%AD+%EC%9A%B4%EB%8F%99";
    private static final String URL_SLEEP_MUSIC =
            "https://www.youtube.com/results?search_query=%EC%88%98%EB%A9%B4+%EC%9C%A0%EB%8F%84+%EC%9D%8C%EC%95%85";
    private static final String URL_RELAX_MUSIC =
            "https://www.youtube.com/results?search_query=%EC%9D%B4%EC%99%84+%EC%9D%8C%EC%95%85";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intervention);

        InterventionClassifier.Abnormality type = InterventionClassifier.classify();
        TextView tvType = findViewById(R.id.tvType);
        tvType.setText(typeLabel(type));

        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvType, R.id.tvHint);
        findViewById(R.id.btnBack).setOnClickListener(v -> goHome());
        Button btnHome = findViewById(R.id.btnHome);
        btnHome.setBackgroundTintList(null);
        btnHome.setOnClickListener(v -> goHome());

        LinearLayout list = findViewById(R.id.cardList);
        List<InterventionClassifier.Recommendation> recs =
                InterventionClassifier.recommendationsFor(type);
        float d = getResources().getDisplayMetrics().density;

        for (InterventionClassifier.Recommendation rec : recs) {
            View card = buildCard(rec, d);
            list.addView(card);
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("abnormality", type.name());
            payload.put("count", recs.size());
            payload.put("ts", System.currentTimeMillis());
            new DataQueueManager(this).enqueue("intervention_shown", payload);
            new DataQueueManager(this).flushIfOnline();
        } catch (Exception ignored) {
        }
    }

    private View buildCard(InterventionClassifier.Recommendation rec, float d) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (12 * d);
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_metric_card);
        card.setPadding((int) (16 * d), (int) (14 * d), (int) (16 * d), (int) (14 * d));

        TextView title = new TextView(this);
        title.setText(stringByName(rec.titleResKey));
        title.setTextColor(getColor(R.color.black));
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView desc = new TextView(this);
        desc.setText(stringByName(rec.descResKey));
        desc.setTextColor(getColor(R.color.text_secondary));
        desc.setTextSize(14f);
        desc.setPadding(0, (int) (6 * d), 0, (int) (10 * d));

        Button btn = new Button(this);
        btn.setText(R.string.interv_start);
        btn.setBackgroundResource(R.drawable.bg_btn_outline);
        btn.setBackgroundTintList(null);
        btn.setTextColor(getColor(R.color.teal_dark));
        btn.setOnClickListener(v -> startContent(rec));

        card.addView(title);
        card.addView(desc);
        card.addView(btn);
        return card;
    }

    private void startContent(InterventionClassifier.Recommendation rec) {
        switch (rec.content) {
            case BREATHING:
                startActivity(new Intent(this, BreathingGuideActivity.class));
                break;
            case MEDITATION:
                openUrl(URL_MEDITATION);
                break;
            case YOGA:
                openUrl(URL_YOGA);
                break;
            case STRESS_VIDEO:
                openUrl(URL_STRESS);
                break;
            case STRETCH_VIDEO:
                openUrl(URL_STRETCH);
                break;
            case MUSIC_RELAX:
                VibrationHelper.relaxPattern(this);
                openUrl(URL_RELAX_MUSIC);
                break;
            case SLEEP_MUSIC:
                VibrationHelper.softSleepPattern(this);
                openUrl(URL_SLEEP_MUSIC);
                break;
            case VIBRATION:
                if ("interv_soft_vib_title".equals(rec.titleResKey)) {
                    VibrationHelper.softSleepPattern(this);
                } else {
                    VibrationHelper.relaxPattern(this);
                }
                Toast.makeText(this, R.string.interv_vib_playing, Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.interv_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private String typeLabel(InterventionClassifier.Abnormality type) {
        switch (type) {
            case STRESS:
                return getString(R.string.interv_type_stress);
            case FATIGUE_INSOMNIA:
                return getString(R.string.interv_type_fatigue);
            case HRV_DECREASE:
                return getString(R.string.interv_type_hrv);
            case LOW_ACTIVITY:
                return getString(R.string.interv_type_activity);
            case AUTONOMIC_IMBALANCE:
                return getString(R.string.interv_type_ans);
            default:
                return getString(R.string.interv_type_balanced);
        }
    }

    private String stringByName(String name) {
        int id = getResources().getIdentifier(name, "string", getPackageName());
        return id != 0 ? getString(id) : name;
    }

    private void goHome() {
        Intent i = new Intent(this, DashboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
