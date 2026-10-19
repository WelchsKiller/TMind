package com.nest.tmind.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 오늘 미션: 오전/오후는 시각으로 자동 결정, 추가는 EVENT 세션.
 * 별 채움: 오전=왼쪽, 오후=오른쪽, 둘 다=가득, 추가 측정=골드 링.
 */
public class MissionManager {

    public enum Session {
        MORNING, AFTERNOON, EVENT
    }

    /** 0=빈, 1=오전(왼), 2=가득, 3=가득+골드링, 4=오전+골드링, 5=오후(오른), 6=오후+골드링 */
    public static final int STAR_EMPTY = 0;
    public static final int STAR_HALF = 1;
    public static final int STAR_FULL = 2;
    public static final int STAR_BONUS = 3;
    public static final int STAR_HALF_BONUS = 4;
    public static final int STAR_HALF_RIGHT = 5;
    public static final int STAR_HALF_RIGHT_BONUS = 6;

    public static final int MAX_ADDITIONAL_PER_DAY = 5;

    private static final String PREF = "tmind_mission_v2";

    private final SharedPreferences sp;
    private final String todayKey;

    public MissionManager(Context ctx) {
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        todayKey = new SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(new Date());
        syncMainSessionByHour();
    }

    /** 계정 전환 시 이전 참여자 미션·추가측정 로컬 상태를 전부 지운다. */
    public static void clearAll(Context ctx) {
        if (ctx == null) return;
        ctx.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
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
    }

    public void clearHrv(Session s) {
        sp.edit().putBoolean(k(s, "hrv"), false).commit();
    }

    public void setEmaDone() {
        setEmaDone(getActiveSession());
    }

    public void setEmaDone(Session s) {
        sp.edit().putBoolean(k(s, "ema"), true).commit();
    }

    public void setDiaryDone() {
        setDiaryDone(getActiveSession());
    }

    public void setDiaryDone(Session s) {
        sp.edit().putBoolean(k(s, "diary"), true).commit();
        if (s == Session.EVENT && isSessionAllDone(Session.EVENT)) {
            recordAdditionalCompletion();
        }
    }

    /** /today 의 hrvDone · emaDone · diaryDone 을 그대로 반영한다. */
    public void syncFromServer(Session s, boolean hrvDone, boolean emaDone, boolean diaryDone) {
        sp.edit()
                .putBoolean(k(s, "hrv"), hrvDone)
                .putBoolean(k(s, "ema"), emaDone)
                .putBoolean(k(s, "diary"), diaryDone)
                .commit();
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

    /** HRV 재측정 시 설문·일기는 처음부터 다시 한다. */
    public void clearFollowUpMissions(Session s) {
        if (s == null) s = getActiveSession();
        sp.edit()
                .putBoolean(k(s, "ema"), false)
                .putBoolean(k(s, "diary"), false)
                .commit();
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
