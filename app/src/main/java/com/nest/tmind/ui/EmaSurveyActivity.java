package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
    public static final String EXTRA_REMOTE_QUESTIONS_JSON = "remote_questions_json";

    public static final String[] QUESTIONS = new String[9];

    private EmaQuestionBank.Item[] items;
    private EmaQuestionBank.SessionType sessionType;
    private boolean feedbackReselect;
    private boolean editMode;

    private int currentIndex = 0;
    private int[] answers;
    private long[] remoteQuestionIds;
    private String[] remotePrompts;
    private String[][] remoteOptionLabels;
    private int serverQuestionCount;
    private SessionManager session;
    private TextView tvQuestion, tvProgress;
    private ProgressBar progressBar;
    private View[] optionViews;
    private boolean submitting;

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

        if (!feedbackReselect && !editMode) {
            if (savedInstanceState != null) {
                serverQuestionCount = savedInstanceState.getInt("serverQuestionCount", 0);
                long[] restoredIds = savedInstanceState.getLongArray("remoteQuestionIds");
                if (restoredIds != null && restoredIds.length > 0) {
                    remoteQuestionIds = restoredIds;
                }
                remotePrompts = savedInstanceState.getStringArray("remotePrompts");
            }
            if (!hasRemoteQuestionIds()) {
                java.util.List<ApiModels.QuestionResponse> remote = loadRemoteQuestions();
                if (remote == null || remote.isEmpty()) {
                    Toast.makeText(this, R.string.ema_questions_load_failed, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                applyRemoteQuestions(remote);
            } else if (serverQuestionCount <= 0 && remoteQuestionIds != null) {
                serverQuestionCount = remoteQuestionIds.length;
            }
        }

        answers = new int[getQuestionCount()];
        if (editMode) {
            loadAnswersFromHistory();
            currentIndex = 0;
        } else if (!feedbackReselect) {
            // 같은 세션의 중간 저장만 복원 (오전→오후·추가 시 이전 답 유지 금지)
            int[] saved = session.loadEmaAnswersForSession(sessionType.name(), getQuestionCount());
            boolean any = false;
            for (int a : saved) {
                if (a > 0) {
                    any = true;
                    break;
                }
            }
            if (any) {
                answers = saved;
                currentIndex = Math.min(session.getEmaIndex(), getQuestionCount() - 1);
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
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("serverQuestionCount", serverQuestionCount);
        outState.putLongArray("remoteQuestionIds", remoteQuestionIds);
        outState.putStringArray("remotePrompts", remotePrompts);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serverQuestionCount = savedInstanceState.getInt("serverQuestionCount", serverQuestionCount);
        long[] ids = savedInstanceState.getLongArray("remoteQuestionIds");
        if (ids != null && ids.length > 0) {
            remoteQuestionIds = ids;
        }
        String[] prompts = savedInstanceState.getStringArray("remotePrompts");
        if (prompts != null && prompts.length > 0) {
            remotePrompts = prompts;
        }
    }

    private java.util.List<ApiModels.QuestionResponse> loadRemoteQuestions() {
        String remoteJson = getIntent().getStringExtra(EXTRA_REMOTE_QUESTIONS_JSON);
        java.util.List<ApiModels.QuestionResponse> remote =
                MemberApiManager.decodeQuestions(remoteJson);
        if (remote != null && !remote.isEmpty()) {
            return remote;
        }
        return MemberApiManager.loadCachedEmaQuestions(this,
                new MissionManager(this).isAdditionalMeasureMode());
    }

    private int getQuestionCount() {
        return serverQuestionCount > 0 ? serverQuestionCount : items.length;
    }

    private void applyRemoteQuestions(java.util.List<ApiModels.QuestionResponse> data) {
        serverQuestionCount = data.size();
        remoteQuestionIds = new long[serverQuestionCount];
        remotePrompts = new String[serverQuestionCount];
        remoteOptionLabels = new String[serverQuestionCount][];
        for (int i = 0; i < serverQuestionCount; i++) {
            ApiModels.QuestionResponse q = data.get(i);
            remoteQuestionIds[i] = MemberApiManager.resolveQuestionId(q);
            remotePrompts[i] = q.text;
            if (q.options != null && q.options.size() >= 5) {
                remoteOptionLabels[i] = q.options.toArray(new String[0]);
            }
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
        EmaQuestionBank.Item item = currentIndex < items.length ? items[currentIndex] : null;
        String[] labels = remoteOptionLabels != null && currentIndex < remoteOptionLabels.length
                && remoteOptionLabels[currentIndex] != null
                ? remoteOptionLabels[currentIndex]
                : (item != null ? item.scaleLabels : EmaQuestionBank.itemsFor(sessionType)[0].scaleLabels);
        for (int i = 0; i < optionViews.length; i++) {
            TextView label = optionViews[i].findViewById(R.id.optLabel);
            ImageView emoji = optionViews[i].findViewById(R.id.optEmoji);
            View radio = optionViews[i].findViewById(R.id.optRadio);
            if (label != null) label.setText(labels[i]);
            if (emoji != null && item != null) {
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
        String prompt = remotePrompts != null && currentIndex < remotePrompts.length
                && remotePrompts[currentIndex] != null && !remotePrompts[currentIndex].isEmpty()
                ? remotePrompts[currentIndex]
                : items[Math.min(currentIndex, items.length - 1)].prompt;
        tvQuestion.setText(prompt);
        tvProgress.setText((currentIndex + 1) + " / " + getQuestionCount());
        progressBar.setMax(getQuestionCount());
        progressBar.setProgress(currentIndex + 1);
        setupTtsButton(R.id.btnTts, prompt);
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
        if (submitting) return;
        if (answers[currentIndex] == 0) {
            if (tts != null) tts.speak("답을 선택해 주세요");
            return;
        }
        // 다음으로 넘어갈 때 이전 문항 음성 중단
        if (tts != null) tts.stop();
        if (currentIndex < getQuestionCount() - 1) {
            currentIndex++;
            showQuestion();
        } else {
            submitAnswers();
        }
    }

    private void submitAnswers() {
        if (submitting) return;
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
        if (!feedbackReselect && !editMode) {
            submitToServerThen(this::afterEmaSubmitted);
            return;
        }

        afterEmaSubmitted();
    }

    private void afterEmaSubmitted() {
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
        session.clearEmaProgress();

        MissionManager mission = new MissionManager(this);
        boolean additional = getIntent().getBooleanExtra(
                AnalysisResultActivity.EXTRA_ADDITIONAL, false)
                || mission.isAdditionalMeasureMode();
        AfterMissionSaved.afterEma(this, additional);
    }

    private void submitToServerThen(Runnable onDone) {
        if (submitting) return;
        submitting = true;
        if (!hasRemoteQuestionIds()) {
            java.util.List<ApiModels.QuestionResponse> cached = loadRemoteQuestions();
            if (cached != null && !cached.isEmpty()) {
                applyRemoteQuestions(cached);
            }
        }
        if (hasRemoteQuestionIds()) {
            sendEmaToServer(onDone);
            return;
        }
        boolean event = new MissionManager(this).isAdditionalMeasureMode();
        MemberApiManager.fetchEmaQuestions(this, event,
                new MemberApiManager.ResultCallback<java.util.List<ApiModels.QuestionResponse>>() {
                    @Override
                    public void onSuccess(java.util.List<ApiModels.QuestionResponse> data) {
                        runOnUiThread(() -> {
                            applyRemoteQuestions(data);
                            if (hasRemoteQuestionIds()) {
                                sendEmaToServer(onDone);
                            } else {
                                submitting = false;
                                Toast.makeText(EmaSurveyActivity.this,
                                        R.string.ema_submit_skipped_no_ids, Toast.LENGTH_LONG).show();
                                if (!fromHrvFlow()) onDone.run();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            submitting = false;
                            Toast.makeText(EmaSurveyActivity.this,
                                    message != null ? message
                                            : getString(R.string.ema_submit_skipped_no_ids),
                                    Toast.LENGTH_LONG).show();
                            if (!fromHrvFlow()) onDone.run();
                        });
                    }
                });
    }

    private boolean fromHrvFlow() {
        return getIntent().getBooleanExtra(AnalysisResultActivity.EXTRA_FROM_HRV, false);
    }

    private boolean hasRemoteQuestionIds() {
        if (remoteQuestionIds == null || remoteQuestionIds.length == 0) return false;
        for (long id : remoteQuestionIds) {
            if (id > 0) return true;
        }
        return false;
    }

    private void sendEmaToServer(Runnable onDone) {
        float valence = 0f;
        float arousal = 0f;
        RussellEmotionCalculator.Point gt = russellPointFromAnswers();
        if (gt != null) {
            valence = gt.valence;
            arousal = gt.arousal;
        }
        MemberApiManager.submitEma(this,
                new MissionManager(this).isAdditionalMeasureMode(),
                MemberApiManager.buildEmaRequests(remoteQuestionIds, answers),
                valence, arousal,
                new MemberApiManager.ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        runOnUiThread(onDone);
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            submitting = false;
                            Toast.makeText(EmaSurveyActivity.this,
                                    message != null ? message : "설문을 서버에 보내지 못했습니다.",
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    /** 로컬 문항 key 기준으로 러셀 좌표를 계산. 서버 문항 수가 달라도 겹치는 만큼만 사용. */
    private RussellEmotionCalculator.Point russellPointFromAnswers() {
        try {
            java.util.HashMap<String, Integer> emoMap = new java.util.HashMap<>();
            int n = Math.min(items.length, answers.length);
            for (int i = 0; i < n; i++) {
                emoMap.put(items[i].key, answers[i]);
            }
            return RussellEmotionCalculator.fromEmaAnswers(emoMap);
        } catch (Exception e) {
            return null;
        }
    }
}
