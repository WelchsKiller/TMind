package com.nest.tmind.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import com.nest.tmind.R;

/** 심호흡 훈련 가이드 (4초 들숨 – 4초 멈춤 – 4초 날숨 × 4회) */
public class BreathingGuideActivity extends BaseSeniorActivity {

    private static final int PHASE_SEC = 4;
    private static final int TOTAL_CYCLES = 4;

    private enum Phase { INHALE, HOLD, EXHALE }

    private TextView tvPhase, tvCount, tvGuide;
    private Button btnToggle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private Phase phase = Phase.INHALE;
    private int count = PHASE_SEC;
    private int cycles;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            count--;
            if (count <= 0) {
                nextPhase();
                if (!running) return; // 4회 완료 후 더 이상 스케줄하지 않음
            }
            tvCount.setText(String.valueOf(Math.max(1, count)));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_breathing);

        tvPhase = findViewById(R.id.tvPhase);
        tvCount = findViewById(R.id.tvCount);
        tvGuide = findViewById(R.id.tvGuide);
        btnToggle = findViewById(R.id.btnToggle);
        btnToggle.setBackgroundTintList(null);

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            stop();
            finish();
        });
        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvGuide, R.id.tvPhase);

        btnToggle.setOnClickListener(v -> {
            if (running) stop();
            else start();
        });
        showPhase();
    }

    private void start() {
        running = true;
        cycles = 0;
        phase = Phase.INHALE;
        count = PHASE_SEC;
        showPhase();
        updateGuide();
        btnToggle.setText(R.string.breath_stop);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000);
        if (tts != null) tts.speak(getString(R.string.breath_inhale));
    }

    private void stop() {
        running = false;
        handler.removeCallbacks(tick);
        btnToggle.setText(R.string.breath_start);
    }

    private void nextPhase() {
        switch (phase) {
            case INHALE:
                phase = Phase.HOLD;
                break;
            case HOLD:
                phase = Phase.EXHALE;
                break;
            case EXHALE:
                cycles++;
                if (cycles >= TOTAL_CYCLES) {
                    stop();
                    tvPhase.setText(R.string.breath_done);
                    tvCount.setText("0");
                    tvGuide.setText(getString(R.string.breath_done));
                    if (tts != null) tts.speak(getString(R.string.breath_done));
                    return;
                }
                phase = Phase.INHALE;
                break;
        }
        count = PHASE_SEC;
        showPhase();
        updateGuide();
        if (tts != null) {
            int res = phase == Phase.INHALE ? R.string.breath_inhale
                    : phase == Phase.HOLD ? R.string.breath_hold : R.string.breath_exhale;
            tts.speak(getString(res));
        }
    }

    private void showPhase() {
        int res = phase == Phase.INHALE ? R.string.breath_inhale
                : phase == Phase.HOLD ? R.string.breath_hold : R.string.breath_exhale;
        tvPhase.setText(res);
        tvCount.setText(String.valueOf(count));
    }

    private void updateGuide() {
        tvGuide.setText(getString(R.string.breath_guide_progress, cycles + 1, TOTAL_CYCLES));
    }

    @Override
    protected void onPause() {
        stop();
        super.onPause();
    }
}
