package com.nest.tmind.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.nest.tmind.R;
import com.nest.tmind.util.EmaQuestionBank;

/** 설문 시작 전 안내 (오전/오후/추가) — 핵심 문구 청록·굵게 강조 */
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

        int bodyRes;
        switch (type) {
            case AFTERNOON:
                bodyRes = R.string.ema_intro_body_afternoon;
                break;
            case EVENT:
                bodyRes = R.string.ema_intro_body_extra;
                break;
            default:
                bodyRes = R.string.ema_intro_body_morning;
                break;
        }

        TextView tvIntro = findViewById(R.id.tvIntro);
        tvIntro.setText(buildHighlightedIntro(getString(bodyRes, count), count));
        setupTtsFromViews(R.id.btnTts, R.id.tvLead, R.id.tvIntro);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnStart).setOnClickListener(v -> {
            Intent i = new Intent(this, EmaSurveyActivity.class);
            i.putExtra(EmaSurveyActivity.EXTRA_SESSION_TYPE, type.name());
            startActivity(i);
            finish();
        });
    }

    private CharSequence buildHighlightedIntro(String full, int count) {
        SpannableString ss = new SpannableString(full);
        int color = ContextCompat.getColor(this, R.color.teal_primary);
        highlight(ss, count + "문항", color);
        highlight(ss, "'매우 많이 있었다'", color);
        highlight(ss, "'전혀 없었다'", color);
        highlight(ss, "5개 응답", color);
        return ss;
    }

    private static void highlight(SpannableString ss, String target, int color) {
        int start = ss.toString().indexOf(target);
        if (start < 0) return;
        int end = start + target.length();
        ss.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
