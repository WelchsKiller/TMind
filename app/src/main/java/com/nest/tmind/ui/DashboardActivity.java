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
    private boolean serverEventActive = true;
    private int serverParticipatedDays = -1;

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
        mission.setAdditionalMeasureMode(false);

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

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExitIfMissionsIncomplete();
            }
        });

        session.saveScreen("dashboard");
        syncServerState();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mission = new MissionManager(this);
        // 추가 측정 진행 중이면 모드 유지
        if (!mission.hasEventInProgress()) {
            mission.setAdditionalMeasureMode(false);
        }
        mission.recalcStars();
        syncServerState();
        refreshUi();
    }

    private void confirmNewParticipant() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.new_participant_confirm)
                .setPositiveButton(R.string.dialog_yes, (d, w) -> {
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
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void confirmExitIfMissionsIncomplete() {
        if (mission.isAllDone() || session.isStudyEnded()) {
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
        if (!serverEventActive) {
            Toast.makeText(this, "추가 측정은 현재 사용할 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isAdditionalUnlocked()) {
            Toast.makeText(this, R.string.additional_measure_need_hrv, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!mission.canStartAdditional() && !mission.hasEventInProgress()) {
            Toast.makeText(this, R.string.additional_measure_max, Toast.LENGTH_SHORT).show();
            return;
        }
        mission.setAdditionalMeasureMode(true);
        MemberApiManager.ensureSessionStarted(this, true, new MemberApiManager.ResultCallback<Long>() {
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
        });
    }

    private void proceedAdditionalMeasure() {
        // 추가 측정: 심박변이도 → 마음상태 → 마음일기
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

    /** 오늘 오전/오후 중 심박변이도 미션을 한 번이라도 완료하면 추가 측정 가능 */
    private boolean isAdditionalUnlocked() {
        return mission.isHrvDone(MissionManager.Session.MORNING)
                || mission.isHrvDone(MissionManager.Session.AFTERNOON);
    }

    private void onHrvClick() {
        mission.setAdditionalMeasureMode(false);
        // hrvDone 이어도 hrvStatus 가 VALID 가 아니면 다시 측정 가능
        if (mission.isHrvDone() && session.isRemoteHrvValid()) {
            Toast.makeText(this, R.string.mission_already_done, Toast.LENGTH_SHORT).show();
        } else {
            openHrv();
        }
    }

    private void onEmaClick() {
        mission.setAdditionalMeasureMode(false);
        if (mission.isEmaDone()) {
            Toast.makeText(this, R.string.mission_already_done, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!canOpenFollowUpMission()) {
            Toast.makeText(this, R.string.mission_need_hrv_first, Toast.LENGTH_LONG).show();
            return;
        }
        openEma();
    }

    private void onDiaryClick() {
        mission.setAdditionalMeasureMode(false);
        if (mission.isDiaryDone()) {
            Toast.makeText(this, R.string.mission_already_done, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!canOpenFollowUpMission()) {
            Toast.makeText(this, R.string.mission_need_hrv_first, Toast.LENGTH_LONG).show();
            return;
        }
        if (!mission.isEmaDone()) {
            Toast.makeText(this, R.string.mission_need_ema_first, Toast.LENGTH_LONG).show();
            return;
        }
        openDiary();
    }

    private boolean canOpenFollowUpMission() {
        boolean event = mission.isAdditionalMeasureMode();
        long sessionId = MemberApiManager.getCurrentSessionId(this, event);
        if (sessionId <= 0) return false;
        MissionManager.Session active = mission.getActiveSession();
        return mission.isHrvDone(active) || session.isRemoteHrvDone();
    }

    private void openHrv() {
        startActivity(new Intent(this, HrvGuideActivity.class));
    }

    private void openEma() {
        boolean event = mission.isAdditionalMeasureMode();
        Intent i = new Intent(this, EmaIntroActivity.class);
        i.putExtra(EmaSurveyActivity.EXTRA_SESSION_TYPE, mapEmaSession().name());
        startActivity(i);
    }

    private EmaQuestionBank.SessionType mapEmaSession() {
        switch (mission.getActiveSession()) {
            case AFTERNOON:
                return EmaQuestionBank.SessionType.AFTERNOON;
            case EVENT:
                return EmaQuestionBank.SessionType.EVENT;
            default:
                return EmaQuestionBank.SessionType.MORNING;
        }
    }

    private void openDiary() {
        startActivity(new Intent(this, VoiceDiaryActivity.class));
    }

    private void refreshUi() {
        // 메인 카드는 항상 오전/오후 기준 (추가 측정 진행 중에도)
        MissionManager.Session active = MissionManager.mainSessionByHour();
        int done = mission.getCompletedCount(active);
        tvProgress.setText(getString(R.string.mission_progress_session,
                mission.sessionLabel(active), done));
        progressBar.setMax(3);
        progressBar.setProgress(done);

        applyCardState(cardHrv, mission.isHrvDone(active), R.drawable.bg_mission_card_active);
        applyCardState(cardEma, mission.isEmaDone(active), R.drawable.bg_mission_card_ema);
        applyCardState(cardDiary, mission.isDiaryDone(active), R.drawable.bg_mission_card_diary);

        // 완료 여부는 체크만 표시 — 부제목은 고정(일관). 다만 HRV 무효/건너뛰기면 재측정 안내
        TextView hrvSub = cardHrv.findViewById(R.id.tvMissionSub);
        if (mission.isHrvDone(active) && !session.isRemoteHrvValid()) {
            hrvSub.setText("다시 측정해 주세요");
        } else {
            hrvSub.setText(R.string.mission_hrv_sub);
        }
        ((TextView) cardEma.findViewById(R.id.tvMissionSub)).setText(R.string.mission_ema_sub);
        ((TextView) cardDiary.findViewById(R.id.tvMissionSub)).setText(R.string.mission_diary_active);

        btnWeeklyTrend.setVisibility(
                serverParticipatedDays >= 7 ? View.VISIBLE : View.GONE);

        refreshAdditionalCard();
        renderStars();
    }

    private void refreshAdditionalCard() {
        boolean unlocked = isAdditionalUnlocked();
        boolean canMore = serverEventActive && (mission.canStartAdditional() || mission.hasEventInProgress());
        boolean active = unlocked && canMore;
        cardAdditional.setEnabled(active);
        cardAdditional.setClickable(true);
        cardAdditional.setAlpha(active ? 1f : 0.45f);
        String guide = session.getEventGuideText();
        if (!unlocked) {
            tvAdditionalSub.setText(R.string.additional_measure_locked);
        } else if (!serverEventActive) {
            tvAdditionalSub.setText("추가 측정은 현재 사용할 수 없습니다.");
        } else if (!canMore) {
            tvAdditionalSub.setText(R.string.additional_measure_max);
        } else if (guide != null && !guide.trim().isEmpty()) {
            tvAdditionalSub.setText(guide.trim());
        } else {
            int n = mission.getAdditionalCompleteCount();
            tvAdditionalSub.setText(getString(R.string.additional_measure_sub_count,
                    n, MissionManager.MAX_ADDITIONAL_PER_DAY));
        }
        if (active) {
            cardAdditional.setBackgroundResource(R.drawable.bg_btn_outline);
        } else {
            cardAdditional.setBackgroundResource(R.drawable.bg_mission_card_completed);
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
                            if (data != null && data.period != null) {
                                serverParticipatedDays = data.period.participatedDays;
                                btnWeeklyTrend.setVisibility(
                                        serverParticipatedDays >= 7 ? View.VISIBLE : View.GONE);
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> btnWeeklyTrend.setVisibility(View.GONE));
                    }
                });
    }

    private void applyTodayResponse(ApiModels.TodayResponse data) {
        if (data == null) return;
        serverEventActive = data.eventActive;
        if (data.participatedDays != null) {
            serverParticipatedDays = data.participatedDays;
        }
        MissionManager.Session main = mapCurrentType(data.currentType);
        if (Boolean.TRUE.equals(data.hrvDone)) {
            mission.setHrvDone(main);
        }
        if (Boolean.TRUE.equals(data.emaDone)) {
            mission.setEmaDone(main);
        }
        if (Boolean.TRUE.equals(data.diaryDone)) {
            mission.setDiaryDone(main);
        }
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
        for (int i = 0; i < 7; i++) {
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(pad, 0, pad, 0);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setAdjustViewBounds(true);
            int state = mission.getStarState(i);
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
