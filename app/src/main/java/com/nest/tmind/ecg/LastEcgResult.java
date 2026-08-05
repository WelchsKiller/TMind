package com.nest.tmind.ecg;

import android.content.Context;
import android.content.SharedPreferences;

import com.nest.tmind.util.AesCrypto;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class LastEcgResult {

    private static final String PREF_NAME = "last_ecg_result";
    private static final String KEY_LAST = "last";
    private static final String KEY_HISTORY = "history";

    // ────────────── 마지막 측정 결과 (UI에서 바로 씀) ──────────────
    public static float[] lastSpike = null;  // 결과 화면 / 탭1에서 그리는 파형
    public static int lastFs = 0;            // 샘플링 레이트
    public static int lastHrBpm = 0;         // 마지막 BPM
    public static int lastHrvMs = 0;         // 마지막 HRV(ms)  ★모든 화면의 “ms”는 이 값 사용
    public static int lastRrMs = 0;          // 마지막 RR 간격(ms)  (저장만, UI에서는 직접 안 씀)
    public static int lastStressScore = 0;   // 0~100
    public static long measuredAtMs = 0L;    // 측정 시각

    // ────────────── 히스토리용 1건 데이터 ──────────────
    public static class DailyRecord {
        public long timeMs;
        public int hr;
        public int hrvMs;   // HRV(ms) – 20~160 근처 값
        public int rrMs;    // RR 간격(ms)
        public int stress;  // 0~100
    }

    // ────────────── 일간 통계 ──────────────
    public static class DailyEcgStats {
        public int count;

        public float avgBpm;
        public float maxBpm;
        public float minBpm;

        public float avgMs;   // HRV(ms) 평균
        public float maxMs;
        public float minMs;

        // 시간대별
        public int[] countPerHour = new int[24];
        public float[] lastMsPerHour = new float[24];   // 마지막 HRV(ms)
        public int[] lastBpmPerHour = new int[24];
        // 0=없음, 1=낮음, 2=중간, 3=높음 (HRV 기준)
        public int[] levelPerHour = new int[24];
    }

    // ────────────── 마지막 결과가 있는지 여부 ──────────────
    public static boolean hasValid() {
        return measuredAtMs > 0 && (lastFs > 0 || lastHrBpm > 0 || lastHrvMs > 0);
    }

    // ────────────── SharedPreferences에서 마지막 결과 로드 ──────────────
    public static void loadFromPrefs(Context ctx) {
        // 이미 메모리에 유효 결과가 있으면 prefs로 덮어쓰지 않음 (결과 화면 공백 방지)
        boolean keepMemory = hasValid();

        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String js = readPref(sp, KEY_LAST);
        if (js == null) {
            if (!keepMemory) {
                lastSpike = null;
                lastFs = 0;
                lastHrBpm = 0;
                lastHrvMs = 0;
                lastRrMs = 0;
                lastStressScore = 0;
                measuredAtMs = 0L;
            }
            return;
        }

        try {
            JSONObject o = new JSONObject(js);
            lastFs = o.optInt("fs", 0);
            lastHrBpm = o.optInt("hr", 0);
            lastHrvMs = o.optInt("hrvMs", 0);
            lastRrMs = o.optInt("rrMs", 0);
            lastStressScore = o.optInt("stress", 0);
            measuredAtMs = o.optLong("timeMs", 0L);

            if (o.has("spike")) {
                JSONArray arr = o.getJSONArray("spike");
                lastSpike = new float[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    lastSpike[i] = (float) arr.getDouble(i);
                }
            } else {
                lastSpike = null;
            }

        } catch (JSONException e) {
            // prefs 파싱 실패 시 메모리 유효값 유지
            if (!keepMemory) {
                lastSpike = null;
                lastFs = 0;
                lastHrBpm = 0;
                lastHrvMs = 0;
                lastRrMs = 0;
                lastStressScore = 0;
                measuredAtMs = 0L;
            }
        }
    }

    // ────────────── 마지막 결과 + 히스토리 저장 ──────────────
    public static void updateAndSave(Context ctx,
                                     float[] spike,
                                     int fs,
                                     Integer hrBpm,
                                     Integer hrvMs,
                                     Integer rrMs,
                                     int stressScore,
                                     long timeMs) {

        lastFs = fs;
        lastHrBpm = hrBpm != null ? hrBpm : 0;
        lastHrvMs = hrvMs != null ? hrvMs : 0;    // ★HRV(ms)
        lastRrMs = rrMs != null ? rrMs : 0;       // RR 간격(ms)
        lastStressScore = stressScore;
        measuredAtMs = timeMs;

        if (spike != null && spike.length > 0) {
            lastSpike = new float[spike.length];
            System.arraycopy(spike, 0, lastSpike, 0, spike.length);
        } else {
            lastSpike = null;
        }

        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // last 저장
        JSONObject last = new JSONObject();
        try {
            last.put("fs", lastFs);
            last.put("hr", lastHrBpm);
            last.put("hrvMs", lastHrvMs);
            last.put("rrMs", lastRrMs);
            last.put("stress", lastStressScore);
            last.put("timeMs", measuredAtMs);

            if (lastSpike != null) {
                JSONArray arr = new JSONArray();
                for (float v : lastSpike) {
                    arr.put((double) v);
                }
                last.put("spike", arr);
            }

        } catch (JSONException ignored) {
        }

        // history 읽어서 append
        JSONArray hist;
        String jsHist = sp.getString(KEY_HISTORY, null);
        if (jsHist != null) {
            try {
                // 암호화 레거시 호환
                String plain = AesCrypto.decryptOrPlain(jsHist);
                hist = new JSONArray(plain);
            } catch (JSONException e) {
                hist = new JSONArray();
            }
        } else {
            hist = new JSONArray();
        }

        JSONObject rec = new JSONObject();
        try {
            rec.put("timeMs", measuredAtMs);
            rec.put("hr", lastHrBpm);
            rec.put("hrvMs", lastHrvMs);
            rec.put("rrMs", lastRrMs);
            rec.put("stress", lastStressScore);
            hist.put(rec);
        } catch (JSONException ignored) {
        }

        // 측정 결과(파형 포함)는 평문 저장 — 암호문 복호화 실패 시 결과 화면이 비는 문제 방지
        sp.edit()
                .putString(KEY_LAST, last.toString())
                .putString(KEY_HISTORY, hist.toString())
                .commit();
    }

    private static String readPref(SharedPreferences sp, String key) {
        String v = sp.getString(key, null);
        if (v == null) return null;
        // 과거 AES 저장분 호환
        String plain = AesCrypto.decryptOrPlain(v);
        if (plain != null && (plain.trim().startsWith("{") || plain.trim().startsWith("["))) {
            return plain;
        }
        // 복호화 실패로 ciphertext가 그대로면 null 취급 (메모리 유효값 유지용)
        if (v.trim().startsWith("{") || v.trim().startsWith("[")) return v;
        return null;
    }

    // ────────────── 특정 날짜(자정 기준) 히스토리 로드 ──────────────
    public static List<DailyRecord> loadHistoryForDay(Context ctx, long dayMs) {
        List<DailyRecord> out = new ArrayList<>();

        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String jsHist = readPref(sp, KEY_HISTORY);
        if (jsHist == null) return out;

        long dayStart, dayEnd;
        {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(dayMs);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            dayStart = c.getTimeInMillis();
            dayEnd = dayStart + 24L * 60L * 60L * 1000L;
        }

        try {
            JSONArray hist = new JSONArray(jsHist);
            for (int i = 0; i < hist.length(); i++) {
                JSONObject o = hist.getJSONObject(i);
                long t = o.optLong("timeMs", 0L);
                if (t < dayStart || t >= dayEnd) continue;

                DailyRecord r = new DailyRecord();
                r.timeMs = t;
                r.hr = o.optInt("hr", 0);
                r.hrvMs = o.optInt("hrvMs", 0); // ★ HRV(ms)
                r.rrMs = o.optInt("rrMs", 0);
                r.stress = o.optInt("stress", 0);
                out.add(r);
            }
        } catch (JSONException ignored) {
        }

        return out;
    }

    // ────────────── 일간 통계 계산 (Tab2에서 사용) ──────────────
    public static DailyEcgStats calcDailyStats(List<DailyRecord> recs) {
        if (recs == null || recs.isEmpty()) return null;

        DailyEcgStats s = new DailyEcgStats();
        float sumHr = 0f;
        float sumMs = 0f;
        boolean first = true;

        for (DailyRecord r : recs) {
            if (r == null) continue;

            float hr = r.hr;
            float ms = r.hrvMs; // ★ HRV(ms) 기준

            if (ms <= 0f) continue;

            s.count++;
            sumHr += hr;
            sumMs += ms;

            if (first) {
                s.maxBpm = s.minBpm = hr;
                s.maxMs = s.minMs = ms;
                first = false;
            } else {
                if (hr > s.maxBpm) s.maxBpm = hr;
                if (hr < s.minBpm) s.minBpm = hr;
                if (ms > s.maxMs) s.maxMs = ms;
                if (ms < s.minMs) s.minMs = ms;
            }

            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(r.timeMs);
            int hour = c.get(Calendar.HOUR_OF_DAY);
            if (hour < 0 || hour > 23) continue;

            s.countPerHour[hour]++;
            s.lastMsPerHour[hour] = ms;
            s.lastBpmPerHour[hour] = r.hr;

            // HRV(ms) → 20~160 클램프 후 레벨(1/2/3)
            float msForScore = Math.max(20f, Math.min(160f, ms));
            int level;
            if (msForScore < 40f)      level = 1; // 낮음(정상)
            else if (msForScore < 70f) level = 2; // 중간
            else                       level = 3; // 높음(스트레스)
            s.levelPerHour[hour] = level;
        }

        if (s.count == 0 || first) return null;

        s.avgBpm = sumHr / s.count;
        s.avgMs = sumMs / s.count;

        return s;
    }
}
