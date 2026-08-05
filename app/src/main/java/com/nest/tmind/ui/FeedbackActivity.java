package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.nest.tmind.R;
import com.nest.tmind.util.DataQueueManager;
import com.nest.tmind.util.MissionManager;

import org.json.JSONObject;

/** APP-USR-007: 예측 피드백 후 맞춤 개입 유도 */
public class FeedbackActivity extends BaseSeniorActivity {

    public static final String EXTRA_CHOICE = "choice";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        String choice = getIntent().getStringExtra(EXTRA_CHOICE);
        TextView tvDone = findViewById(R.id.tvDone);

        try {
            JSONObject payload = new JSONObject();
            payload.put("feedback", choice != null ? choice : "unknown");
            payload.put("ts", System.currentTimeMillis());
            new DataQueueManager(this).enqueue("feedback", payload);
            new DataQueueManager(this).flushIfOnline();
        } catch (Exception ignored) {
        }

        if (new MissionManager(this).isAllDone()) {
            tvDone.setText(R.string.mission_complete);
        }

        setupTtsButton(R.id.btnTts, tvDone.getText().toString());

        Button btnIntervention = findViewById(R.id.btnIntervention);
        btnIntervention.setBackgroundTintList(null);
        btnIntervention.setOnClickListener(v -> {
            startActivity(new Intent(this, InterventionActivity.class));
            finish();
        });

        Button btnHome = findViewById(R.id.btnHome);
        btnHome.setBackgroundTintList(null);
        btnHome.setOnClickListener(v -> {
            Intent i = new Intent(this, DashboardActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        });
    }
}
