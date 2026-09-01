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
    private static final String KEY_USER_PHONE = "user_phone";
    private static final String KEY_USER_GENDER = "user_gender";
    private static final String KEY_USER_AGE = "user_age";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_STUDY_START = "study_start_ms";
    private static final String KEY_EMA_SESSION = "ema_session_type";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_MAIN_SESSION_ID = "main_session_id";
    private static final String KEY_EVENT_SESSION_ID = "event_session_id";
    private static final String KEY_FCM_TOKEN = "fcm_token";
    private static final String KEY_EVENT_ACTIVE_REMOTE = "event_active_remote";
    private static final String KEY_HRV_STATUS = "hrv_status_remote";
    private static final String KEY_EVENT_GUIDE = "event_guide_text";
    private static final String KEY_CRYPTO_KEY_ID = "crypto_key_id";
    private static final String KEY_CRYPTO_PUBLIC_KEY = "crypto_public_key";
    private static final String KEY_CRYPTO_PREFETCH_BLOCKED_UNTIL = "crypto_prefetch_blocked_until";
    private static final String KEY_SERVER_TODAY_SESSION_ID = "server_today_session_id";
    private static final String KEY_SERVER_DATE = "server_date";
    private static final String KEY_EMA_Q_CACHE_JSON = "ema_questions_cache_json";
    private static final String KEY_EMA_Q_CACHE_SID = "ema_questions_cache_session_id";
    /** 피드백은 예측이 먼저 저장된 세션에서만 받아준다(E00702). */
    public void setPredictionSaved(long sessionId) {
        sp.edit().putLong("prediction_saved_session_id", sessionId).apply();
    }

    public boolean isPredictionSaved(long sessionId) {
        return sessionId > 0 && sp.getLong("prediction_saved_session_id", 0L) == sessionId;
    }

    /** 피드백의 correctedValence/Arousal 은 필수라, MATCH/UNKNOWN 이면 예측값을 그대로 보낸다. */
    public void setLastPrediction(float valence, float arousal) {
        sp.edit()
                .putFloat("last_prediction_valence", valence)
                .putFloat("last_prediction_arousal", arousal)
                .apply();
    }

    public float getLastPredictionValence() {
        return sp.getFloat("last_prediction_valence", 0f);
    }

    public float getLastPredictionArousal() {
        return sp.getFloat("last_prediction_arousal", 0f);
    }

    /** /today 가 알려준 진행 중 세션 ID. POST /session 실패 시 폴백으로만 사용. */
    public void setServerTodaySessionId(long sessionId) {
        sp.edit().putLong(KEY_SERVER_TODAY_SESSION_ID, Math.max(0L, sessionId)).apply();
    }

    public long getServerTodaySessionId() {
        return sp.getLong(KEY_SERVER_TODAY_SESSION_ID, 0L);
    }

    /** 서버가 세션 판정에 쓴 오늘 날짜(yyyy-MM-dd). 단말 시계가 틀어졌는지 판단하는 기준. */
    public void setServerDate(String serverDate) {
        sp.edit().putString(KEY_SERVER_DATE, serverDate != null ? serverDate : "").apply();
    }

    public String getServerDate() {
        return sp.getString(KEY_SERVER_DATE, "");
    }

    public void clearEmaQuestionsCache() {
        sp.edit()
                .remove(KEY_EMA_Q_CACHE_JSON)
                .remove(KEY_EMA_Q_CACHE_SID)
                .apply();
    }

    public void setEmaQuestionsCache(long sessionId, String json) {
        if (sessionId <= 0 || json == null || json.isEmpty()) return;
        sp.edit()
                .putLong(KEY_EMA_Q_CACHE_SID, sessionId)
                .putString(KEY_EMA_Q_CACHE_JSON, json)
                .apply();
    }

    public String getEmaQuestionsCache(long sessionId) {
        if (sessionId <= 0) return null;
        if (sp.getLong(KEY_EMA_Q_CACHE_SID, 0L) != sessionId) return null;
        return sp.getString(KEY_EMA_Q_CACHE_JSON, null);
    }

    /** 연구 참여 일수 (종료 후 7일 추이 제공) */
    public static final int STUDY_DAYS = 7;

    private final SharedPreferences sp;

    public SessionManager(Context ctx) {
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void setProfile(String phoneTail8, String userName, String gender, int age) {
        SharedPreferences.Editor ed = sp.edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_USER_PHONE, phoneTail8 != null ? phoneTail8 : "")
                .putString(KEY_USER_NAME, userName != null ? userName : "")
                .putString(KEY_USER_GENDER, gender != null ? gender : "")
                .putInt(KEY_USER_AGE, age);
        // 최초 로그인 시에만 연구 시작일 기록
        if (sp.getLong(KEY_STUDY_START, 0L) <= 0L) {
            ed.putLong(KEY_STUDY_START, System.currentTimeMillis());
        }
        ed.apply();
    }

    /** @deprecated setProfile(phone, name, gender, age) 사용 */
    public void setProfile(String userName, String gender, int age) {
        setProfile(sp.getString(KEY_USER_PHONE, ""), userName, gender, age);
    }

    /** @deprecated 프로필 등록은 setProfile 사용 */
    public void setLoggedIn(String userName) {
        setProfile(sp.getString(KEY_USER_PHONE, ""), userName,
                sp.getString(KEY_USER_GENDER, ""), sp.getInt(KEY_USER_AGE, 0));
    }

    public String getUserPhone() {
        return sp.getString(KEY_USER_PHONE, "");
    }

    public String getUserGender() {
        return sp.getString(KEY_USER_GENDER, "");
    }

    public int getUserAge() {
        return sp.getInt(KEY_USER_AGE, 0);
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
        return sp.getString(KEY_USER_NAME, "");
    }

    public void setTokens(String accessToken, String refreshToken) {
        // commit: 저장 직후 바로 다른 API 호출에 쓰이도록 동기 반영
        sp.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken != null ? accessToken : "")
                .putString(KEY_REFRESH_TOKEN, refreshToken != null ? refreshToken : "")
                .commit();
    }

    public String getAccessToken() {
        return sp.getString(KEY_ACCESS_TOKEN, "");
    }

    public String getRefreshToken() {
        return sp.getString(KEY_REFRESH_TOKEN, "");
    }

    public boolean hasAccessToken() {
        String t = getAccessToken();
        return t != null && !t.isEmpty();
    }

    public boolean hasRefreshToken() {
        String t = getRefreshToken();
        return t != null && !t.isEmpty();
    }

    /** 로그인 유지 여부: 로컬 플래그 + accessToken */
    public boolean hasValidAuth() {
        return isLoggedIn() && hasAccessToken();
    }

    public void clearTokens() {
        sp.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .commit();
    }

    /** 동의/인증 실패 시: 토큰·로그인 플래그 해제 후 키패드 로그인으로 */
    public void clearAuthForRelogin() {
        sp.edit()
                .putBoolean(KEY_LOGGED_IN, false)
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_MAIN_SESSION_ID)
                .remove(KEY_EVENT_SESSION_ID)
                .commit();
    }

    public void setCurrentSessionId(boolean event, long sessionId) {
        sp.edit().putLong(event ? KEY_EVENT_SESSION_ID : KEY_MAIN_SESSION_ID, sessionId).apply();
    }

    public long getCurrentSessionId(boolean event) {
        return sp.getLong(event ? KEY_EVENT_SESSION_ID : KEY_MAIN_SESSION_ID, 0L);
    }

    public void clearCurrentSession(boolean event) {
        sp.edit().remove(event ? KEY_EVENT_SESSION_ID : KEY_MAIN_SESSION_ID).apply();
        clearEmaQuestionsCache();
    }

    public void clearAllCurrentSessions() {
        sp.edit()
                .remove(KEY_MAIN_SESSION_ID)
                .remove(KEY_EVENT_SESSION_ID)
                .apply();
    }

    public void setLastFcmToken(String token) {
        sp.edit().putString(KEY_FCM_TOKEN, token != null ? token : "").apply();
    }

    public String getLastFcmToken() {
        return sp.getString(KEY_FCM_TOKEN, "");
    }

    public void setRemoteEventActive(boolean active) {
        sp.edit().putBoolean(KEY_EVENT_ACTIVE_REMOTE, active).apply();
    }

    public boolean isRemoteEventActive() {
        return sp.getBoolean(KEY_EVENT_ACTIVE_REMOTE, true);
    }

    /** VALID / SKIPPED / ""(미수행) */
    public void setRemoteHrvStatus(String status) {
        sp.edit().putString(KEY_HRV_STATUS, status != null ? status : "").apply();
    }

    public String getRemoteHrvStatus() {
        return sp.getString(KEY_HRV_STATUS, "");
    }

    public boolean isRemoteHrvValid() {
        return "VALID".equalsIgnoreCase(getRemoteHrvStatus());
    }

    public boolean isRemoteHrvSkipped() {
        return "SKIPPED".equalsIgnoreCase(getRemoteHrvStatus());
    }

    public boolean isRemoteHrvDone() {
        return isRemoteHrvValid() || isRemoteHrvSkipped();
    }

    public void setCryptoPublicKey(String keyId, String publicKey) {
        sp.edit()
                .putString(KEY_CRYPTO_KEY_ID, keyId != null ? keyId : "")
                .putString(KEY_CRYPTO_PUBLIC_KEY, publicKey != null ? publicKey : "")
                .apply();
    }

    public String getCryptoKeyId() {
        return sp.getString(KEY_CRYPTO_KEY_ID, "");
    }

    public String getCryptoPublicKey() {
        return sp.getString(KEY_CRYPTO_PUBLIC_KEY, "");
    }

    public boolean hasCryptoPublicKey() {
        return !getCryptoKeyId().isEmpty() && !getCryptoPublicKey().isEmpty();
    }

    /** 서버가 E00504(복호화 실패)를 주면 캐시된 키가 서버 키와 어긋난 것이므로 버린다. */
    public void clearCryptoPublicKey() {
        sp.edit()
                .remove(KEY_CRYPTO_KEY_ID)
                .remove(KEY_CRYPTO_PUBLIC_KEY)
                .remove(KEY_CRYPTO_PREFETCH_BLOCKED_UNTIL)
                .apply();
    }

    public void blockCryptoPrefetchUntil(long epochMs) {
        sp.edit().putLong(KEY_CRYPTO_PREFETCH_BLOCKED_UNTIL, epochMs).apply();
    }

    public void clearCryptoPrefetchBlock() {
        sp.edit().remove(KEY_CRYPTO_PREFETCH_BLOCKED_UNTIL).apply();
    }

    public boolean isCryptoPrefetchBlocked() {
        long until = sp.getLong(KEY_CRYPTO_PREFETCH_BLOCKED_UNTIL, 0L);
        return until > System.currentTimeMillis();
    }

    public void setEventGuideText(String text) {
        sp.edit().putString(KEY_EVENT_GUIDE, text != null ? text : "").apply();
    }

    public String getEventGuideText() {
        return sp.getString(KEY_EVENT_GUIDE, "");
    }

    public void saveScreen(String screen) {
        sp.edit().putString(KEY_SCREEN, screen).apply();
    }

    public String getSavedScreen() {
        return sp.getString(KEY_SCREEN, "");
    }

    public void saveEmaProgress(String sessionType, int index, int[] answers) {
        try {
            JSONArray arr = new JSONArray();
            if (answers != null) {
                for (int a : answers) arr.put(a);
            }
            sp.edit()
                    .putString(KEY_EMA_SESSION, sessionType != null ? sessionType : "")
                    .putInt(KEY_EMA_INDEX, index)
                    .putString(KEY_EMA_ANSWERS, arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    /** @deprecated sessionType 포함 오버로드 사용 */
    public void saveEmaProgress(int index, int[] answers) {
        saveEmaProgress(sp.getString(KEY_EMA_SESSION, ""), index, answers);
    }

    public String getEmaSessionType() {
        return sp.getString(KEY_EMA_SESSION, "");
    }

    public int getEmaIndex() {
        return sp.getInt(KEY_EMA_INDEX, 0);
    }

    /**
     * 동일 세션 타입의 진행 중 답만 복원. 다른 세션(오전→오후·추가)이면 빈 배열.
     */
    public int[] loadEmaAnswersForSession(String sessionType, int size) {
        int[] out = new int[size];
        for (int i = 0; i < size; i++) out[i] = 0;
        if (sessionType == null || !sessionType.equals(getEmaSessionType())) {
            return out;
        }
        return loadEmaAnswers(size);
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

    public void clearEmaProgress() {
        sp.edit()
                .remove(KEY_EMA_SESSION)
                .remove(KEY_EMA_INDEX)
                .remove(KEY_EMA_ANSWERS)
                .apply();
    }

    /** 연구 태블릿 등: 다른 참여자 등록을 위해 로그인만 해제 */
    public void logoutForNewParticipant() {
        sp.edit().clear().apply();
    }

    public void clearSession() {
        sp.edit().clear().apply();
    }
}
