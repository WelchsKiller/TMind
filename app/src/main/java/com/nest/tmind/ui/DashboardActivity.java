package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.nest.tmind.R;
import com.nest.tmind.util.EmaQuestionBank;
import com.nest.tmind.util.MissionManager;
import com.nest.tmind.util.SessionManager;

/** APP-USR-002: 오늘 할 일 — 오전/오후/이벤트 세션별 미션 + 주간 별 */
public class DashboardActivity extends BaseSeniorActivity {

    private MissionManager mission;
    private SessionManager session;

    private View cardHrv, cardEma, cardDiary;
    private TextView tvGreeting, tvProgress;
    private TextView chipMorning, chipEvent, chipAfternoon;
    private LinearLayout starRow;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
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
        progressBar = findViewById(R.id.progressBar);
        chipMorning = findViewById(R.id.chipMorning);
        chipEvent = findViewById(R.id.chipEvent);
        chipAfternoon = findViewById(R.id.chipAfternoon);
        starRow = findViewById(R.id.starRow);

        setupMissionCard(cardHrv, R.drawable.ic_heart, R.string.mission_hrv, R.string.mission_hrv_sub);
        setupMissionCard(cardEma, R.drawable.ic_survey, R.string.mission_ema, R.string.mission_ema_sub);
        setupMissionCard(cardDiary, R.drawable.ic_diary, R.string.mission_diary, R.string.mission_diary_active);

        tvGreeting.setText(getString(R.string.dashboard_greeting, session.getUserName()));
        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvGreeting, R.id.tvProgress);

        chipMorning.setOnClickListener(v -> switchSession(MissionManager.Session.MORNING));
        chipEvent.setOnClickListener(v -> switchSession(MissionManager.Session.EVENT));
        chipAfternoon.setOnClickListener(v -> switchSession(MissionManager.Session.AFTERNOON));

        cardHrv.setOnClickListener(v -> onHrvClick());
        cardEma.setOnClickListener(v -> onEmaClick());
        cardDiary.setOnClickListener(v -> onDiaryClick());

        findViewById(R.id.btnHistorySurvey).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryListActivity.class)
                        .putExtra(HistoryListActivity.EXTRA_MODE, HistoryListActivity.MODE_SURVEY)));
        findViewById(R.id.btnHistoryAnalysis).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryListActivity.class)
                        .putExtra(HistoryListActivity.EXTRA_MODE, HistoryListActivity.MODE_ANALYSIS)));

        session.saveScreen("dashboard");
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mission = new MissionManager(this);
        refreshUi();
    }

    private void switchSession(MissionManager.Session s) {
        MissionManager.Session prev = mission.getActiveSession();
        mission.setActiveSession(s);
        // 새 세션으로 전환 시 카드 색/체크는 세션별 상태로 다시 그림(초기화 효과)
        if (prev != s) {
            session.saveEmaProgress(0, new int[16]);
        }
        refreshUi();
    }

    private void onHrvClick() {
        if (mission.isHrvDone()) {
            showRedoDialog(R.string.redo_hrv_message, () -> {
                mission.clearHrv();
                openHrv();
            });
        } else {
            openHrv();
        }
    }

    private void onEmaClick() {
        if (mission.isEmaDone()) {
            showRedoDialog(R.string.redo_ema_message, () -> {
                mission.clearEma();
                session.saveEmaProgress(0, new int[16]);
                openEma();
            });
        } else {
            openEma();
        }
    }

    private void onDiaryClick() {
        if (mission.isDiaryDone()) {
            showRedoDialog(R.string.redo_diary_message, () -> {
                mission.clearDiary();
                openDiary();
            });
        } else {
            openDiary();
        }
    }

    private void openHrv() {
        startActivity(new Intent(this, HrvGuideActivity.class));
    }

    private void openEma() {
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

    private void showRedoDialog(int messageRes, Runnable onYes) {
        new AlertDialog.Builder(this)
                .setMessage(messageRes)
                .setPositiveButton(R.string.dialog_yes, (d, w) -> onYes.run())
                .setNegativeButton(R.string.dialog_no, null)
                .show();
    }

    private void refreshUi() {
        MissionManager.Session active = mission.getActiveSession();
        int done = mission.getCompletedCount(active);
        tvProgress.setText(getString(R.string.mission_progress_session,
                mission.sessionLabel(active), done));
        progressBar.setMax(3);
        progressBar.setProgress(done);

        styleChip(chipMorning, active == MissionManager.Session.MORNING);
        styleChip(chipEvent, active == MissionManager.Session.EVENT);
        styleChip(chipAfternoon, active == MissionManager.Session.AFTERNOON);

        applyCardState(cardHrv, mission.isHrvDone(active), R.drawable.bg_mission_card_active);
        applyCardState(cardEma, mission.isEmaDone(active), R.drawable.bg_mission_card_ema);
        applyCardState(cardDiary, mission.isDiaryDone(active), R.drawable.bg_mission_card_diary);

        TextView diarySub = cardDiary.findViewById(R.id.tvMissionSub);
        diarySub.setText(mission.isDiaryDone(active)
                ? R.string.mission_diary_sub
                : R.string.mission_diary_active);

        renderStars();
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected
                ? R.drawable.bg_session_chip_selected
                : R.drawable.bg_session_chip);
        chip.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.white : R.color.teal_dark));
    }

    private void renderStars() {
        starRow.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (28 * d);
        int pad = (int) (4 * d);
        for (int i = 0; i < 7; i++) {
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(pad, 0, pad, 0);
            iv.setLayoutParams(lp);
            int state = mission.getStarState(i);
            if (state >= 2) {
                iv.setImageResource(R.drawable.ic_star_gold);
            } else if (state == 1) {
                iv.setImageResource(R.drawable.ic_star_filled);
            } else {
                iv.setImageResource(R.drawable.ic_star_empty);
            }
            starRow.addView(iv);
        }
    }

    private void setupMissionCard(View card, int iconRes, int titleRes, int subRes) {
        ((ImageView) card.findViewById(R.id.ivIcon)).setImageResource(iconRes);
        ((TextView) card.findViewById(R.id.tvMissionTitle)).setText(titleRes);
        ((TextView) card.findViewById(R.id.tvMissionSub)).setText(subRes);
    }

    private void applyCardState(View card, boolean completed, int activeBg) {
        TextView title = card.findViewById(R.id.tvMissionTitle);
        TextView sub = card.findViewById(R.id.tvMissionSub);
        View check = card.findViewById(R.id.checkDone);
        ImageView arrow = card.findViewById(R.id.ivArrow);

        card.setClickable(true);
        card.setFocusable(true);
        card.setEnabled(true);
        card.setAlpha(1f);

        if (completed) {
            card.setBackgroundResource(R.drawable.bg_mission_card_completed);
            title.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            sub.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            check.setVisibility(View.VISIBLE);
            arrow.setVisibility(View.GONE);
        } else {
            card.setBackgroundResource(activeBg);
            title.setTextColor(ContextCompat.getColor(this, R.color.white));
            sub.setTextColor(ContextCompat.getColor(this, R.color.white));
            sub.setAlpha(0.92f);
            check.setVisibility(View.GONE);
            arrow.setVisibility(View.VISIBLE);
            arrow.setImageResource(R.drawable.ic_chevron_white);
        }
    }
}
