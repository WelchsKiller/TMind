package com.nest.tmind.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 오늘 미션: 오전 / 오후 / 이벤트(수시) 세션별 3종(HRV·EMA·일기).
 * 주간 별: 오전 또는 오후 완료 시 별 채움, 3세션 모두 완료 시 금별.
 */
public class MissionManager {

    public enum Session {
        MORNING, AFTERNOON, EVENT
    }

    private static final String PREF = "tmind_mission_v2";

    private final SharedPreferences sp;
    private final String todayKey;

    public MissionManager(Context ctx) {
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        todayKey = new SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(new Date());
        ensureSessionMigrated();
    }

    private void ensureSessionMigrated() {
        String active = sp.getString(todayKey + "_active_session", null);
        if (active == null) {
            setActiveSession(currentSessionByHour());
        }
    }

    public static Session currentSessionByHour() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return Session.MORNING;
        if (hour < 17) return Session.EVENT;
        return Session.AFTERNOON;
    }

    public Session getActiveSession() {
        String v = sp.getString(todayKey + "_active_session", Session.MORNING.name());
        try {
            return Session.valueOf(v);
        } catch (Exception e) {
            return Session.MORNING;
        }
    }

    public void setActiveSession(Session session) {
        Session prev = null;
        try {
            String p = sp.getString(todayKey + "_active_session", null);
            if (p != null) prev = Session.valueOf(p);
        } catch (Exception ignored) {
        }
        sp.edit().putString(todayKey + "_active_session", session.name()).apply();
        // 세션이 바뀌면 카드 상태는 세션별 저장값을 쓰므로 별도 clear 불필요
        if (prev != null && prev != session) {
            // no-op: UI가 세션별 상태를 읽음
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
        sp.edit().putBoolean(k(getActiveSession(), "hrv"), true).apply();
        updateStarsAfterProgress();
    }

    public void setEmaDone() {
        sp.edit().putBoolean(k(getActiveSession(), "ema"), true).apply();
        updateStarsAfterProgress();
    }

    public void setDiaryDone() {
        sp.edit().putBoolean(k(getActiveSession(), "diary"), true).apply();
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

    /** 세션 카드 색 초기화(재시작) */
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

    public boolean isAmOrPmDoneToday() {
        return isSessionAllDone(Session.MORNING) || isSessionAllDone(Session.AFTERNOON);
    }

    public boolean isAllThreeSessionsDoneToday() {
        return isSessionAllDone(Session.MORNING)
                && isSessionAllDone(Session.AFTERNOON)
                && isSessionAllDone(Session.EVENT);
    }

    private void updateStarsAfterProgress() {
        int dayIndex = dayOfWeekIndex(); // 0=월 … 6=일
        if (isAllThreeSessionsDoneToday()) {
            setStarState(dayIndex, 2); // gold
        } else if (isAmOrPmDoneToday()) {
            int cur = getStarState(dayIndex);
            if (cur < 1) setStarState(dayIndex, 1); // filled
        }
    }

    /** 0=월 … 6=일 */
    public static int dayOfWeekIndex() {
        int cal = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        // Calendar: SUN=1 … SAT=7 → Mon=0
        return (cal + 5) % 7;
    }

    /** 0=empty, 1=filled, 2=gold */
    public int getStarState(int dayIndex) {
        return sp.getInt(weekKey() + "_star_" + dayIndex, 0);
    }

    private void setStarState(int dayIndex, int state) {
        sp.edit().putInt(weekKey() + "_star_" + dayIndex, state).apply();
    }

    private String weekKey() {
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return new SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(c.getTime()) + "_week";
    }

    public String sessionLabel(Session s) {
        switch (s) {
            case MORNING:
                return "오전";
            case AFTERNOON:
                return "오후";
            default:
                return "이벤트";
        }
    }
}
