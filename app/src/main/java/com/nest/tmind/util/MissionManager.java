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

    /** 0=빈, 1=반, 2=가득, 3=가득+특수(추가측정) */
    public static final int STAR_EMPTY = 0;
    public static final int STAR_HALF = 1;
    public static final int STAR_FULL = 2;
    public static final int STAR_BONUS = 3;

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
        sp.edit().putBoolean(k(getActiveSession(), "hrv"), true).commit();
        updateStarsAfterProgress();
    }

    public void setEmaDone() {
        sp.edit().putBoolean(k(getActiveSession(), "ema"), true).commit();
        updateStarsAfterProgress();
    }

    public void setDiaryDone() {
        Session active = getActiveSession();
        sp.edit().putBoolean(k(active, "diary"), true).commit();
        if (active == Session.EVENT && isSessionAllDone(Session.EVENT)) {
            recordAdditionalCompletion();
        }
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

    private void updateStarsAfterProgress() {
        int dayIndex = studyStarIndex();
        if (dayIndex < 0 || dayIndex > 6) return;

        boolean am = isSessionAllDone(Session.MORNING);
        boolean pm = isSessionAllDone(Session.AFTERNOON);
        boolean bonus = getAdditionalCompleteCount() >= 1;

        int state = STAR_EMPTY;
        if (am && pm) {
            state = bonus ? STAR_BONUS : STAR_FULL;
        } else if (am || pm) {
            // 반개 + 추가 완료 시에도 특수 효과
            state = bonus ? STAR_BONUS : STAR_HALF;
        } else if (bonus) {
            state = STAR_BONUS;
        }
        setStarState(dayIndex, state);
    }

    /** 연구 시작일 기준 0~6 (왼쪽부터 순차). 요일과 무관. */
    public int studyStarIndex() {
        SessionManager sm = new SessionManager(appCtx);
        int idx = sm.getStudyDayIndex();
        if (idx < 0) return 0;
        if (idx > 6) return 6;
        return idx;
    }

    /** @deprecated 별은 studyStarIndex 사용 */
    public static int dayOfWeekIndex() {
        int cal = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return (cal + 5) % 7;
    }

    public int getStarState(int dayIndex) {
        return sp.getInt(weekKey() + "_star_" + dayIndex, STAR_EMPTY);
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
