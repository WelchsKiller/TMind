package com.nest.tmind.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 오늘 미션: 오전/오후는 시각으로 자동 결정, 추가는 EVENT 세션.
 * 별: 연구 일차(왼쪽부터 순차). 오전 반개 + 오후 반개 = 가득.
 * 추가 측정 1회 이상 완료 시 특수(금) 별. 추가 측정은 하루 최대 5회.
 */
public class MissionManager {

    public enum Session {
        MORNING, AFTERNOON, EVENT
    }

    /** 0=빈, 1=반, 2=가득, 3=가득+특수, 4=반+특수(추가) */
    public static final int STAR_EMPTY = 0;
    public static final int STAR_HALF = 1;
    public static final int STAR_FULL = 2;
    public static final int STAR_BONUS = 3;
    public static final int STAR_HALF_BONUS = 4;

    public static final int MAX_ADDITIONAL_PER_DAY = 5;

    private static final String PREF = "tmind_mission_v2";

    private final SharedPreferences sp;
    private final String todayKey;
    private final Context appCtx;

    public MissionManager(Context ctx) {
        appCtx = ctx.getApplicationContext();
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        todayKey = new SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(new Date());
        syncMainSessionByHour();
    }

    /** 메인 미션용: 오전/오후만 (12시 기준). 추가는 별도. */
    public static Session mainSessionByHour() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour < 12 ? Session.MORNING : Session.AFTERNOON;
    }

    /** @deprecated 메인 미션은 mainSessionByHour 사용. EVENT는 추가 측정 전용. */
    public static Session currentSessionByHour() {
        return mainSessionByHour();
    }

    public void syncMainSessionByHour() {
        Session main = mainSessionByHour();
        String forced = sp.getString(todayKey + "_force_event", null);
        if ("1".equals(forced)) {
            sp.edit().putString(todayKey + "_active_session", Session.EVENT.name()).apply();
        } else {
            sp.edit().putString(todayKey + "_active_session", main.name()).apply();
        }
    }

    /** 추가 측정 모드 진입/해제 */
    public void setAdditionalMeasureMode(boolean on) {
        sp.edit().putString(todayKey + "_force_event", on ? "1" : null)
                .putString(todayKey + "_active_session",
                        on ? Session.EVENT.name() : mainSessionByHour().name())
                .commit();
    }

    public boolean isAdditionalMeasureMode() {
        return "1".equals(sp.getString(todayKey + "_force_event", null));
    }

    /** EVENT 세션에 진행 중인(일부 완료) 추가 측정이 있는지 */
    public boolean hasEventInProgress() {
        Session e = Session.EVENT;
        int done = getCompletedCount(e);
        return done > 0 && done < 3;
    }

    public Session getActiveSession() {
        syncMainSessionByHour();
        String v = sp.getString(todayKey + "_active_session", Session.MORNING.name());
        try {
            return Session.valueOf(v);
        } catch (Exception e) {
            return mainSessionByHour();
        }
    }

    public void setActiveSession(Session session) {
        if (session == Session.EVENT) {
            setAdditionalMeasureMode(true);
        } else {
            setAdditionalMeasureMode(false);
        }
    }

    private String k(Session s, String suffix) {
        return todayKey + "_" + s.name() + "_" + suffix;
    }

    public boolean isHrvDone() {
        return isHrvDone(getActiveSession());
    }

    public boolean isEmaDone() {
        return isEmaDone(getActiveSession());
    }

    public boolean isDiaryDone() {
        return isDiaryDone(getActiveSession());
    }

    public boolean isHrvDone(Session s) {
        return sp.getBoolean(k(s, "hrv"), false);
    }

    public boolean isEmaDone(Session s) {
        return sp.getBoolean(k(s, "ema"), false);
    }

    public boolean isDiaryDone(Session s) {
        return sp.getBoolean(k(s, "diary"), false);
    }

    public void setHrvDone() {
        setHrvDone(getActiveSession());
    }

    public void setHrvDone(Session s) {
        sp.edit().putBoolean(k(s, "hrv"), true).commit();
        updateStarsAfterProgress();
    }

    public void clearHrv(Session s) {
        sp.edit().putBoolean(k(s, "hrv"), false).commit();
        updateStarsAfterProgress();
    }

    public void setEmaDone() {
        setEmaDone(getActiveSession());
    }

    public void setEmaDone(Session s) {
        sp.edit().putBoolean(k(s, "ema"), true).commit();
        updateStarsAfterProgress();
    }

    public void setDiaryDone() {
        setDiaryDone(getActiveSession());
    }

    public void setDiaryDone(Session s) {
        sp.edit().putBoolean(k(s, "diary"), true).commit();
        if (s == Session.EVENT && isSessionAllDone(Session.EVENT)) {
            recordAdditionalCompletion();
        }
        updateStarsAfterProgress();
    }

    /** /today 의 hrvDone · emaDone · diaryDone 을 그대로 반영한다. */
    public void syncFromServer(Session s, boolean hrvDone, boolean emaDone, boolean diaryDone) {
        sp.edit()
                .putBoolean(k(s, "hrv"), hrvDone)
                .putBoolean(k(s, "ema"), emaDone)
                .putBoolean(k(s, "diary"), diaryDone)
                .commit();
        updateStarsAfterProgress();
    }

    public void clearHrv() {
        sp.edit().putBoolean(k(getActiveSession(), "hrv"), false).apply();
    }

    public void clearEma() {
        sp.edit().putBoolean(k(getActiveSession(), "ema"), false).apply();
    }

    public void clearDiary() {
        sp.edit().putBoolean(k(getActiveSession(), "diary"), false).apply();
    }

    public void clearEventMissions() {
        Session s = Session.EVENT;
        sp.edit()
                .putBoolean(k(s, "hrv"), false)
                .putBoolean(k(s, "ema"), false)
                .putBoolean(k(s, "diary"), false)
                .apply();
    }

    public void resetActiveSessionMissions() {
        Session s = getActiveSession();
        sp.edit()
                .putBoolean(k(s, "hrv"), false)
                .putBoolean(k(s, "ema"), false)
                .putBoolean(k(s, "diary"), false)
                .apply();
    }

    public int getCompletedCount() {
        return getCompletedCount(getActiveSession());
    }

    public int getCompletedCount(Session s) {
        int c = 0;
        if (isHrvDone(s)) c++;
        if (isEmaDone(s)) c++;
        if (isDiaryDone(s)) c++;
        return c;
    }

    public boolean isAllDone() {
        return getCompletedCount() >= 3;
    }

    public boolean isSessionAllDone(Session s) {
        return getCompletedCount(s) >= 3;
    }

    public int getAdditionalCompleteCount() {
        return sp.getInt(todayKey + "_additional_count", 0);
    }

    public boolean canStartAdditional() {
        return getAdditionalCompleteCount() < MAX_ADDITIONAL_PER_DAY;
    }

    private void recordAdditionalCompletion() {
        int n = getAdditionalCompleteCount();
        if (n < MAX_ADDITIONAL_PER_DAY) {
            sp.edit().putInt(todayKey + "_additional_count", n + 1).apply();
        }
    }

    /** 대시보드 진입 시 오늘 별 상태를 규칙에 맞게 재계산 */
    public void recalcStars() {
        updateStarsAfterProgress();
    }

    private void updateStarsAfterProgress() {
        int dayIndex = studyStarIndex();
        if (dayIndex < 0 || dayIndex > 6) return;

        boolean am = isSessionAllDone(Session.MORNING);
        boolean pm = isSessionAllDone(Session.AFTERNOON);
        boolean bonus = getAdditionalCompleteCount() >= 1;

        int state = STAR_EMPTY;
        if (am && pm) {
            // 오전+오후 모두 완료 → 가득, 추가까지 하면 특수 별
            state = bonus ? STAR_BONUS : STAR_FULL;
        } else if (am ^ pm) {
            // 한쪽만 완료 → 반별. 추가 측정 시 가득 찬 별이 아닌 반별+효과
            state = bonus ? STAR_HALF_BONUS : STAR_HALF;
        }
        // 추가만으로는 별을 채우지 않음 (am, pm 모두 false)
        setStarState(dayIndex, state);
    }

    /**
     * 연구 시작일 기준 일차. 0~6 만 별로 기록되고 7 이상은 연구 기간을 벗어난 것이다.
     * 6 으로 잘라내면 8일차 이후 진행이 계속 마지막 별을 덮어쓰므로 원값을 그대로 준다.
     */
    public int studyStarIndex() {
        return new SessionManager(appCtx).getStudyDayIndex();
    }

    /** @deprecated 별은 studyStarIndex 사용 */
    public static int dayOfWeekIndex() {
        int cal = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return (cal + 5) % 7;
    }

    public int getStarState(int dayIndex) {
        return sp.getInt(weekKey() + "_star_" + dayIndex, STAR_EMPTY);
    }

    /**
     * 표시용 별 7칸. 어떤 날을 빠뜨렸든 항상 왼쪽부터 채워 보이도록, 빈 별을 건너뛰고
     * 날짜 순서를 유지한 채 앞으로 당긴다. 일차별 기록 자체는 그대로 남는다.
     */
    public int[] getStarRow() {
        int[] row = new int[7];
        int filled = 0;
        for (int day = 0; day < 7; day++) {
            int state = getStarState(day);
            if (state != STAR_EMPTY) {
                row[filled++] = state;
            }
        }
        return row;
    }

    private void setStarState(int dayIndex, int state) {
        sp.edit().putInt(weekKey() + "_star_" + dayIndex, state).apply();
    }

    private String weekKey() {
        // 연구 주간 키: 연구 시작일 기준 (요일 월요일 고정 아님)
        SessionManager sm = new SessionManager(appCtx);
        long start = sm.getStudyStartMs();
        if (start <= 0) start = System.currentTimeMillis();
        return new SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(new Date(start)) + "_study";
    }

    public String sessionLabel(Session s) {
        switch (s) {
            case MORNING:
                return "오전";
            case AFTERNOON:
                return "오후";
            default:
                return "추가";
        }
    }
}
