package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.nest.tmind.R;
import com.nest.tmind.util.EmaQuestionBank;

/** 설문 시작 전 안내 (문항 수는 세션별 동적) */
public class EmaIntroActivity extends BaseSeniorActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ema_intro);

        String typeExtra = getIntent().getStringExtra(EmaSurveyActivity.EXTRA_SESSION_TYPE);
        EmaQuestionBank.SessionType type;
        if (typeExtra != null) {
            type = EmaQuestionBank.SessionType.valueOf(typeExtra);
        } else {
            type = EmaQuestionBank.dailyTypeNow();
        }
        int count = EmaQuestionBank.itemsFor(type).length;
        String body = getString(R.string.ema_intro_body, count);

        TextView tvIntro = findViewById(R.id.tvIntro);
        tvIntro.setText(body);
        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvIntro);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnStart).setOnClickListener(v -> {
            Intent i = new Intent(this, EmaSurveyActivity.class);
            i.putExtra(EmaSurveyActivity.EXTRA_SESSION_TYPE, type.name());
            startActivity(i);
            finish();
        });
    }
}
