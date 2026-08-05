package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.nest.tmind.R;
import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.util.DataQueueManager;
import com.nest.tmind.util.HistoryStore;
import com.nest.tmind.util.RussellEmotionCalculator;
import com.nest.tmind.view.RussellCircumplexView;

import org.json.JSONObject;

/** 예측과 다를 때 Russell 사분면에서 현재 감정 위치 수정 */
public class EmotionEditActivity extends BaseSeniorActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emotion_edit);

        RussellCircumplexView russell = findViewById(R.id.russellView);
        russell.setEditable(true);

        RussellEmotionCalculator.Point predicted = predictedPoint();
        if (predicted != null) {
            russell.setPoint(predicted.valence, predicted.arousal);
        }

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        Button btnConfirm = findViewById(R.id.btnConfirm);
        stylePrimary(btnConfirm);
        btnConfirm.setOnClickListener(v -> {
            if (!russell.hasPoint()) {
                Toast.makeText(this, R.string.emotion_edit_need_point, Toast.LENGTH_SHORT).show();
                return;
            }
            float valence = russell.getValence();
            float arousal = russell.getArousal();
            try {
                JSONObject payload = new JSONObject();
                payload.put("feedback", "disagree");
                payload.put("valence", valence);
                payload.put("arousal", arousal);
                payload.put("ts", System.currentTimeMillis());
                new DataQueueManager(this).enqueue("emotion_edit", payload);
                new DataQueueManager(this).flushIfOnline();
            } catch (Exception ignored) {
            }

            int bpm = LastEcgResult.lastHrBpm;
            int hrv = LastEcgResult.lastHrvMs;
            long at = LastEcgResult.measuredAtMs > 0
                    ? LastEcgResult.measuredAtMs : System.currentTimeMillis();
            HistoryStore.addAnalysis(this, "", bpm, hrv, at, valence, arousal);

            Intent i = new Intent(this, FeedbackActivity.class);
            i.putExtra(FeedbackActivity.EXTRA_CHOICE, "disagree");
            startActivity(i);
            finish();
        });
    }

    private RussellEmotionCalculator.Point predictedPoint() {
        if (!LastEcgResult.hasValid()) return null;
        return RussellEmotionCalculator.fromHrvStress(
                LastEcgResult.lastStressScore,
                LastEcgResult.lastHrvMs,
                LastEcgResult.lastHrBpm);
    }

    private void stylePrimary(Button btn) {
        btn.setBackgroundResource(R.drawable.bg_btn_primary);
        btn.setTextColor(getColor(R.color.white));
        btn.setBackgroundTintList(null);
    }
}
