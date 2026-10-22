package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.nest.tmind.R;
import com.nest.tmind.api.ApiModels;
import com.nest.tmind.api.MemberApiManager;
import com.nest.tmind.util.EmaQuestionBank;
import com.nest.tmind.util.MissionManager;
import com.nest.tmind.util.SessionManager;

/** 오늘의 기록 — 시각 자동 세션 + 추가 측정 + 주간 별 */
public class DashboardActivity extends BaseSeniorActivity {

    private MissionManager mission;
    private SessionManager session;

    private View cardHrv, cardEma, cardDiary, cardFeedback, cardAdditional;
    private TextView tvGreeting, tvProgress, tvAdditionalSub, tvPhaseBadge;
    private LinearLayout starRow;
    private ProgressBar progressBar;
    private View btnWeeklyTrend;
    private ApiModels.TodayResponse today;
    private ApiModels.ParticipationResponse participation;

    private static final int SECRET_TAP_COUNT = 5;
    private static final long SECRET_TAP_WINDOW_MS = 2000L;
    private int titleTapCount;
    private long lastTitleTapMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (!session.hasValidAuth()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_dashboard);
        mission = new MissionManager(this);

        tvGreeting = findViewById(R.id.tvGreeting);
        tvPhaseBadge = findViewById(R.id.tvPhaseBadge);
        tvProgress = findViewById(R.id.tvProgress);
        cardHrv = findViewById(R.id.cardHrv);
        cardEma = findViewById(R.id.cardEma);
        cardDiary = findViewById(R.id.cardDiary);
        cardFeedback = findViewById(R.id.cardFeedback);
        cardAdditional = findViewById(R.id.cardAdditional);
        tvAdditionalSub = findViewById(R.id.tvAdditionalSub);
        progressBar = findViewById(R.id.progressBar);
        starRow = findViewById(R.id.starRow);
        btnWeeklyTrend = findViewById(R.id.btnWeeklyTrend);

        setupMissionCard(cardHrv, R.drawable.ic_heart, R.string.mission_hrv, R.string.mission_hrv_sub);
        setupMissionCard(cardEma, R.drawable.ic_survey, R.string.mission_ema, R.string.mission_ema_sub);
        setupMissionCard(cardDiary, R.drawable.ic_diary, R.string.mission_diary, R.string.mission_diary_active);
        setupMissionCard(cardFeedback, R.drawable.ic_survey, R.string.feedback_resume_title,
                R.string.feedback_resume_sub);

