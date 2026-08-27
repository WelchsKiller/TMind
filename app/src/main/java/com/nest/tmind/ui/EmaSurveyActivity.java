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
import com.nest.tmind.api.ApiModels;
import com.nest.tmind.api.MemberApiManager;
import com.nest.tmind.util.DataQueueManager;
import com.nest.tmind.util.EmaQuestionBank;
import com.nest.tmind.util.HistoryStore;
import com.nest.tmind.util.MissionManager;
import com.nest.tmind.util.RussellEmotionCalculator;
import com.nest.tmind.util.SessionManager;

import org.json.JSONObject;

/** EMA 설문 */
public class EmaSurveyActivity extends BaseSeniorActivity {

    public static final String EXTRA_FEEDBACK_RESELECT = "feedback_reselect";
    public static final String EXTRA_SESSION_TYPE = "session_type";
    public static final String EXTRA_EDIT_MODE = "edit_mode";

    public static final String[] QUESTIONS = new String[9];

    private EmaQuestionBank.Item[] items;
    private EmaQuestionBank.SessionType sessionType;
    private boolean feedbackReselect;
    private boolean editMode;

    private int currentIndex = 0;
    private int[] answers;
    private long[] remoteQuestionIds;
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
        editMode = getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false);
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
        if (editMode) {
            loadAnswersFromHistory();
            currentIndex = 0;
        } else if (!feedbackReselect) {
            // 같은 세션의 중간 저장만 복원 (오전→오후·추가 시 이전 답 유지 금지)
            int[] saved = session.loadEmaAnswersForSession(sessionType.name(), items.length);
            boolean any = false;
            for (int a : saved) {
                if (a > 0) {
                    any = true;
                    break;
                }
            }
            if (any) {
                answers = saved;
                currentIndex = Math.min(session.getEmaIndex(), items.length - 1);
            } else {
                currentIndex = 0;
                session.clearEmaProgress();
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
        if (!feedbackReselect && !editMode) {
            fetchRemoteQuestions();
        }
    }

    private void confirmExit() {
        if (tts != null) tts.stop();
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_exit_missions)
                .setPositiveButton(R.string.dialog_end, (d, w) -> finish())
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void loadAnswersFromHistory() {
        JSONObject row = HistoryStore.latestEmaForSession(this, sessionType.name());
        if (row != null) {
            JSONObject ans = row.optJSONObject("answers");
            if (ans == null) ans = row;
            for (int i = 0; i < items.length; i++) {
                int v = ans.optInt(items[i].key, 0);
                if (v >= 1 && v <= 5) answers[i] = v;
            }
        }
        // 히스토리 없으면 세션에 남은 답 사용
        boolean any = false;
        for (int a : answers) {
            if (a > 0) {
                any = true;
                break;
            }
        }
        if (!any) {
            int[] saved = session.loadEmaAnswers(items.length);
            if (saved != null && saved.length == items.length) {
                answers = saved;
            }
        }
        session.saveEmaProgress(sessionType.name(), 0, answers);
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
            session.saveEmaProgress(sessionType.name(), currentIndex, answers);
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
            java.util.HashMap<String, Integer> emoMap = new java.util.HashMap<>();
            for (int i = 0; i < items.length; i++) {
                payload.put(items[i].key, answers[i]);
                emoMap.put(items[i].key, answers[i]);
            }
            RussellEmotionCalculator.Point gt = RussellEmotionCalculator.fromEmaAnswers(emoMap);
            if (gt != null) {
                payload.put("valence", gt.valence);
                payload.put("arousal", gt.arousal);
            }
            new DataQueueManager(this).enqueue(
                    feedbackReselect ? "ema_feedback" : (editMode ? "ema_edit" : "ema"), payload);
            new DataQueueManager(this).flushIfOnline();
            if (!feedbackReselect) {
                HistoryStore.addEma(this, sessionType.name(), payload);
            }
        } catch (Exception ignored) {
        }
        if (!feedbackReselect && !editMode && remoteQuestionIds != null) {
            float valence = 0f;
            float arousal = 0f;
            try {
                java.util.HashMap<String, Integer> emoMap = new java.util.HashMap<>();
                for (int i = 0; i < items.length; i++) {
                    emoMap.put(items[i].key, answers[i]);
                }
                RussellEmotionCalculator.Point gt = RussellEmotionCalculator.fromEmaAnswers(emoMap);
                if (gt != null) {
                    valence = gt.valence;
                    arousal = gt.arousal;
                }
            } catch (Exception ignored) {
            }
            MemberApiManager.submitEma(this,
                    new MissionManager(this).isAdditionalMeasureMode(),
                    MemberApiManager.buildEmaRequests(remoteQuestionIds, answers),
                    valence, arousal);
        }

        if (feedbackReselect) {
            Intent i = new Intent(this, FeedbackActivity.class);
            i.putExtra(FeedbackActivity.EXTRA_CHOICE, "disagree");
            try {
                java.util.HashMap<String, Integer> emoMap = new java.util.HashMap<>();
                for (int j = 0; j < items.length; j++) {
                    emoMap.put(items[j].key, answers[j]);
                }
                RussellEmotionCalculator.Point gt = RussellEmotionCalculator.fromEmaAnswers(emoMap);
                if (gt != null) {
                    i.putExtra(FeedbackActivity.EXTRA_CORRECTED_VALENCE, gt.valence);
                    i.putExtra(FeedbackActivity.EXTRA_CORRECTED_AROUSAL, gt.arousal);
                }
            } catch (Exception ignored) {
            }
            startActivity(i);
            finish();
            return;
        }

        new MissionManager(this).setEmaDone();
        // 다음 세션(오후·추가)에서 이전 답이 남지 않도록 진행 저장 삭제 (수정은 히스토리에서 복원)
        session.clearEmaProgress();

        MissionManager mission = new MissionManager(this);
        boolean additional = mission.isAdditionalMeasureMode();

        if (!editMode && additional) {
            startActivity(new Intent(this, VoiceDiaryActivity.class));
            finish();
            return;
        }
        goDashboard();
    }

    private void goDashboard() {
        Intent i = new Intent(this, DashboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void fetchRemoteQuestions() {
        MemberApiManager.fetchEmaQuestions(this,
                new MissionManager(this).isAdditionalMeasureMode(),
                new MemberApiManager.ResultCallback<java.util.List<ApiModels.QuestionResponse>>() {
                    @Override
                    public void onSuccess(java.util.List<ApiModels.QuestionResponse> data) {
                        if (data == null || data.isEmpty()) return;
                        remoteQuestionIds = new long[Math.min(items.length, data.size())];
                        for (int i = 0; i < remoteQuestionIds.length; i++) {
                            ApiModels.QuestionResponse q = data.get(i);
                            remoteQuestionIds[i] = q.questionId;
                        }
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> new AlertDialog.Builder(EmaSurveyActivity.this)
                                .setTitle("오류")
                                .setMessage(message)
                                .setPositiveButton("확인", (d, w) -> finish())
                                .setCancelable(false)
                                .show());
                    }
                });
    }
}
