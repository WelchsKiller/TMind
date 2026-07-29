package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;

import com.nest.tmind.R;
import com.nest.tmind.util.DataQueueManager;
import com.nest.tmind.util.EmaQuestionBank;
import com.nest.tmind.util.HistoryStore;
import com.nest.tmind.util.MissionManager;
import com.nest.tmind.util.SessionManager;

import org.json.JSONObject;

/** EMA 설문 */
public class EmaSurveyActivity extends BaseSeniorActivity {

    public static final String EXTRA_FEEDBACK_RESELECT = "feedback_reselect";
    public static final String EXTRA_SESSION_TYPE = "session_type";

    public static final String[] QUESTIONS = new String[9];

    private EmaQuestionBank.Item[] items;
    private EmaQuestionBank.SessionType sessionType;
    private boolean feedbackReselect;

    private int currentIndex = 0;
    private int[] answers;
    private SessionManager session;
    private TextView tvQuestion, tvProgress;
    private ProgressBar progressBar;
    private View[] optionViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ema_survey);
        session = new SessionManager(this);

        feedbackReselect = getIntent().getBooleanExtra(EXTRA_FEEDBACK_RESELECT, false);
        String typeExtra = getIntent().getStringExtra(EXTRA_SESSION_TYPE);
        if (typeExtra != null) {
            sessionType = EmaQuestionBank.SessionType.valueOf(typeExtra);
        } else if (feedbackReselect) {
            sessionType = EmaQuestionBank.SessionType.MORNING;
            items = emotionOnly();
        } else {
            sessionType = EmaQuestionBank.dailyTypeNow();
            items = EmaQuestionBank.itemsFor(sessionType);
        }
        if (items == null) {
            items = EmaQuestionBank.itemsFor(sessionType);
        }

        answers = new int[items.length];
        if (!feedbackReselect) {
            int[] saved = session.loadEmaAnswers(items.length);
            if (saved != null && saved.length == items.length) {
                answers = saved;
                currentIndex = Math.min(session.getEmaIndex(), items.length - 1);
            }
        }

        tvQuestion = findViewById(R.id.tvQuestion);
        tvProgress = findViewById(R.id.tvProgress);
        progressBar = findViewById(R.id.progressBar);
        optionViews = new View[]{
                findViewById(R.id.opt1), findViewById(R.id.opt2), findViewById(R.id.opt3),
                findViewById(R.id.opt4), findViewById(R.id.opt5)
        };

        findViewById(R.id.btnBack).setOnClickListener(v -> confirmExit());
        findViewById(R.id.btnPrev).setOnClickListener(v -> goPrev());
        findViewById(R.id.btnNext).setOnClickListener(v -> goNext());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });

        for (int i = 0; i < optionViews.length; i++) {
            final int displayIndex = i;
            optionViews[i].setOnClickListener(v -> selectOption(scoreFromDisplayIndex(displayIndex)));
        }

        showQuestion();
    }

    private void confirmExit() {
        if (tts != null) tts.stop();
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_exit_survey)
                .setPositiveButton(R.string.dialog_end, (d, w) -> finish())
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private static EmaQuestionBank.Item[] emotionOnly() {
        EmaQuestionBank.Item[] all = EmaQuestionBank.itemsFor(EmaQuestionBank.SessionType.MORNING);
        if (all.length >= 9) {
            EmaQuestionBank.Item[] emo = new EmaQuestionBank.Item[9];
            System.arraycopy(all, all.length - 9, emo, 0, 9);
            return emo;
        }
        return all;
    }

    private int scoreFromDisplayIndex(int displayIndex) {
        return 5 - displayIndex;
    }

    private int displayIndexFromScore(int score) {
        if (score <= 0) return -1;
        return 5 - score;
    }

    private void bindOptionsForCurrent() {
        EmaQuestionBank.Item item = items[currentIndex];
        String[] labels = item.scaleLabels;
        for (int i = 0; i < optionViews.length; i++) {
            TextView label = optionViews[i].findViewById(R.id.optLabel);
            ImageView emoji = optionViews[i].findViewById(R.id.optEmoji);
            View radio = optionViews[i].findViewById(R.id.optRadio);
            if (label != null) label.setText(labels[i]);
            if (emoji != null) {
                emoji.setImageResource(EmaQuestionBank.drawableResFor(item, i));
                emoji.setBackground(null);
            }
            if (radio != null) {
                radio.setBackgroundResource(R.drawable.bg_radio_unchecked);
            }
        }
    }

    private void showQuestion() {
        if (tts != null) tts.stop();
        bindOptionsForCurrent();
        tvQuestion.setText(items[currentIndex].prompt);
        tvProgress.setText((currentIndex + 1) + " / " + items.length);
        progressBar.setMax(items.length);
        progressBar.setProgress(currentIndex + 1);
        setupTtsButton(R.id.btnTts, items[currentIndex].prompt);
        int selected = answers[currentIndex];
        int selectedDisplay = displayIndexFromScore(selected);
        for (int i = 0; i < optionViews.length; i++) {
            View radio = optionViews[i].findViewById(R.id.optRadio);
            boolean on = i == selectedDisplay;
            if (radio != null) {
                radio.setBackgroundResource(on
                        ? R.drawable.bg_radio_checked
                        : R.drawable.bg_radio_unchecked);
            }
        }
        if (!feedbackReselect) {
            session.saveEmaProgress(currentIndex, answers);
        }
    }

    private void selectOption(int score) {
        answers[currentIndex] = score;
        int selectedDisplay = displayIndexFromScore(score);
        for (int i = 0; i < optionViews.length; i++) {
            View radio = optionViews[i].findViewById(R.id.optRadio);
            boolean on = i == selectedDisplay;
            if (radio != null) {
                radio.setBackgroundResource(on
                        ? R.drawable.bg_radio_checked
                        : R.drawable.bg_radio_unchecked);
            }
        }
    }

    private void goPrev() {
        if (tts != null) tts.stop();
        if (currentIndex > 0) {
            currentIndex--;
            showQuestion();
        }
    }

    private void goNext() {
        if (answers[currentIndex] == 0) {
            if (tts != null) tts.speak("답을 선택해 주세요");
            return;
        }
        // 다음으로 넘어갈 때 이전 문항 음성 중단
        if (tts != null) tts.stop();
        if (currentIndex < items.length - 1) {
            currentIndex++;
            showQuestion();
        } else {
            submitAnswers();
        }
    }

    private void submitAnswers() {
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionType", sessionType.name());
            payload.put("feedbackReselect", feedbackReselect);
            for (int i = 0; i < items.length; i++) {
                payload.put(items[i].key, answers[i]);
            }
            new DataQueueManager(this).enqueue(feedbackReselect ? "ema_feedback" : "ema", payload);
            new DataQueueManager(this).flushIfOnline();
            if (!feedbackReselect) {
                HistoryStore.addEma(this, sessionType.name(), payload);
            }
        } catch (Exception ignored) {
        }

        if (feedbackReselect) {
            Intent i = new Intent(this, FeedbackActivity.class);
            i.putExtra(FeedbackActivity.EXTRA_CHOICE, "disagree");
            startActivity(i);
            finish();
            return;
        }

        new MissionManager(this).setEmaDone();
        session.saveEmaProgress(0, new int[items.length]);
        goDashboard();
    }

    private void goDashboard() {
        Intent i = new Intent(this, DashboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
