package com.nest.tmind.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 오늘 미션: 오전/오후는 시각으로 자동 결정, 추가는 EVENT 세션.
 * 주간 별: 오전 0.5 + 오후 0.5 = 가득, 추가측정까지 하면 특수(금) 별.
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

    private static final String PREF = "tmind_mission_v2";

    private final SharedPreferences sp;
    private final String todayKey;

    public MissionManager(Context ctx) {
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        todayKey = new SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(new Date());
        // 항상 현재 시각 기준 메인 세션으로 동기화 (수동 탭 선택 금지)
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
        // 추가 측정 중이 아니면 메인 세션으로 고정
        String forced = sp.getString(todayKey + "_force_event", null);
        if ("1".equals(forced)) {
            sp.edit().putString(todayKey + "_active_session", Session.EVENT.name()).apply();
        } else {
            sp.edit().putString(todayKey + "_active_session", main.name()).apply();
        }
    }

    /** 추가 측정 모드 진입/해제 */
    public void setAdditionalMeasureMode(boolean on) {
        // commit: 직후 isHrvDone() 등이 EVENT 세션을 보도록 동기 반영
        sp.edit().putString(todayKey + "_force_event", on ? "1" : null)
                .putString(todayKey + "_active_session",
                        on ? Session.EVENT.name() : mainSessionByHour().name())
                .commit();
    }

    public boolean isAdditionalMeasureMode() {
        return "1".equals(sp.getString(todayKey + "_force_event", null));
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
        // 수동 전환 비활성: 추가측정만 허용
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
        // commit: 대시보드 onResume에서 추가모드 해제 전에 별 상태가 반영되도록
        sp.edit().putBoolean(k(getActiveSession(), "hrv"), true).commit();
        updateStarsAfterProgress();
    }

    public void setEmaDone() {
        sp.edit().putBoolean(k(getActiveSession(), "ema"), true).commit();
        updateStarsAfterProgress();
    }

    public void setDiaryDone() {
        sp.edit().putBoolean(k(getActiveSession(), "diary"), true).commit();
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

    private void updateStarsAfterProgress() {
        int dayIndex = dayOfWeekIndex();
        boolean am = isSessionAllDone(Session.MORNING);
        boolean pm = isSessionAllDone(Session.AFTERNOON);
        // 추가 측정은 HRV만 진행하므로 EVENT HRV 완료 = 추가 측정 완료
        boolean extra = isHrvDone(Session.EVENT);

        int state = STAR_EMPTY;
        if (am && pm) {
            state = extra ? STAR_BONUS : STAR_FULL;
        } else if (am || pm) {
            // 오전·오후 한쪽만 끝난 뒤 추가 측정을 해도 반은 유지하되,
            // 추가까지 했으면 특수 별로 표시해 변화를 알 수 있게 함
            state = extra ? STAR_BONUS : STAR_HALF;
        } else if (extra) {
            // 메인 미션 없이 추가만 한 경우(비정상 경로)에도 표시
            state = STAR_BONUS;
        }
        setStarState(dayIndex, state);
    }

    public static int dayOfWeekIndex() {
        int cal = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return (cal + 5) % 7;
    }

    public int getStarState(int dayIndex) {
        return sp.getInt(weekKey() + "_star_" + dayIndex, STAR_EMPTY);
    }

    private void setStarState(int dayIndex, int state) {
        sp.edit().putInt(weekKey() + "_star_" + dayIndex, state).commit();
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
                return "추가";
        }
    }
}
