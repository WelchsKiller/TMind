package com.nest.tmind.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.nest.tmind.R;
import com.nest.tmind.api.MemberApiManager;
import com.nest.tmind.util.EmaQuestionBank;
import com.nest.tmind.util.MissionManager;
import com.nest.tmind.util.SessionManager;

/** 설문 시작 전 안내 (오전/오후/추가) — 핵심 문구 청록·굵게 강조 */
public class EmaIntroActivity extends BaseSeniorActivity {

    private EmaQuestionBank.SessionType sessionType;
    private View btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ema_intro);

        String typeExtra = getIntent().getStringExtra(EmaSurveyActivity.EXTRA_SESSION_TYPE);
        if (typeExtra != null) {
            sessionType = EmaQuestionBank.SessionType.valueOf(typeExtra);
        } else {
            sessionType = EmaQuestionBank.dailyTypeNow();
        }
        int count = EmaQuestionBank.itemsFor(sessionType).length;

        int bodyRes;
        switch (sessionType) {
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
        btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> loadServerQuestionsAndStart());
    }

    /** Apidog: HRV/skip 완료된 sessionId 로 GET .../ema/questions 성공 후 설문 진입 */
    private void loadServerQuestionsAndStart() {
        MissionManager mm = new MissionManager(this);
        boolean event = mm.isAdditionalMeasureMode();
        MissionManager.Session missionSession = event
                ? MissionManager.Session.EVENT
                : MissionManager.mainSessionByHour();
        SessionManager sm = new SessionManager(this);

        if (!mm.isHrvDone(missionSession) && !sm.isRemoteHrvDone()) {
            Toast.makeText(this, R.string.ema_need_hrv_first, Toast.LENGTH_LONG).show();
            return;
        }

        btnStart.setEnabled(false);
        long sessionId = MemberApiManager.getCurrentSessionId(this, event);
        if (sessionId > 0) {
            requestQuestions(event);
        } else {
            ensureSessionThenQuestions(event, missionSession);
        }
    }

    private void ensureSessionThenQuestions(boolean event, MissionManager.Session missionSession) {
        MemberApiManager.ensureSessionStarted(this, event, new MemberApiManager.ResultCallback<Long>() {
            @Override
            public void onSuccess(Long data) {
                MissionManager mm = new MissionManager(EmaIntroActivity.this);
                if (mm.isHrvDone(missionSession)) {
                    MemberApiManager.skipHrv(EmaIntroActivity.this, event, "USER_SKIP",
                            new MemberApiManager.ResultCallback<Void>() {
                                @Override
                                public void onSuccess(Void ignored) {
                                    runOnUiThread(() -> requestQuestions(event));
                                }

                                @Override
                                public void onError(String message) {
                                    runOnUiThread(() -> requestQuestions(event));
                                }
                            });
                } else {
                    runOnUiThread(() -> requestQuestions(event));
                }
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnStart.setEnabled(true);
                    Toast.makeText(EmaIntroActivity.this,
                            message != null ? message : getString(R.string.ema_questions_load_failed),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void requestQuestions(boolean event) {
        MemberApiManager.fetchEmaQuestions(this, event,
                new MemberApiManager.ResultCallback<java.util.List<com.nest.tmind.api.ApiModels.QuestionResponse>>() {
                    @Override
                    public void onSuccess(java.util.List<com.nest.tmind.api.ApiModels.QuestionResponse> data) {
                        runOnUiThread(() -> {
                            btnStart.setEnabled(true);
                            Intent i = new Intent(EmaIntroActivity.this, EmaSurveyActivity.class);
                            i.putExtra(EmaSurveyActivity.EXTRA_SESSION_TYPE, sessionType.name());
                            i.putExtra(EmaSurveyActivity.EXTRA_REMOTE_QUESTIONS_JSON,
                                    MemberApiManager.encodeQuestions(data));
                            startActivity(i);
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            btnStart.setEnabled(true);
                            new AlertDialog.Builder(EmaIntroActivity.this)
                                    .setTitle(R.string.ema_questions_load_failed_title)
                                    .setMessage(message != null ? message
                                            : getString(R.string.ema_questions_load_failed))
                                    .setPositiveButton(R.string.dialog_retry,
                                            (d, w) -> loadServerQuestionsAndStart())
                                    .setNegativeButton(R.string.dialog_cancel, null)
                                    .show();
                        });
                    }
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