        tvGreeting.setText(R.string.dashboard_greeting);
        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvGreeting, R.id.tvStarHint, R.id.tvProgress);

        findViewById(R.id.btnLogout).setOnClickListener(v -> confirmLogout());
        cardHrv.setOnClickListener(v -> onHrvClick());
        cardEma.setOnClickListener(v -> onEmaClick());
        cardDiary.setOnClickListener(v -> onDiaryClick());
        cardFeedback.setOnClickListener(v -> onFeedbackClick());
        cardAdditional.setOnClickListener(v -> openAdditional());
        btnWeeklyTrend.setOnClickListener(v ->
                startActivity(new Intent(this, WeeklyTrendActivity.class)));
        findViewById(R.id.btnNewParticipant).setOnClickListener(v -> confirmNewParticipant());
        findViewById(R.id.tvTitle).setOnClickListener(v -> onTitleTap());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExitIfMissionsIncomplete();
            }
        });

        session.saveScreen("dashboard");
        applyLatestTodayIfAny();
        syncServerState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mission = new MissionManager(this);
        applyLatestTodayIfAny();
        syncServerState();
    }

    private void confirmNewParticipant() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.new_participant_confirm)
                .setPositiveButton(R.string.dialog_yes, (d, w) -> logoutToLogin(true))
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.logout, (d, w) -> logoutToLogin(false))
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    /**
     * 제목 연속 5회 탭: 로그아웃 후 8자리 로그인 화면으로. 참여자가 우연히 누르지 않도록
     * 탭 간격이 벌어지면 횟수를 초기화한다.
     */
    private void onTitleTap() {
        long now = System.currentTimeMillis();
        if (now - lastTitleTapMs > SECRET_TAP_WINDOW_MS) {
            titleTapCount = 0;
        }
        lastTitleTapMs = now;
        if (++titleTapCount < SECRET_TAP_COUNT) return;
        titleTapCount = 0;
        logoutToLogin(true);
    }

    private void logoutToLogin(boolean forceRegister) {
        MemberApiManager.logout(this, new MemberApiManager.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
            }

            @Override
            public void onError(String message) {
            }
        });
        // logout() 안에서도 비우지만, 화면 전환 전에 홈 캐시를 즉시 끊는다.
        MemberApiManager.clearAccountLocalState(this);
        today = null;
        participation = null;
        session.logoutForNewParticipant();
        Intent i = new Intent(this, LoginActivity.class);
        if (forceRegister) {
            i.putExtra(LoginActivity.EXTRA_FORCE_REGISTER, true);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void confirmExitIfMissionsIncomplete() {
        if (hasPendingTodaySession()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.confirm_exit_missions)
                    .setPositiveButton(R.string.dialog_end, (d, w) -> finishAffinity())
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
            return;
        }
        if (isTodayAllDone() || session.isStudyEnded()) {
            finishAffinity();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_exit_missions)
                .setPositiveButton(R.string.dialog_end, (d, w) -> finishAffinity())
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void openAdditional() {
        if (!isEventAvailable()) {
            Toast.makeText(this, additionalUnavailableMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        MemberApiManager.fetchToday(this, new MemberApiManager.ResultCallback<ApiModels.TodayResponse>() {
            @Override
            public void onSuccess(ApiModels.TodayResponse data) {
                runOnUiThread(() -> {
                    applyTodayResponse(data);
                    if (!isEventAvailable()) {
                        Toast.makeText(DashboardActivity.this,
                                additionalUnavailableMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startEventSession();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(DashboardActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void startEventSession() {
        if (needsEventFeedback()) {
            mission.setAdditionalMeasureMode(true);
            openFeedbackReentry(true);
            return;
        }
        mission.setAdditionalMeasureMode(true);
        MemberApiManager.ResultCallback<Long> afterEvent =
                new MemberApiManager.ResultCallback<Long>() {
                    @Override
                    public void onSuccess(Long data) {
                        runOnUiThread(() -> proceedAdditionalMeasure());
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            mission.setAdditionalMeasureMode(false);
                            Toast.makeText(DashboardActivity.this, message, Toast.LENGTH_LONG).show();
                        });
                    }
                };
        // 피드백만 남았거나 미션이 이어지면 기존 Event 를 쓰고, 아니면 새로 연다.
        long eventId = session.getCurrentSessionId(true);
        if (needsEventFeedback() || mission.hasEventInProgress()
                || (eventId > 0 && !session.isFeedbackDone(eventId))) {
            MemberApiManager.ensureSessionStarted(this, true, afterEvent);
        } else {
            MemberApiManager.startFreshEventSession(this, afterEvent);
        }
    }

    private void proceedAdditionalMeasure() {
        if (needsEventFeedback()) {
            openFeedbackReentry(true);
            return;
        }
        MissionManager.Session event = MissionManager.Session.EVENT;
        if (mission.isSessionAllDone(event) && session.getCurrentSessionId(true) > 0
                && !session.isFeedbackDone(session.getCurrentSessionId(true))) {
            MemberApiManager.resyncEventSession(this, new MemberApiManager.ResultCallback<Long>() {
                @Override
                public void onSuccess(Long data) {
                    runOnUiThread(() -> {
                        if (needsEventFeedback()) {
                            openFeedbackReentry(true);
                        } else {
                            mission.clearEventMissions();
                            openHrv();
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        mission.clearEventMissions();
                        openHrv();
                    });
                }
            });
            return;
        }
        // 추가 측정도 심박 → 설문 → 일기 순서.
        if (!mission.isHrvDone(event)) {
            openHrv();
        } else if (!mission.isEmaDone(event)) {
            openEma();
        } else if (!mission.isDiaryDone(event)) {
            openDiary();
        } else {
            mission.clearEventMissions();
            openHrv();
        }
    }

    private void onFeedbackClick() {
        if (!needsFeedbackButton()) return;
        boolean additional = !needsTodayFeedback() || isTodayEventSession();
        mission.setAdditionalMeasureMode(additional);
        openFeedbackReentry(additional);
    }

    private void openFeedbackReentry(boolean additional) {
        Intent i = new Intent(this, AnalysisResultActivity.class);
        i.putExtra(AnalysisResultActivity.EXTRA_ADDITIONAL, additional);
        i.putExtra(AnalysisResultActivity.EXTRA_REENTRY, true);
        Float valence = predictedValence(additional);
        Float arousal = predictedArousal(additional);
        if (valence != null && arousal != null) {
            i.putExtra(AnalysisResultActivity.EXTRA_PREDICTED_VALENCE, valence);
            i.putExtra(AnalysisResultActivity.EXTRA_PREDICTED_AROUSAL, arousal);
        }
        startActivity(i);
    }

    private void onHrvClick() {
        mission.setAdditionalMeasureMode(false);
        if (isPredictionLocked()) {
            Toast.makeText(this, R.string.hrv_cannot_remeasure_after_result, Toast.LENGTH_SHORT).show();
            return;
        }
        if ((isHrvValid() || isHrvInvalid()) && (isEmaDone() || isDiaryDone())) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.hrv_remeasure_resets_followup)
                    .setPositiveButton(R.string.hrv_remeasure, (d, w) -> openHrv())
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
            return;
        }
        openHrv();
    }

    private void onEmaClick() {
        mission.setAdditionalMeasureMode(false);
        if (isPredictionLocked()) {
            Toast.makeText(this, R.string.mission_locked_after_result, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isEmaDone()) {
            Toast.makeText(this, R.string.mission_already_done, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isHrvValid()) {
            Toast.makeText(this, isHrvInvalid()
                            ? R.string.mission_need_valid_hrv
                            : R.string.mission_need_hrv_first,
                    Toast.LENGTH_LONG).show();
            return;
        }
        openEma();
    }

    private void onDiaryClick() {
        mission.setAdditionalMeasureMode(false);
        if (isPredictionLocked()) {
            Toast.makeText(this, R.string.mission_locked_after_result, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isDiaryDone()) {
            Toast.makeText(this, R.string.mission_already_done, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isHrvValid()) {
            Toast.makeText(this, isHrvInvalid()
                            ? R.string.mission_need_valid_hrv
                            : R.string.mission_need_hrv_first,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!isEmaDone()) {
            Toast.makeText(this, R.string.mission_need_ema_first, Toast.LENGTH_LONG).show();
            return;
        }
        openDiary();
    }

    private void openHrv() {
        Intent i = new Intent(this, HrvGuideActivity.class);
        i.putExtra(AnalysisResultActivity.EXTRA_ADDITIONAL, mission.isAdditionalMeasureMode());
        startActivity(i);
    }

    private void openEma() {
        boolean event = mission.isAdditionalMeasureMode();
        Intent i = new Intent(this, EmaIntroActivity.class);
        i.putExtra(EmaSurveyActivity.EXTRA_SESSION_TYPE, mapEmaSession().name());
        i.putExtra(AnalysisResultActivity.EXTRA_ADDITIONAL, event);
        startActivity(i);
    }

    private EmaQuestionBank.SessionType mapEmaSession() {
        if (mission.isAdditionalMeasureMode()) {
            return EmaQuestionBank.SessionType.EVENT;
        }
        if (today != null) {
            EmaQuestionBank.SessionType fromServer = EmaQuestionBank.fromServerType(today.currentType);
            if (fromServer != null) return fromServer;
        }
        switch (MissionManager.mainSessionByHour()) {
            case AFTERNOON:
                return EmaQuestionBank.SessionType.AFTERNOON;
            default:
                return EmaQuestionBank.SessionType.MORNING;
        }
    }

    private void openDiary() {
        Intent i = new Intent(this, VoiceDiaryActivity.class);
        i.putExtra(AnalysisResultActivity.EXTRA_ADDITIONAL, mission.isAdditionalMeasureMode());
        startActivity(i);
    }

    private void refreshUi() {
        String label = sessionLabelFromToday();
        int done = todayCompletedCount();
        int total = todayTotalCount();
        tvProgress.setText(getString(R.string.mission_progress_session, label, done, total));
        progressBar.setMax(total);
        progressBar.setProgress(Math.min(done, total));

        applyCardState(cardHrv, isHrvValid(), R.drawable.bg_mission_card_active);
        applyCardState(cardEma, isEmaDone(), R.drawable.bg_mission_card_ema);
        applyCardState(cardDiary, isDiaryDone(), R.drawable.bg_mission_card_diary);
        refreshFeedbackCard();

        TextView hrvSub = cardHrv.findViewById(R.id.tvMissionSub);
        if (isHrvInvalid() && !isPredictionLocked()) {
            hrvSub.setText(R.string.hrv_status_invalid_remeasure);
        } else if (isHrvValid() && !isPredictionLocked()) {
            hrvSub.setText(R.string.hrv_can_remeasure_until_result);
        } else {
            hrvSub.setText(R.string.mission_hrv_sub);
        }
        ((TextView) cardEma.findViewById(R.id.tvMissionSub)).setText(R.string.mission_ema_sub);
        ((TextView) cardDiary.findViewById(R.id.tvMissionSub)).setText(R.string.mission_diary_active);

        btnWeeklyTrend.setVisibility(participatedDays() >= 7 ? View.VISIBLE : View.GONE);

        updateGreeting();
        updatePhaseBadge();
        refreshAdditionalCard();
        renderStars();
    }

    private void refreshFeedbackCard() {
        boolean show = needsFeedbackButton();
        cardFeedback.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            applyCardState(cardFeedback, false, R.drawable.bg_mission_card_ema);
            ((TextView) cardFeedback.findViewById(R.id.tvMissionSub))
                    .setText(R.string.feedback_resume_sub);
        }
    }

    private void refreshAdditionalCard() {
        boolean active = isEventAvailable();
        cardAdditional.setEnabled(active);
        cardAdditional.setClickable(active);
        cardAdditional.setAlpha(active ? 1f : 0.45f);
        if (active) {
            String guide = today != null ? today.eventGuideText : null;
            if (guide != null && !guide.trim().isEmpty()) {
                tvAdditionalSub.setText(guide.trim());
            } else {
                tvAdditionalSub.setText(R.string.additional_measure_sub);
            }
        } else {
            tvAdditionalSub.setText(R.string.additional_measure_need_mission);
        }
        if (active) {
            cardAdditional.setBackgroundResource(R.drawable.bg_btn_outline);
        } else {
            cardAdditional.setBackgroundResource(R.drawable.bg_mission_card_completed);
        }
    }

    private String additionalUnavailableMessage() {
        if (today != null && !today.eventActive) {
            return getString(R.string.additional_measure_admin_off);
        }
        return getString(R.string.additional_measure_unavailable);
    }

    private boolean isHrvValid() {
        return MemberApiManager.isTodayHrvValid(today);
    }

    private boolean isHrvInvalid() {
        return MemberApiManager.isTodayHrvInvalid(today);
    }

    /** 결과(예측) 화면이 나온 뒤에는 이 세션의 심박을 다시 잴 수 없다. */
    private boolean isPredictionLocked() {
        if (today != null && Boolean.TRUE.equals(today.predictionDone)) return true;
        long sid = 0L;
        if (today != null && today.currentSessionId != null) {
            sid = today.currentSessionId;
        }
        if (sid <= 0) sid = session.getCurrentSessionId(false);
        if (sid > 0) return session.isPredictionSaved(sid);
        return isTodayAllDone();
    }

    private boolean needsTodayFeedback() {
        return today != null && today.needsFeedback();
    }

    private boolean needsEventFeedback() {
        long eventId = session.getCurrentSessionId(true);
        if (eventId > 0 && session.needsFeedback(eventId)) return true;
        ApiModels.StartSessionData started = MemberApiManager.lastStartSession();
        return eventId > 0 && started != null
                && started.sessionId == eventId && started.needsFeedback();
    }

    private boolean needsFeedbackButton() {
        return needsTodayFeedback() || needsEventFeedback();
    }

    private boolean isTodayEventSession() {
        if (today == null || today.currentSessionId == null) return false;
        long sid = today.currentSessionId;
        long eventId = session.getCurrentSessionId(true);
        long mainId = session.getCurrentSessionId(false);
        return sid > 0 && sid == eventId && sid != mainId;
    }

    private Float predictedValence(boolean additional) {
        if (!additional && today != null && today.predictedValence != null) {
            return today.predictedValence;
        }
        if (additional) {
            if (today != null && isTodayEventSession() && today.predictedValence != null) {
                return today.predictedValence;
            }
            ApiModels.StartSessionData started = MemberApiManager.lastStartSession();
            if (started != null && started.predictedValence != null) {
                return started.predictedValence;
            }
        }
        return null;
    }

    private Float predictedArousal(boolean additional) {
        if (!additional && today != null && today.predictedArousal != null) {
            return today.predictedArousal;
        }
        if (additional) {
            if (today != null && isTodayEventSession() && today.predictedArousal != null) {
                return today.predictedArousal;
            }
            ApiModels.StartSessionData started = MemberApiManager.lastStartSession();
            if (started != null && started.predictedArousal != null) {
                return started.predictedArousal;
            }
        }
        return null;
    }

    private boolean isEmaDone() {
        return today != null && Boolean.TRUE.equals(today.emaDone);
    }

    private boolean isDiaryDone() {
        return today != null && Boolean.TRUE.equals(today.diaryDone);
    }

    private boolean isEventAvailable() {
        return today != null && Boolean.TRUE.equals(today.eventAvailable);
    }

    private boolean isTodayAllDone() {
        return isHrvValid() && isEmaDone() && isDiaryDone();
    }

    /** /today 가 FOLLOWUP 등 아직 할 세션을 내려주면, 연구 기간이 지나도 이어간다. */
    private boolean hasPendingTodaySession() {
        if (needsFeedbackButton()) return true;
        if (today == null || today.currentType == null) return false;
        return !today.currentType.trim().isEmpty() && !isTodayAllDone();
    }

    private int todayCompletedCount() {
        if (today == null) return 0;
        int n = 0;
        if (MemberApiManager.isTodayHrvValid(today)) n++;
        if (Boolean.TRUE.equals(today.emaDone)) n++;
        if (Boolean.TRUE.equals(today.diaryDone)) n++;
        return n;
    }

    private int todayTotalCount() {
        if (today != null && today.totalCount > 0) return today.totalCount;
        return 3;
    }

    private int participatedDays() {
        if (today != null && today.participatedDays != null) return today.participatedDays;
        if (participation != null && participation.period != null) {
            return participation.period.participatedDays;
        }
        return 0;
    }

    private String sessionLabelFromToday() {
        if (today == null || today.currentType == null || today.currentType.trim().isEmpty()) {
            return getString(R.string.session_today);
        }
        switch (today.currentType.trim().toUpperCase()) {
            case "PM":
            case "AFTERNOON":
                return getString(R.string.session_afternoon);
            case "AM":
            case "MORNING":
                return getString(R.string.session_morning);
            case "BASELINE":
                return getString(R.string.session_baseline);
            case "FOLLOWUP":
                return getString(R.string.session_followup);
            default:
                return today.currentType.trim();
        }
    }

    private void syncServerState() {
        if (!session.hasAccessToken()) return;
        MemberApiManager.fetchToday(this, new MemberApiManager.ResultCallback<ApiModels.TodayResponse>() {
            @Override
            public void onSuccess(ApiModels.TodayResponse data) {
                runOnUiThread(() -> applyTodayResponse(data));
            }

            @Override
            public void onError(String message) {
                MemberApiManager.showToast(DashboardActivity.this, message);
            }
        });
        MemberApiManager.fetchParticipation(this,
                new MemberApiManager.ResultCallback<ApiModels.ParticipationResponse>() {
                    @Override
                    public void onSuccess(ApiModels.ParticipationResponse data) {
                        runOnUiThread(() -> {
                            participation = data;
                            updateGreeting();
                            updatePhaseBadge();
                            renderStars();
                            btnWeeklyTrend.setVisibility(
                                    participatedDays() >= 7 ? View.VISIBLE : View.GONE);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> btnWeeklyTrend.setVisibility(View.GONE));
                    }
                });
    }

    /** 심박·설문 저장 직후 받아 둔 /today 가 있으면, 재조회가 끝나기 전에 그 값으로 먼저 그린다. */
    private void applyLatestTodayIfAny() {
        ApiModels.TodayResponse cached = MemberApiManager.lastToday();
        if (cached != null) {
            applyTodayResponse(cached);
        } else {
            refreshUi();
        }
    }

    private void applyTodayResponse(ApiModels.TodayResponse data) {
        if (data == null) return;
        today = data;
        syncPhaseFlagsFromToday(data);
        MissionManager.Session main = mapCurrentType(data.currentType);
        mission.syncFromServer(main,
                MemberApiManager.isTodayHrvValid(data),
                Boolean.TRUE.equals(data.emaDone),
                Boolean.TRUE.equals(data.diaryDone));
        refreshUi();
    }

    private void syncPhaseFlagsFromToday(ApiModels.TodayResponse data) {
        if (data == null) return;
        if (Boolean.TRUE.equals(data.baselineDone)) {
            session.setBaselineDone(true);
        }
        if (Boolean.TRUE.equals(data.followupDone)) {
            session.setFollowupDone(true);
            session.setBaselineDone(true);
        }
        String type = currentTypeKey();
        if ("FOLLOWUP".equals(type) || "AM".equals(type) || "MORNING".equals(type)
                || "PM".equals(type) || "AFTERNOON".equals(type) || hasAnyFilledStar()) {
            session.setBaselineDone(true);
        }
        if ("BASELINE".equals(type) && isTodayAllDone() && !needsFeedbackButton()) {
            session.setBaselineDone(true);
        }
        if ("FOLLOWUP".equals(type) && isTodayAllDone() && !needsFeedbackButton()) {
            session.setFollowupDone(true);
            session.setBaselineDone(true);
        }
    }

    private void updateGreeting() {
        tvGreeting.setText(greetingForToday());
    }

    private void updatePhaseBadge() {
        if (tvPhaseBadge == null) return;
        String type = currentTypeKey();
        if ("BASELINE".equals(type)) {
            tvPhaseBadge.setVisibility(View.VISIBLE);
            tvPhaseBadge.setText(R.string.phase_baseline);
            tvPhaseBadge.setTextColor(ContextCompat.getColor(this, R.color.baseline_blue));
            tvPhaseBadge.setBackgroundResource(R.drawable.bg_phase_badge_baseline);
            tvPhaseBadge.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.bg_phase_dot_baseline, 0, 0, 0);
            tvPhaseBadge.setCompoundDrawablePadding(dp(8));
        } else if ("FOLLOWUP".equals(type)) {
            tvPhaseBadge.setVisibility(View.VISIBLE);
            tvPhaseBadge.setText(R.string.phase_followup);
            tvPhaseBadge.setTextColor(ContextCompat.getColor(this, R.color.followup_pink));
            tvPhaseBadge.setBackgroundResource(R.drawable.bg_phase_badge_followup);
            tvPhaseBadge.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.bg_phase_dot_followup, 0, 0, 0);
            tvPhaseBadge.setCompoundDrawablePadding(dp(8));
        } else {
            tvPhaseBadge.setVisibility(View.GONE);
            tvPhaseBadge.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String greetingForToday() {
        if (needsFeedbackButton()) {
            return getString(R.string.dashboard_greeting_feedback);
        }
        String type = currentTypeKey();
        if ("BASELINE".equals(type) || "FOLLOWUP".equals(type)) {
            return isTodayAllDone()
                    ? getString(R.string.dashboard_greeting_mission_done)
                    : getString(R.string.dashboard_greeting_mission_start);
        }
        boolean morningHour = MissionManager.mainSessionByHour() == MissionManager.Session.MORNING;
        boolean amDone = isAmDoneToday();
        boolean pmDone = isPmDoneToday();
        if ("AM".equals(type) || "MORNING".equals(type) || (morningHour && type.isEmpty())) {
            if (amDone || (isTodayAllDone() && morningHour)) {
                return getString(R.string.dashboard_greeting_am_done);
            }
            return getString(R.string.dashboard_greeting_am_start);
        }
        // 오후 시간대
        if (amDone && pmDone) {
            return getString(R.string.dashboard_greeting_am_pm_done);
        }
        if (pmDone) {
            return getString(R.string.dashboard_greeting_pm_done);
        }
        if ("PM".equals(type) || "AFTERNOON".equals(type) || !morningHour) {
            return getString(R.string.dashboard_greeting_pm_start);
        }
        return getString(R.string.dashboard_greeting_mission_start);
    }

    private String currentTypeKey() {
        if (today == null || today.currentType == null) return "";
        return today.currentType.trim().toUpperCase();
    }

    private boolean isAmDoneToday() {
        if (participation != null && participation.today != null) {
            return participation.today.amDone;
        }
        String type = currentTypeKey();
        if ("AM".equals(type) || "MORNING".equals(type)) {
            return isTodayAllDone();
        }
        if ("PM".equals(type) || "AFTERNOON".equals(type)) {
            return !isMissedMorning();
        }
        return false;
    }

    private boolean isPmDoneToday() {
        if (participation != null && participation.today != null) {
            return participation.today.pmDone;
        }
        String type = currentTypeKey();
        if ("PM".equals(type) || "AFTERNOON".equals(type)) {
            return isTodayAllDone();
        }
        return type.isEmpty() && !MissionManager.mainSessionByHour().equals(MissionManager.Session.MORNING)
                && isTodayAllDone();
    }

    private boolean isMissedMorning() {
        if (today == null || today.missedType == null) return false;
        String missed = today.missedType.trim().toUpperCase();
        return "AM".equals(missed) || "MORNING".equals(missed);
    }

    private boolean isBaselineDoneUi() {
        if (today != null && today.baselineDone != null) {
            return today.baselineDone;
        }
        if (session.isBaselineDone()) return true;
        String type = currentTypeKey();
        if ("BASELINE".equals(type)) return false;
        return "FOLLOWUP".equals(type)
                || "AM".equals(type) || "MORNING".equals(type)
                || "PM".equals(type) || "AFTERNOON".equals(type)
                || hasAnyFilledStar()
                || participatedDays() > 0;
    }

    private boolean isFollowupDoneUi() {
        if (today != null && today.followupDone != null) {
            return today.followupDone;
        }
        return session.isFollowupDone();
    }

    private boolean hasAnyFilledStar() {
        if (today == null || today.stars == null) return false;
        for (Integer v : today.stars) {
            if (v != null && v > 1) return true;
        }
        return false;
    }

    private static MissionManager.Session mapCurrentType(String currentType) {
        if (currentType == null) return MissionManager.mainSessionByHour();
        switch (currentType.toUpperCase()) {
            case "PM":
            case "AFTERNOON":
                return MissionManager.Session.AFTERNOON;
            case "AM":
            case "MORNING":
                return MissionManager.Session.MORNING;
            default:
                // BASELINE / FOLLOWUP 등은 현재 시각 기준으로 표시
                return MissionManager.mainSessionByHour();
        }
    }

    private void renderStars() {
        starRow.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (36 * d);
        int circle = (int) (22 * d);
        int pad = (int) (2 * d);
        int gap = (int) (6 * d);

        starRow.addView(progressCircleView(circle, gap, isBaselineDoneUi(), true));

        int[] states = starStates();
        for (int i = 0; i < states.length; i++) {
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(pad, 0, pad, 0);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setAdjustViewBounds(true);
            int state = states[i];
            iv.clearColorFilter();
            if (state == MissionManager.STAR_BONUS) {
                iv.setImageResource(R.drawable.ic_star_gold);
            } else if (state == MissionManager.STAR_FULL) {
                iv.setImageResource(R.drawable.ic_star_filled);
            } else if (state == MissionManager.STAR_HALF_BONUS) {
                iv.setImageResource(R.drawable.ic_star_half_bonus);
            } else if (state == MissionManager.STAR_HALF_RIGHT_BONUS) {
                iv.setImageResource(R.drawable.ic_star_half_right_bonus);
            } else if (state == MissionManager.STAR_HALF_RIGHT) {
                iv.setImageResource(R.drawable.ic_star_half_right);
            } else if (state == MissionManager.STAR_HALF) {
                iv.setImageResource(R.drawable.ic_star_half);
            } else {
                iv.setImageResource(R.drawable.ic_star_empty);
            }
            starRow.addView(iv);
        }

        starRow.addView(progressCircleView(circle, gap, isFollowupDoneUi(), false));
    }

    private View progressCircleView(int size, int sideGap, boolean done, boolean baseline) {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(sideGap, 0, sideGap, 0);
        lp.gravity = android.view.Gravity.CENTER_VERTICAL;
        v.setLayoutParams(lp);
        if (done) {
            v.setBackgroundResource(baseline
                    ? R.drawable.bg_progress_circle_baseline
                    : R.drawable.bg_progress_circle_followup);
        } else {
            v.setBackgroundResource(R.drawable.bg_progress_circle_empty);
        }
        return v;
    }

    /**
     * /today stars 7칸만 그린다. 1=빈, 2=한쪽, 3=한쪽+효과, 4=가득, 5=가득+효과.
     * participation 날짜로 다시 그리면 서버 칸보다 한 칸 밀린다.
     */
    private int[] starStates() {
        int[] row = new int[7];
        for (int i = 0; i < 7; i++) row[i] = MissionManager.STAR_EMPTY;
        if (today != null && today.stars != null) {
            int n = Math.min(7, today.stars.size());
            for (int i = 0; i < n; i++) {
                Integer v = today.stars.get(i);
                row[i] = starStateFromServer(v != null ? v : 1);
            }
        }
        logStarMapping(row);
        return row;
    }

    private void logStarMapping(int[] row) {
        StringBuilder sb = new StringBuilder("star map");
        if (today != null && today.stars != null) {
            sb.append(" server=").append(today.stars);
        } else {
            sb.append(" server=null");
        }
        sb.append(" ui=");
        for (int i = 0; i < row.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(describeUiStar(row[i]));
        }
        if (participation != null && participation.today != null) {
            sb.append(" todayAm=").append(participation.today.amDone)
                    .append(" todayPm=").append(participation.today.pmDone)
                    .append(" eventCount=").append(participation.today.eventCount);
        }
        Log.i("TMindToday", sb.toString());
    }

    private static String describeUiStar(int state) {
        if (state == MissionManager.STAR_BONUS) return "가득+링";
        if (state == MissionManager.STAR_FULL) return "가득";
        if (state == MissionManager.STAR_HALF_BONUS) return "왼쪽+링";
        if (state == MissionManager.STAR_HALF_RIGHT_BONUS) return "오른쪽+링";
        if (state == MissionManager.STAR_HALF_RIGHT) return "오른쪽";
        if (state == MissionManager.STAR_HALF) return "왼쪽";
        return "빈";
    }

    /** 1=빈, 2=한쪽, 3=한쪽+효과, 4=가득, 5=가득+효과. */
    private static int starStateFromServer(int serverStar) {
        switch (serverStar) {
            case 2:
                return MissionManager.STAR_HALF;
            case 3:
                return MissionManager.STAR_HALF_BONUS;
            case 4:
                return MissionManager.STAR_FULL;
            case 5:
                return MissionManager.STAR_BONUS;
            default:
                return MissionManager.STAR_EMPTY;
        }
    }

    private void setupMissionCard(View card, int iconRes, int titleRes, int subRes) {
        ImageView icon = card.findViewById(R.id.ivIcon);
        icon.setImageResource(iconRes);
        icon.clearColorFilter();
        ((TextView) card.findViewById(R.id.tvMissionTitle)).setText(titleRes);
        ((TextView) card.findViewById(R.id.tvMissionSub)).setText(subRes);
    }

    private void applyCardState(View card, boolean completed, int activeBg) {
        TextView title = card.findViewById(R.id.tvMissionTitle);
        TextView sub = card.findViewById(R.id.tvMissionSub);
        View check = card.findViewById(R.id.checkDone);
        ImageView arrow = card.findViewById(R.id.ivArrow);
        ImageView icon = card.findViewById(R.id.ivIcon);

        card.setClickable(true);
        card.setFocusable(true);
        card.setEnabled(true);
        card.setAlpha(1f);

        if (completed) {
            card.setBackgroundResource(R.drawable.bg_mission_card_completed);
            title.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            sub.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            // 완료 카드(밝은 배경) → 진한 녹색 아이콘
            icon.setColorFilter(ContextCompat.getColor(this, R.color.teal_dark),
                    android.graphics.PorterDuff.Mode.SRC_IN);
            check.setVisibility(View.VISIBLE);
            arrow.setVisibility(View.GONE);
        } else {
            card.setBackgroundResource(activeBg);
            title.setTextColor(ContextCompat.getColor(this, R.color.white));
            sub.setTextColor(ContextCompat.getColor(this, R.color.white));
            sub.setAlpha(1f);
            // 활성 카드 → 흰색 아이콘 (배경색과 선명하게 대비)
            icon.setColorFilter(ContextCompat.getColor(this, R.color.white),
                    android.graphics.PorterDuff.Mode.SRC_IN);
            check.setVisibility(View.GONE);
            arrow.setVisibility(View.VISIBLE);
            arrow.setImageResource(R.drawable.ic_chevron_white);
        }
    }
}
