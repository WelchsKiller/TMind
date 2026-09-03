package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
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

    private View cardHrv, cardEma, cardDiary, cardAdditional;
    private TextView tvGreeting, tvProgress, tvAdditionalSub;
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
        tvProgress = findViewById(R.id.tvProgress);
        cardHrv = findViewById(R.id.cardHrv);
        cardEma = findViewById(R.id.cardEma);
        cardDiary = findViewById(R.id.cardDiary);
        cardAdditional = findViewById(R.id.cardAdditional);
        tvAdditionalSub = findViewById(R.id.tvAdditionalSub);
        progressBar = findViewById(R.id.progressBar);
        starRow = findViewById(R.id.starRow);
        btnWeeklyTrend = findViewById(R.id.btnWeeklyTrend);

        setupMissionCard(cardHrv, R.drawable.ic_heart, R.string.mission_hrv, R.string.mission_hrv_sub);
        setupMissionCard(cardEma, R.drawable.ic_survey, R.string.mission_ema, R.string.mission_ema_sub);
        setupMissionCard(cardDiary, R.drawable.ic_diary, R.string.mission_diary, R.string.mission_diary_active);

        tvGreeting.setText(R.string.dashboard_greeting);
        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvGreeting, R.id.tvProgress);

        cardHrv.setOnClickListener(v -> onHrvClick());
        cardEma.setOnClickListener(v -> onEmaClick());
        cardDiary.setOnClickListener(v -> onDiaryClick());
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
                .setPositiveButton(R.string.dialog_yes, (d, w) -> logoutToLogin())
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
        logoutToLogin();
    }

    private void logoutToLogin() {
        MemberApiManager.logout(this, new MemberApiManager.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
            }

            @Override
            public void onError(String message) {
            }
        });
        session.logoutForNewParticipant();
        Intent i = new Intent(this, LoginActivity.class);
        i.putExtra(LoginActivity.EXTRA_FORCE_REGISTER, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void confirmExitIfMissionsIncomplete() {
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
        // 진행 중인 Event 가 없으면 예측이 끝난 정규 세션 ID 를 재사용하지 않고 새로 연다.
        if (mission.hasEventInProgress()) {
            MemberApiManager.ensureSessionStarted(this, true, afterEvent);
        } else {
            MemberApiManager.startFreshEventSession(this, afterEvent);
        }
    }

    private void proceedAdditionalMeasure() {
        // 추가 측정도 심박 → 설문 → 일기 순서.
        MissionManager.Session event = MissionManager.Session.EVENT;
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

    private void onHrvClick() {
        mission.setAdditionalMeasureMode(false);
        // VALID 만 측정 완료. SKIPPED 등 VALID 가 아니면 재측정.
        if (isHrvValid()) {
            Toast.makeText(this, R.string.mission_already_done, Toast.LENGTH_SHORT).show();
        } else {
            openHrv();
        }
    }

    private void onEmaClick() {
        mission.setAdditionalMeasureMode(false);
        if (isEmaDone()) {
            Toast.makeText(this, R.string.mission_already_done, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isHrvStageDone()) {
            Toast.makeText(this, R.string.mission_need_hrv_first, Toast.LENGTH_LONG).show();
            return;
        }
        openEma();
    }

    private void onDiaryClick() {
        mission.setAdditionalMeasureMode(false);
        if (isDiaryDone()) {
            Toast.makeText(this, R.string.mission_already_done, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isHrvStageDone()) {
            Toast.makeText(this, R.string.mission_need_hrv_first, Toast.LENGTH_LONG).show();
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
        if (today != null && today.currentType != null) {
            switch (today.currentType.trim().toUpperCase()) {
                case "PM":
                case "AFTERNOON":
                    return EmaQuestionBank.SessionType.AFTERNOON;
                default:
                    return EmaQuestionBank.SessionType.MORNING;
            }
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

        applyCardState(cardHrv, isHrvStageDone(), R.drawable.bg_mission_card_active);
        applyCardState(cardEma, isEmaDone(), R.drawable.bg_mission_card_ema);
        applyCardState(cardDiary, isDiaryDone(), R.drawable.bg_mission_card_diary);

        TextView hrvSub = cardHrv.findViewById(R.id.tvMissionSub);
        if (isHrvStageDone() && !isHrvValid()) {
            hrvSub.setText("다시 측정해 주세요");
        } else {
            hrvSub.setText(R.string.mission_hrv_sub);
        }
        ((TextView) cardEma.findViewById(R.id.tvMissionSub)).setText(R.string.mission_ema_sub);
        ((TextView) cardDiary.findViewById(R.id.tvMissionSub)).setText(R.string.mission_diary_active);

        btnWeeklyTrend.setVisibility(participatedDays() >= 7 ? View.VISIBLE : View.GONE);

        refreshAdditionalCard();
        renderStars();
    }

    private void refreshAdditionalCard() {
        boolean active = isEventAvailable();
        cardAdditional.setEnabled(active);
        cardAdditional.setClickable(active);
        cardAdditional.setAlpha(active ? 1f : 0.45f);
        String guide = today != null ? today.eventGuideText : null;
        if (guide != null && !guide.trim().isEmpty()) {
            tvAdditionalSub.setText(guide.trim());
        } else if (active) {
            tvAdditionalSub.setText(R.string.additional_measure_sub);
        } else {
            tvAdditionalSub.setText(additionalUnavailableMessage());
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

    private boolean isHrvStageDone() {
        return today != null && Boolean.TRUE.equals(today.hrvDone);
    }

    private boolean isHrvValid() {
        return today != null && today.hrvStatus != null
                && "VALID".equalsIgnoreCase(today.hrvStatus.trim());
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
        return isHrvStageDone() && isEmaDone() && isDiaryDone();
    }

    private int todayCompletedCount() {
        if (today == null) return 0;
        int n = 0;
        if (Boolean.TRUE.equals(today.hrvDone)) n++;
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
                            btnWeeklyTrend.setVisibility(
                                    participatedDays() >= 7 ? View.VISIBLE : View.GONE);
                            renderStars();
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
        MissionManager.Session main = mapCurrentType(data.currentType);
        mission.syncFromServer(main,
                Boolean.TRUE.equals(data.hrvDone),
                Boolean.TRUE.equals(data.emaDone),
                Boolean.TRUE.equals(data.diaryDone));
        if (data.missedType != null && !data.missedType.trim().isEmpty()) {
            tvGreeting.setText(getString(R.string.dashboard_missed_session, data.missedType));
        } else {
            tvGreeting.setText(R.string.dashboard_greeting);
        }
        refreshUi();
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
        int pad = (int) (3 * d);
        int[] states = starStatesFromParticipation();
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
            } else if (state == MissionManager.STAR_HALF) {
                iv.setImageResource(R.drawable.ic_star_half);
            } else {
                iv.setImageResource(R.drawable.ic_star_empty);
            }
            starRow.addView(iv);
        }
    }

    /** 참여 현황 days 의 amDone / pmDone / eventDone 으로 별 7칸을 채운다. */
    private int[] starStatesFromParticipation() {
        int[] row = new int[7];
        if (participation == null || participation.period == null
                || participation.period.days == null || participation.period.days.isEmpty()) {
            return row;
        }
        java.util.List<ApiModels.DayStatus> days = new java.util.ArrayList<>(participation.period.days);
        java.util.Collections.sort(days, (a, b) -> {
            String da = a != null && a.date != null ? a.date : "";
            String db = b != null && b.date != null ? b.date : "";
            return da.compareTo(db);
        });
        int filled = 0;
        for (int i = 0; i < days.size() && filled < 7; i++) {
            int state = starStateFromDay(days.get(i));
            if (state != MissionManager.STAR_EMPTY) {
                row[filled++] = state;
            }
        }
        return row;
    }

    private static int starStateFromDay(ApiModels.DayStatus day) {
        if (day == null) return MissionManager.STAR_EMPTY;
        boolean am = day.amDone;
        boolean pm = day.pmDone;
        boolean bonus = day.eventDone;
        if (am && pm) {
            return bonus ? MissionManager.STAR_BONUS : MissionManager.STAR_FULL;
        }
        if (am ^ pm) {
            return bonus ? MissionManager.STAR_HALF_BONUS : MissionManager.STAR_HALF;
        }
        return MissionManager.STAR_EMPTY;
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
