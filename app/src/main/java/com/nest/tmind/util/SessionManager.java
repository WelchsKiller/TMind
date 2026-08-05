package com.nest.tmind.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** 안전한 복구: 세션 상태 저장/복원 */
public class SessionManager {

    private static final String PREF = "tmind_session";
    private static final String KEY_SCREEN = "current_screen";
    private static final String KEY_EMA_INDEX = "ema_index";
    private static final String KEY_EMA_ANSWERS = "ema_answers";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_STUDY_START = "study_start_ms";
    /** 연구 참여 일수 (종료 후 7일 추이 제공) */
    public static final int STUDY_DAYS = 7;

    private final SharedPreferences sp;

    public SessionManager(Context ctx) {
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void setLoggedIn(String userName) {
        SharedPreferences.Editor ed = sp.edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_USER_NAME, userName);
        if (sp.getLong(KEY_STUDY_START, 0L) <= 0L) {
            ed.putLong(KEY_STUDY_START, System.currentTimeMillis());
        }
        ed.apply();
    }

    public long getStudyStartMs() {
        long start = sp.getLong(KEY_STUDY_START, 0L);
        if (start <= 0L && isLoggedIn()) {
            start = System.currentTimeMillis();
            sp.edit().putLong(KEY_STUDY_START, start).apply();
        }
        return start;
    }

    /** 연구 시작일 기준 경과 일수 (0=첫날) */
    public int getStudyDayIndex() {
        long start = getStudyStartMs();
        if (start <= 0) return 0;
        long dayMs = 24L * 60L * 60L * 1000L;
        return (int) ((startOfDay(System.currentTimeMillis()) - startOfDay(start)) / dayMs);
    }

    public boolean isStudyEnded() {
        return getStudyDayIndex() >= STUDY_DAYS;
    }

    private static long startOfDay(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public boolean isLoggedIn() {
        return sp.getBoolean(KEY_LOGGED_IN, false);
    }

    public String getUserName() {
        return sp.getString(KEY_USER_NAME, "김순자");
    }

    public void saveScreen(String screen) {
        sp.edit().putString(KEY_SCREEN, screen).apply();
    }

    public String getSavedScreen() {
        return sp.getString(KEY_SCREEN, "");
    }

    public void saveEmaProgress(int index, int[] answers) {
        try {
            JSONArray arr = new JSONArray();
            if (answers != null) {
                for (int a : answers) arr.put(a);
            }
            sp.edit()
                    .putInt(KEY_EMA_INDEX, index)
                    .putString(KEY_EMA_ANSWERS, arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public int getEmaIndex() {
        return sp.getInt(KEY_EMA_INDEX, 0);
    }

    public int[] loadEmaAnswers(int size) {
        int[] out = new int[size];
        for (int i = 0; i < size; i++) out[i] = 0;
        String js = sp.getString(KEY_EMA_ANSWERS, null);
        if (js == null) return out;
        try {
            JSONArray arr = new JSONArray(js);
            for (int i = 0; i < Math.min(size, arr.length()); i++) {
                out[i] = arr.getInt(i);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public void clearSession() {
        sp.edit().clear().apply();
    }
}
