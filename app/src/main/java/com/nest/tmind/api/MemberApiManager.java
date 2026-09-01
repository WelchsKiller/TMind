package com.nest.tmind.api;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.ui.LoginActivity;
import com.nest.tmind.util.InterventionClassifier;
import com.nest.tmind.util.SessionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class MemberApiManager {

    private static final String TAG = "MemberApiManager";
    private static final Gson GSON = new Gson();
    /** 서버 crypto/public-key API 배포 전까지 false (500 방지) */
    private static final boolean CRYPTO_PUBLIC_KEY_ENABLED = false;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private MemberApiManager() {
    }

    public interface ResultCallback<T> {
        void onSuccess(T data);

        void onError(String message);
    }

    public static void loginAndConsent(Context context, String code, ResultCallback<ApiModels.TokenPair> callback) {
        MemberApiClient.service(context).login(new ApiModels.MemberLoginRequest(code))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        ApiModels.TokenPair pair = parseTokenResponse(response);
                        if (!response.isSuccessful() || pair == null) {
                            callback.onError(httpErrorMessage(response, "로그인에 실패했습니다."));
                            return;
                        }
                        SessionManager session = new SessionManager(context);
                        session.setTokens(pair.accessToken, pair.refreshToken);
                        Log.i(TAG, "login ok, tokens saved");
                        runConsentThenRefresh(context, session, callback);
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        callback.onError("서버에 연결할 수 없습니다.");
                    }
                });
    }

    /** 연구 참여 동의 → (필요 시) refresh 로 CONSENTED 토큰 확보 */
    private static void runConsentThenRefresh(Context context, SessionManager session,
                                              ResultCallback<ApiModels.TokenPair> callback) {
        MemberApiClient.service(context).consent().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                ApiModels.TokenPair consented = parseTokenResponse(response);
                if (response.isSuccessful() && consented != null) {
                    session.setTokens(consented.accessToken, consented.refreshToken);
                    Log.i(TAG, "consent ok, tokens updated");
                    refreshTokens(context, session, consented, callback);
                    return;
                }
                if (response.code() == 404) {
                    callback.onError("진행 중인 연구 참여 회차가 없습니다. 관리자에게 문의해 주세요.");
                    return;
                }
                callback.onError(httpErrorMessage(response,
                        "참여 동의에 실패했습니다. 다시 로그인해 주세요."));
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError("참여 동의 요청에 실패했습니다.");
            }
        });
    }

    private static void refreshTokens(Context context, SessionManager session,
                                      ApiModels.TokenPair fallback,
                                      ResultCallback<ApiModels.TokenPair> callback) {
        String refresh = session.getRefreshToken();
        if (refresh == null || refresh.isEmpty()) {
            callback.onSuccess(fallback);
            return;
        }
        MemberApiClient.service(context).refresh(new ApiModels.RefreshRequest(refresh))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        ApiModels.TokenPair refreshed = parseTokenResponse(response);
                        if (response.isSuccessful() && refreshed != null) {
                            session.setTokens(refreshed.accessToken, refreshed.refreshToken);
                            Log.i(TAG, "refresh ok after consent");
                            callback.onSuccess(refreshed);
                            return;
                        }
                        // refresh 실패해도 consent 토큰으로 진행
                        callback.onSuccess(fallback);
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        callback.onSuccess(fallback);
                    }
                });
    }

    public static void logout(Context context, ResultCallback<Void> callback) {
        SessionManager session = new SessionManager(context);
        String refresh = session.getRefreshToken();
        if (refresh == null || refresh.isEmpty()) {
            session.clearTokens();
            callback.onSuccess(null);
            return;
        }
        MemberApiClient.service(context).deleteFcmToken().enqueue(new EmptyCallback());
        MemberApiClient.service(context).logout(new ApiModels.RefreshRequest(refresh))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        session.clearTokens();
                        session.clearAllCurrentSessions();
                        callback.onSuccess(null);
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        session.clearTokens();
                        session.clearAllCurrentSessions();
                        callback.onSuccess(null);
                    }
                });
    }

    public static void fetchToday(Context context, ResultCallback<ApiModels.TodayResponse> callback) {
        MemberApiClient.service(context).getToday().enqueue(new Callback<ApiModels.ApiResponse<ApiModels.TodayResponse>>() {
            @Override
            public void onResponse(Call<ApiModels.ApiResponse<ApiModels.TodayResponse>> call,
                                   Response<ApiModels.ApiResponse<ApiModels.TodayResponse>> response) {
                if (handleAuthFailure(context, response.code(), response)) {
                    return;
                }
                ApiModels.ApiResponse<ApiModels.TodayResponse> body = response.body();
                if (!response.isSuccessful() || body == null || !body.isSuccess()) {
                    callback.onError(errorMessage(body, response.code(), "오늘 세션 정보를 불러오지 못했습니다."));
                    return;
                }
                SessionManager session = new SessionManager(context);
                session.setRemoteEventActive(body.data.eventActive);
                if (body.data.hrvStatus != null && !body.data.hrvStatus.trim().isEmpty()) {
                    session.setRemoteHrvStatus(body.data.hrvStatus);
                }
                session.setServerTodaySessionId(
                        body.data.currentSessionId != null ? body.data.currentSessionId : 0L);
                session.setEventGuideText(body.data.eventGuideText);
                callback.onSuccess(body.data);
            }

            @Override
            public void onFailure(Call<ApiModels.ApiResponse<ApiModels.TodayResponse>> call, Throwable t) {
                callback.onError("오늘 세션 정보를 불러오지 못했습니다.");
            }
        });
    }

    public static void fetchParticipation(Context context,
                                          ResultCallback<ApiModels.ParticipationResponse> callback) {
        MemberApiClient.service(context).getParticipation()
                .enqueue(new Callback<ApiModels.ApiResponse<ApiModels.ParticipationResponse>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<ApiModels.ParticipationResponse>> call,
                                           Response<ApiModels.ApiResponse<ApiModels.ParticipationResponse>> response) {
                        if (handleAuthFailure(context, response.code(), response)) {
                            return;
                        }
                        ApiModels.ApiResponse<ApiModels.ParticipationResponse> body = response.body();
                        if (!response.isSuccessful() || body == null || !body.isSuccess()) {
                            callback.onError(errorMessage(body, response.code(), "참여 현황을 불러오지 못했습니다."));
                            return;
                        }
                        callback.onSuccess(body.data);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<ApiModels.ParticipationResponse>> call,
                                          Throwable t) {
                        callback.onError("참여 현황을 불러오지 못했습니다.");
                    }
                });
    }

    /** 서버 공개키 API — 실패 시 무시(평문 CSV 업로드 유지). HRV 업로드 직전에만 호출. */
    public static void prefetchPublicKey(Context context) {
        if (!CRYPTO_PUBLIC_KEY_ENABLED) return;
        SessionManager session = new SessionManager(context);
        if (session.hasCryptoPublicKey() || session.isCryptoPrefetchBlocked()) return;
        MemberApiClient.service(context).getPublicKey()
                .enqueue(new Callback<ApiModels.ApiResponse<ApiModels.PublicKeyResponse>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<ApiModels.PublicKeyResponse>> call,
                                           Response<ApiModels.ApiResponse<ApiModels.PublicKeyResponse>> response) {
                        if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) {
                            if (response.code() == 404 || response.code() >= 500) {
                                session.blockCryptoPrefetchUntil(System.currentTimeMillis()
                                        + 24L * 60L * 60L * 1000L);
                            }
                            return;
                        }
                        ApiModels.PublicKeyResponse data = response.body().data;
                        if (data == null || data.keyId == null || data.publicKey == null) return;
                        session.setCryptoPublicKey(data.keyId, data.publicKey);
                        session.clearCryptoPrefetchBlock();
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<ApiModels.PublicKeyResponse>> call,
                                          Throwable t) {
                        session.blockCryptoPrefetchUntil(System.currentTimeMillis()
                                + 24L * 60L * 60L * 1000L);
                        Log.d(TAG, "public key prefetch skipped: " + t.getMessage());
                    }
                });
    }

    public static long getCurrentSessionId(Context context, boolean event) {
        return new SessionManager(context).getCurrentSessionId(event);
    }

    public static void ensureSessionStarted(Context context, boolean event, ResultCallback<Long> callback) {
        SessionManager session = new SessionManager(context);
        if (!session.hasAccessToken()) {
            forceRelogin(context, "로그인이 필요합니다. 다시 로그인해 주세요.");
            return;
        }
        long cached = session.getCurrentSessionId(event);
        if (cached > 0) {
            callback.onSuccess(cached);
            return;
        }
        MemberApiClient.service(context)
                .startSession(new ApiModels.StartSessionRequest(event, System.currentTimeMillis()))
                .enqueue(new Callback<ApiModels.ApiResponse<ApiModels.StartSessionData>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<ApiModels.StartSessionData>> call,
                                           Response<ApiModels.ApiResponse<ApiModels.StartSessionData>> response) {
                        if (handleAuthFailure(context, response.code(), response)) {
                            return;
                        }
                        ApiModels.ApiResponse<ApiModels.StartSessionData> body = response.body();
                        ApiModels.StartSessionData data = body != null ? body.data : null;
                        if (!response.isSuccessful() || body == null || !body.isSuccess()
                                || data == null || data.sessionId <= 0) {
                            String detail = body != null
                                    ? apiErrorMessage(body, response.code(), "세션을 시작하지 못했습니다.")
                                    : httpErrorMessage(response, "세션을 시작하지 못했습니다.");
                            Log.w(TAG, "startSession failed: http=" + response.code()
                                    + " isEvent=" + event + " detail=" + detail);
                            long fallback = session.getServerTodaySessionId();
                            if (!event && fallback > 0) {
                                Log.w(TAG, "startSession fallback to /today sessionId=" + fallback);
                                session.setCurrentSessionId(false, fallback);
                                callback.onSuccess(fallback);
                                return;
                            }
                            callback.onError(detail);
                            return;
                        }
                        session.setCurrentSessionId(event, data.sessionId);
                        if (data.hrvStatus != null) {
                            session.setRemoteHrvStatus(data.hrvStatus);
                        }
                        callback.onSuccess(data.sessionId);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<ApiModels.StartSessionData>> call, Throwable t) {
                        callback.onError("세션을 시작하지 못했습니다. 네트워크를 확인해 주세요.");
                    }
                });
    }

    public static void uploadHrv(Context context, boolean event, long measuredAtMs) {
        uploadHrv(context, event, measuredAtMs, true, null);
    }

    public static void uploadHrv(Context context, boolean event, long measuredAtMs,
                                 ResultCallback<Void> callback) {
        uploadHrv(context, event, measuredAtMs, true, callback);
    }

    /**
     * 서버가 실제로 200 을 준 경우에만 remoteHrvStatus 를 VALID 로 올린다.
     * 낙관적으로 VALID 를 찍으면 업로드 실패가 EMA 문항 404 로 뒤늦게 드러난다.
     */
    public static void uploadHrv(Context context, boolean event, long measuredAtMs,
                                 boolean measurementValid, ResultCallback<Void> callback) {
        prefetchPublicKey(context);
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) {
            reportHrvFailure(context, callback, "세션이 없어 심박변이도를 전송하지 못했습니다.");
            return;
        }
        File signalFile = buildSignalFile(context);
        if (signalFile == null) {
            reportHrvFailure(context, callback, "ECG 신호 파일이 없어 심박변이도를 전송하지 못했습니다.");
            return;
        }
        long measuredAt = measuredAtMs > 0 ? measuredAtMs : System.currentTimeMillis();
        MultipartBody.Part data = MultipartBody.Part.createFormData("data", null,
                RequestBody.create(GSON.toJson(buildHrvSaveRequest(measuredAt, measurementValid)),
                        JSON));
        RequestBody fileBody = RequestBody.create(signalFile, MediaType.parse("text/csv"));
        MultipartBody.Part signal = MultipartBody.Part.createFormData(
                "signal", signalFile.getName(), fileBody);
        Log.d(TAG, "uploadHrv sessionId=" + sessionId + " measuredAt=" + measuredAt
                + " valid=" + measurementValid
                + " signalBytes=" + signalFile.length()
                + " samples=" + (LastEcgResult.lastRawSignal != null
                        ? LastEcgResult.lastRawSignal.length
                        : (LastEcgResult.lastSpike != null ? LastEcgResult.lastSpike.length : 0)));
        MemberApiClient.service(context).uploadHrv(sessionId, data, signal)
                .enqueue(new Callback<ApiModels.ApiResponse<String>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<String>> call,
                                           Response<ApiModels.ApiResponse<String>> response) {
                        if (handleAuthFailure(context, response.code(), response)) {
                            return;
                        }
                        ApiModels.ApiResponse<String> body = response.body();
                        if (!response.isSuccessful() || (body != null && !body.isSuccess())) {
                            String fallback = "심박변이도 전송에 실패했습니다.";
                            String detail = body != null
                                    ? apiErrorMessage(body, response.code(), fallback)
                                    : httpErrorMessage(response, fallback);
                            Log.w(TAG, "uploadHrv failed: http=" + response.code()
                                    + " url=" + call.request().url() + " detail=" + detail);
                            reportHrvFailure(context, callback, detail);
                            return;
                        }
                        Log.d(TAG, "uploadHrv ok: sessionId=" + sessionId);
                        new SessionManager(context).setRemoteHrvStatus("VALID");
                        if (callback != null) callback.onSuccess(null);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<String>> call, Throwable t) {
                        Log.w(TAG, "uploadHrv error", t);
                        reportHrvFailure(context, callback,
                                "심박변이도 전송 중 오류가 발생했습니다. 네트워크를 확인해 주세요.");
                    }
                });
    }

    private static ApiModels.HrvSaveRequest buildHrvSaveRequest(long measuredAt,
                                                                boolean measurementValid) {
        String abnormality = null;
        if (measurementValid) {
            try {
                abnormality = InterventionClassifier.classify().name();
            } catch (Exception e) {
                Log.w(TAG, "abnormality classify failed", e);
            }
        }
        return new ApiModels.HrvSaveRequest(
                measuredAt,
                positiveOrNull(LastEcgResult.lastHrBpm),
                positiveOrNull(LastEcgResult.lastHrvMs),
                positiveOrNull(LastEcgResult.lastRrMs),
                positiveOrNull(LastEcgResult.lastStressScore),
                positiveOrNull(LastEcgResult.lastFs),
                abnormality,
                measurementValid);
    }

    /** 지표는 모두 선택이라, 값이 없으면 0 대신 생략한다. */
    private static Integer positiveOrNull(int value) {
        return value > 0 ? value : null;
    }

    private static void reportHrvFailure(Context context, ResultCallback<Void> callback,
                                         String message) {
        Log.w(TAG, "uploadHrv unavailable: " + message);
        if (callback != null) {
            callback.onError(message);
            return;
        }
        showToast(context, message);
    }

    public static void skipHrv(Context context, boolean event, String reason) {
        skipHrv(context, event, reason, null);
    }

    public static void skipHrv(Context context, boolean event, String reason,
                               ResultCallback<Void> callback) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) {
            if (callback != null) callback.onError("세션이 없습니다.");
            return;
        }
        MemberApiClient.service(context).skipHrv(sessionId, new ApiModels.SkipHrvRequest(reason))
                .enqueue(new Callback<ApiModels.ApiResponse<String>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<String>> call,
                                           Response<ApiModels.ApiResponse<String>> response) {
                        if (handleAuthFailure(context, response.code(), response)) {
                            return;
                        }
                        ApiModels.ApiResponse<String> body = response.body();
                        if (!response.isSuccessful() || body == null || !body.isSuccess()) {
                            if (callback != null) {
                                callback.onError(errorMessage(body, response.code(),
                                        "심박변이도 건너뛰기에 실패했습니다."));
                            }
                            return;
                        }
                        new SessionManager(context).setRemoteHrvStatus("SKIPPED");
                        if (callback != null) callback.onSuccess(null);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<String>> call, Throwable t) {
                        if (callback != null) {
                            callback.onError("심박변이도 건너뛰기에 실패했습니다.");
                        }
                    }
                });
    }

    public static void savePrediction(Context context, boolean event,
                                      float valence, float arousal) {
        savePrediction(context, event, valence, arousal, null);
    }

    public static void savePrediction(Context context, boolean event,
                                      float valence, float arousal,
                                      ResultCallback<Void> callback) {
        SessionManager session = new SessionManager(context);
        long sessionId = session.getCurrentSessionId(event);
        if (sessionId <= 0) {
            if (callback != null) callback.onError("세션이 없어 예측을 저장하지 못했습니다.");
            return;
        }
        float v = safeFloat(valence, -1.2f, 1.2f);
        float a = safeFloat(arousal, -1.2f, 1.2f);
        session.setLastPrediction(v, a);
        Log.d(TAG, "savePrediction sessionId=" + sessionId
                + " valence=" + v + " arousal=" + a);
        MemberApiClient.service(context).savePrediction(sessionId,
                        new ApiModels.SavePredictionRequest(v, a))
                .enqueue(new Callback<ApiModels.ApiResponse<String>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<String>> call,
                                           Response<ApiModels.ApiResponse<String>> response) {
                        if (handleAuthFailure(context, response.code(), response)) {
                            return;
                        }
                        // 세션당 1회 제약. 결과 화면 재진입 시 409 는 이미 저장된 정상 상태다.
                        if (response.code() == 409) {
                            Log.d(TAG, "savePrediction already saved. sessionId=" + sessionId);
                            session.setPredictionSaved(sessionId);
                            if (callback != null) callback.onSuccess(null);
                            return;
                        }
                        ApiModels.ApiResponse<String> body = response.body();
                        if (!response.isSuccessful() || (body != null && !body.isSuccess())) {
                            String fallback = "예측 저장에 실패했습니다.";
                            String detail = body != null
                                    ? apiErrorMessage(body, response.code(), fallback)
                                    : httpErrorMessage(response, fallback);
                            Log.w(TAG, "savePrediction failed: http=" + response.code()
                                    + " detail=" + detail);
                            if (callback != null) {
                                callback.onError(detail);
                            } else {
                                showToast(context, "예측 저장 실패: " + detail);
                            }
                            return;
                        }
                        Log.d(TAG, "savePrediction ok: sessionId=" + sessionId);
                        session.setPredictionSaved(sessionId);
                        if (callback != null) callback.onSuccess(null);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<String>> call, Throwable t) {
                        Log.w(TAG, "savePrediction error", t);
                        if (callback != null) {
                            callback.onError("예측 저장 중 오류가 발생했습니다.");
                        }
                    }
                });
    }

    /**
     * NaN/Infinity 는 Gson 이 비표준 JSON 으로 직렬화해 400 이 난다.
     * 허용 범위는 필드마다 다르다: 예측·피드백 좌표는 -1.2~1.2, EMA 가중합은 -4~4.
     */
    private static float safeFloat(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
        return Math.max(min, Math.min(max, value));
    }

    /**
     * MATCH/UNKNOWN: reasonCode 없이 전송.
     * MISMATCH: reasonCode 로 사유 전송 (Apidog FeedbackRequest).
     */
    public static void submitFeedback(Context context, boolean event, String choice,
                                      Float correctedValence, Float correctedArousal) {
        SessionManager session = new SessionManager(context);
        long sessionId = session.getCurrentSessionId(event);
        if (sessionId <= 0) return;
        String match = matchResult(choice);
        String reason = null;
        if ("MISMATCH".equals(match)) {
            if (correctedValence == null || correctedArousal == null) {
                showToast(context, "다른 감정 위치를 선택한 뒤 다시 제출해 주세요.");
                return;
            }
            reason = "MANUAL_EDIT";
        }
        // correctedValence/Arousal 은 필수 필드다. 일치·모름이면 예측 좌표를 그대로 보낸다.
        float cv = safeFloat(correctedValence != null
                ? correctedValence : session.getLastPredictionValence(), -1.2f, 1.2f);
        float ca = safeFloat(correctedArousal != null
                ? correctedArousal : session.getLastPredictionArousal(), -1.2f, 1.2f);
        Log.d(TAG, "submitFeedback sessionId=" + sessionId + " match=" + match
                + " valence=" + cv + " arousal=" + ca);
        final String reasonCode = reason;
        if (session.isPredictionSaved(sessionId)) {
            sendFeedback(context, sessionId, match, reasonCode, cv, ca, true);
            return;
        }
        // 예측이 저장된 세션에서만 피드백을 받는다(E00702). 먼저 예측부터 올린다.
        savePredictionThen(context, event, session,
                () -> sendFeedback(context, sessionId, match, reasonCode, cv, ca, false));
    }

    private static void savePredictionThen(Context context, boolean event,
                                           SessionManager session, Runnable next) {
        savePrediction(context, event,
                session.getLastPredictionValence(), session.getLastPredictionArousal(),
                new ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        next.run();
                    }

                    @Override
                    public void onError(String message) {
                        showToast(context, "예측 저장에 실패해 피드백을 보내지 못했습니다: " + message);
                    }
                });
    }

    private static void sendFeedback(Context context, long sessionId, String match,
                                     String reasonCode, float cv, float ca, boolean allowRetry) {
        MemberApiClient.service(context).submitFeedback(sessionId,
                        new ApiModels.FeedbackRequest(match, reasonCode, cv, ca,
                                System.currentTimeMillis()))
                .enqueue(new Callback<ApiModels.ApiResponse<String>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<String>> call,
                                           Response<ApiModels.ApiResponse<String>> response) {
                        if (handleAuthFailure(context, response.code(), response)) {
                            return;
                        }
                        ApiModels.ApiResponse<String> body = response.body();
                        if (!response.isSuccessful() || (body != null && !body.isSuccess())) {
                            String fallback = "피드백 전송에 실패했습니다.";
                            String detail = body != null
                                    ? apiErrorMessage(body, response.code(), fallback)
                                    : httpErrorMessage(response, fallback);
                            Log.w(TAG, "submitFeedback failed: http=" + response.code()
                                    + " detail=" + detail);
                            // E00702 = 예측 미저장. 저장 플래그가 어긋난 경우라 한 번만 복구 시도.
                            if (allowRetry && isPredictionMissing(response.code(), body)) {
                                new SessionManager(context).setPredictionSaved(0L);
                                Log.d(TAG, "submitFeedback retry after saving prediction");
                                retryFeedbackAfterPrediction(context, sessionId, match,
                                        reasonCode, cv, ca);
                                return;
                            }
                            showToast(context, "피드백 제출 실패: " + detail);
                            return;
                        }
                        Log.d(TAG, "submitFeedback ok: sessionId=" + sessionId);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<String>> call, Throwable t) {
                        Log.w(TAG, "submitFeedback error", t);
                        showToast(context, "피드백 전송 중 오류가 발생했습니다.");
                    }
                });
    }

    private static boolean isPredictionMissing(int httpCode, ApiModels.ApiResponse<?> body) {
        if (body != null && "E00702".equals(body.code)) return true;
        return httpCode == 404;
    }

    private static void retryFeedbackAfterPrediction(Context context, long sessionId, String match,
                                                     String reasonCode, float cv, float ca) {
        SessionManager session = new SessionManager(context);
        savePrediction(context, session.getCurrentSessionId(true) == sessionId,
                session.getLastPredictionValence(), session.getLastPredictionArousal(),
                new ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        sendFeedback(context, sessionId, match, reasonCode, cv, ca, false);
                    }

                    @Override
                    public void onError(String message) {
                        showToast(context, "예측 저장에 실패해 피드백을 보내지 못했습니다: " + message);
                    }
                });
    }

    public static void fetchEmaQuestions(Context context, boolean event,
                                         ResultCallback<List<ApiModels.QuestionResponse>> callback) {
        fetchEmaQuestions(context, event, 0, callback);
    }

    private static void fetchEmaQuestions(Context context, boolean event, int attempt,
                                          ResultCallback<List<ApiModels.QuestionResponse>> callback) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) {
            callback.onError("세션이 없습니다. HRV 측정 또는 건너뛰기를 먼저 완료해 주세요.");
            return;
        }
        Log.d(TAG, "fetchEmaQuestions sessionId=" + sessionId + " event=" + event);
        MemberApiClient.service(context).getEmaQuestions(sessionId)
                .enqueue(new Callback<ApiModels.ApiResponse<List<ApiModels.QuestionResponse>>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<List<ApiModels.QuestionResponse>>> call,
                                           Response<ApiModels.ApiResponse<List<ApiModels.QuestionResponse>>> response) {
                        if (handleAuthFailure(context, response.code(), response)) {
                            return;
                        }
                        ApiModels.ApiResponse<List<ApiModels.QuestionResponse>> body = response.body();
                        if (!response.isSuccessful() || body == null || !body.isSuccess()) {
                            if (attempt < 2 && response.code() >= 500) {
                                retryFetchEmaQuestions(context, event, attempt + 1, callback);
                                return;
                            }
                            String fallback = "문항을 불러오지 못했습니다.";
                            String detail = body != null
                                    ? apiErrorMessage(body, response.code(), fallback)
                                    : httpErrorMessage(response, fallback);
                            // E00301 = 연구기간에 문항 버전이 배정되지 않음. 서버 데이터 설정 문제라
                            // 앱에서 재시도해도 풀리지 않으므로 서버 메시지를 그대로 노출한다.
                            if (response.code() == 404) {
                                detail = detail + "\n연구 관리자에게 문항 배정을 요청해 주세요.";
                            }
                            Log.w(TAG, "fetchEmaQuestions failed http=" + response.code()
                                    + " url=" + call.request().url()
                                    + " code=" + (body != null ? body.code : null)
                                    + " hrvStatus=" + new SessionManager(context).getRemoteHrvStatus());
                            callback.onError(detail);
                            return;
                        }
                        List<ApiModels.QuestionResponse> list = body.data != null ? body.data : new ArrayList<>();
                        if (list.isEmpty()) {
                            callback.onError("설문 문항이 아직 준비되지 않았습니다.");
                            return;
                        }
                        Collections.sort(list, Comparator.comparingInt(q -> q.orderNo));
                        normalizeQuestionIds(list);
                        if (!hasValidQuestionIds(list)) {
                            Log.w(TAG, "fetchEmaQuestions: no valid questionId in response");
                            callback.onError("서버 문항 ID를 받지 못했습니다. Nest 담당자에게 문의해 주세요.");
                            return;
                        }
                        String encoded = encodeQuestions(list);
                        new SessionManager(context).setEmaQuestionsCache(sessionId, encoded);
                        callback.onSuccess(list);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<List<ApiModels.QuestionResponse>>> call,
                                          Throwable t) {
                        if (attempt < 2) {
                            retryFetchEmaQuestions(context, event, attempt + 1, callback);
                            return;
                        }
                        Log.w(TAG, "fetchEmaQuestions error", t);
                        callback.onError("문항을 불러오지 못했습니다.");
                    }
                });
    }

    private static void retryFetchEmaQuestions(Context context, boolean event, int attempt,
                                               ResultCallback<List<ApiModels.QuestionResponse>> callback) {
        new android.os.Handler(Looper.getMainLooper()).postDelayed(
                () -> fetchEmaQuestions(context, event, attempt, callback), 600L);
    }

    public static String encodeQuestions(List<ApiModels.QuestionResponse> questions) {
        if (questions == null) return "[]";
        return GSON.toJson(questions);
    }

    public static List<ApiModels.QuestionResponse> decodeQuestions(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            java.lang.reflect.Type type = new TypeToken<List<ApiModels.QuestionResponse>>() {
            }.getType();
            List<ApiModels.QuestionResponse> list = GSON.fromJson(json, type);
            if (list != null) {
                normalizeQuestionIds(list);
            }
            return list;
        } catch (Exception e) {
            Log.w(TAG, "decodeQuestions failed", e);
            return null;
        }
    }

    public static List<ApiModels.QuestionResponse> loadCachedEmaQuestions(Context context, boolean event) {
        SessionManager session = new SessionManager(context);
        long sessionId = session.getCurrentSessionId(event);
        return decodeQuestions(session.getEmaQuestionsCache(sessionId));
    }

    public static long resolveQuestionId(ApiModels.QuestionResponse q) {
        if (q == null) return 0L;
        if (q.questionId > 0) return q.questionId;
        if (q.id > 0) return q.id;
        return 0L;
    }

    private static void normalizeQuestionIds(List<ApiModels.QuestionResponse> list) {
        if (list == null) return;
        for (ApiModels.QuestionResponse q : list) {
            long resolved = resolveQuestionId(q);
            if (resolved > 0) {
                q.questionId = resolved;
            }
        }
    }

    private static boolean hasValidQuestionIds(List<ApiModels.QuestionResponse> list) {
        if (list == null || list.isEmpty()) return false;
        for (ApiModels.QuestionResponse q : list) {
            if (resolveQuestionId(q) > 0) return true;
        }
        return false;
    }

    public static void submitEma(Context context, boolean event,
                                 List<ApiModels.SubmitEmaRequest> responses,
                                 float valence, float arousal) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) {
            showToast(context, "세션이 없어 설문을 전송하지 못했습니다.");
            return;
        }
        if (responses == null || responses.isEmpty()) {
            showToast(context, "서버 문항 ID가 없어 설문 결과를 서버에 보내지 못했습니다.");
            return;
        }
        // EMA 는 Nandy 가중합 원시값이라 예측 좌표와 스케일이 다르다 (-4 ~ 4)
        float v = safeFloat(valence, -4f, 4f);
        float a = safeFloat(arousal, -4f, 4f);
        Log.d(TAG, "submitEma sessionId=" + sessionId + " count=" + responses.size()
                + " valence=" + v + " arousal=" + a);
        MemberApiClient.service(context).submitEma(sessionId,
                        new ApiModels.SubmitEmaListRequest(responses, v, a,
                                System.currentTimeMillis()))
                .enqueue(new LoggingCallback(context, "설문 제출"));
    }

    public static void uploadVoiceDiary(Context context, boolean event, File audioFile,
                                        int durationSec, long recordedAtMs) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0 || audioFile == null || !audioFile.exists()) return;
        if (durationSec <= 0) {
            showToast(context, "녹음 길이가 0초라 음성 일기를 전송하지 못했습니다.");
            return;
        }
        // 서버 허용 범위: 0 초과 200 이하
        int duration = Math.min(200, durationSec);
        long recordedAt = recordedAtMs > 0 ? recordedAtMs : System.currentTimeMillis();
        RequestBody body = RequestBody.create(audioFile, MediaType.parse("audio/mp4"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("audio", audioFile.getName(), body);
        Log.d(TAG, "uploadVoiceDiary sessionId=" + sessionId + " durationSec=" + duration
                + " recordedAt=" + recordedAt + " audioBytes=" + audioFile.length());
        MemberApiClient.service(context).uploadVoiceDiary(sessionId, duration, recordedAt, part)
                .enqueue(new LoggingCallback(context, "음성 일기 업로드"));
    }

    public static void registerFcmToken(Context context, String token) {
        if (token == null || token.trim().isEmpty()) return;
        SessionManager session = new SessionManager(context);
        session.setLastFcmToken(token);
        if (!session.isLoggedIn() || session.getAccessToken().isEmpty()) return;
        MemberApiClient.service(context).putFcmToken(new ApiModels.FcmTokenRequest(token))
                .enqueue(new EmptyCallback());
    }

    public static void clearCompletedSession(Context context, boolean event) {
        new SessionManager(context).clearCurrentSession(event);
    }

    public static void showError(Context context, String message) {
        Runnable show = () -> new AlertDialog.Builder(context)
                .setTitle("오류")
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            show.run();
        } else {
            new android.os.Handler(Looper.getMainLooper()).post(show);
        }
    }

    public static void showToast(Context context, String message) {
        new android.os.Handler(Looper.getMainLooper()).post(
                () -> Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        );
    }

    /**
     * 동의/인증 실패 시 자동 로그아웃 후 8자리 로그인 화면으로 이동.
     * @return always true (호출부에서 early-return 용)
     */
    public static boolean forceRelogin(Context context, String message) {
        SessionManager session = new SessionManager(context);
        session.clearAuthForRelogin();
        final String msg = (message == null || message.isEmpty())
                ? "다시 로그인해 주세요."
                : message;
        Runnable go = () -> {
            Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            Intent i = new Intent(context, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(i);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            go.run();
        } else {
            new android.os.Handler(Looper.getMainLooper()).post(go);
        }
        return true;
    }

    private static boolean handleAuthFailure(Context context, int httpCode, Response<?> response) {
        if (httpCode == 401) {
            return forceRelogin(context, "인증이 만료되었습니다. 다시 로그인해 주세요.");
        }
        if (httpCode == 403) {
            String detail = httpErrorMessage(response, "연구 참여 동의가 필요합니다.");
            return forceRelogin(context, detail);
        }
        return false;
    }

    /** 로그인/동의/refresh 응답: 루트 TokenPair 또는 ApiResponse.data 모두 지원 */
    public static ApiModels.TokenPair parseTokenResponse(Response<ResponseBody> response) {
        if (response == null) return null;
        try {
            String raw = null;
            if (response.body() != null) {
                raw = response.body().string();
            } else if (response.errorBody() != null) {
                raw = response.errorBody().string();
            }
            return parseTokenJson(raw);
        } catch (Exception e) {
            Log.w(TAG, "parseTokenResponse failed", e);
            return null;
        }
    }

    private static ApiModels.TokenPair parseTokenJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            com.google.gson.JsonObject obj = GSON.fromJson(raw, com.google.gson.JsonObject.class);
            if (obj == null) return null;
            if (obj.has("accessToken")) {
                return validToken(GSON.fromJson(obj, ApiModels.TokenPair.class));
            }
            if (obj.has("data") && obj.get("data").isJsonObject()) {
                return validToken(GSON.fromJson(obj.getAsJsonObject("data"), ApiModels.TokenPair.class));
            }
        } catch (Exception e) {
            Log.w(TAG, "parseTokenJson failed", e);
        }
        return null;
    }

    private static ApiModels.TokenPair validToken(ApiModels.TokenPair pair) {
        if (pair == null) return null;
        if (pair.accessToken == null || pair.accessToken.trim().isEmpty()) return null;
        if (pair.refreshToken == null || pair.refreshToken.trim().isEmpty()) return null;
        return pair;
    }

    private static String httpErrorMessage(Response<?> response, String fallback) {
        if (response == null) return fallback;
        try {
            ResponseBody errBody = response.errorBody();
            // 성공 응답인데 파싱 실패인 경우 body 를 이미 소비했을 수 있음
            if (errBody != null) {
                String raw = errBody.string();
                Log.w(TAG, "error body(" + response.code() + "): " + raw);
                String parsed = parseErrorRaw(raw);
                if (parsed != null) return parsed;
            }
        } catch (Exception ignored) {
        }
        if (response.code() == 401) return "인증이 만료되었습니다. 다시 로그인해 주세요.";
        if (response.code() == 403) return "권한이 없습니다. 다시 로그인해 주세요.";
        if (response.code() == 404) return "요청한 정보를 찾을 수 없습니다.";
        return fallback + " (HTTP " + response.code() + ")";
    }

    private static String parseErrorRaw(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            ApiModels.ApiResponse<?> err = GSON.fromJson(raw, ApiModels.ApiResponse.class);
            if (err != null && err.message != null && !err.message.isEmpty()) {
                if (err.code != null && !err.code.isEmpty()) {
                    return err.message + " (" + err.code + ")";
                }
                return err.message;
            }
            com.google.gson.JsonObject obj = GSON.fromJson(raw, com.google.gson.JsonObject.class);
            if (obj != null && obj.has("message") && !obj.get("message").isJsonNull()) {
                String msg = obj.get("message").getAsString();
                if (obj.has("error") && !obj.get("error").isJsonNull()) {
                    String errCode = obj.get("error").getAsString();
                    if ("CONSENT_REQUIRED".equals(errCode)) {
                        return "연구 참여 동의가 필요합니다. 다시 로그인해 주세요.";
                    }
                    return msg;
                }
                return msg;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String matchResult(String choice) {
        if ("agree".equalsIgnoreCase(choice)) return "MATCH";
        if ("unknown".equalsIgnoreCase(choice)) return "UNKNOWN";
        return "MISMATCH";
    }

    private static String errorMessage(ApiModels.ApiResponse<?> body, int httpCode, String fallback) {
        return apiErrorMessage(body, httpCode, fallback);
    }

    private static String apiErrorMessage(ApiModels.ApiResponse<?> body, int httpCode, String fallback) {
        if (body != null && body.code != null) {
            if ("E00201".equals(body.code)) {
                return "세션이 없습니다. HRV 측정 또는 건너뛰기 후 다시 시도해 주세요.";
            }
            if ("E00301".equals(body.code)) {
                return "설문 문항이 아직 준비되지 않았습니다.";
            }
        }
        if (body != null) {
            if (body.message != null && !body.message.isEmpty()) {
                if (body.code != null && !body.code.isEmpty()) {
                    return body.message + " (" + body.code + ")";
                }
                return body.message;
            }
            if (body.code != null && !body.code.isEmpty()) {
                return fallback + " (" + body.code + ")";
            }
        }
        if (httpCode == 401) return "인증이 만료되었습니다. 다시 로그인해 주세요.";
        if (httpCode == 403) return "연구 참여 동의가 필요합니다. 다시 로그인해 주세요.";
        if (httpCode > 0) return fallback + " (HTTP " + httpCode + ")";
        return fallback;
    }

    private static File buildSignalFile(Context context) {
        try {
            float[] wave = LastEcgResult.lastRawSignal;
            if (wave == null || wave.length == 0) {
                wave = LastEcgResult.lastSpike;
            }
            if (wave == null || wave.length == 0) return null;
            File out = new File(context.getCacheDir(), "ecg-raw-signal.csv");
            FileOutputStream fos = new FileOutputStream(out, false);
            StringBuilder sb = new StringBuilder(wave.length * 12);
            sb.append("index,value\n");
            for (int i = 0; i < wave.length; i++) {
                sb.append(i).append(',').append(wave[i]).append('\n');
            }
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
            return out;
        } catch (Exception e) {
            Log.w(TAG, "buildSignalFile failed", e);
            return null;
        }
    }

    public static List<ApiModels.SubmitEmaRequest> buildEmaRequests(long[] questionIds, int[] answers) {
        List<ApiModels.SubmitEmaRequest> out = new ArrayList<>();
        if (questionIds == null || answers == null) return out;
        int n = Math.min(questionIds.length, answers.length);
        for (int i = 0; i < n; i++) {
            long qid = questionIds[i];
            if (qid > 0 && answers[i] >= 1 && answers[i] <= 5) {
                out.add(new ApiModels.SubmitEmaRequest(qid, answers[i]));
            }
        }
        return out;
    }

    private static final class LoggingCallback implements Callback<ApiModels.ApiResponse<String>> {
        private final String label;
        private final Context context;

        LoggingCallback(Context context, String label) {
            this.label = label;
            this.context = context;
        }

        @Override
        public void onResponse(Call<ApiModels.ApiResponse<String>> call,
                               Response<ApiModels.ApiResponse<String>> response) {
            if (handleAuthFailure(context, response.code(), response)) {
                return;
            }
            ApiModels.ApiResponse<String> body = response.body();
            // HTTP 성공 + 본문 없음은 정상 처리 (서버가 빈 본문을 주는 경우가 있음)
            boolean failed = !response.isSuccessful() || (body != null && !body.isSuccess());
            if (!failed) {
                return;
            }
            String fallback = label + " 전송에 실패했습니다.";
            String detail = body != null
                    ? apiErrorMessage(body, response.code(), fallback)
                    : httpErrorMessage(response, fallback);
            Log.w(TAG, label + " failed: http=" + response.code()
                    + " url=" + call.request().url()
                    + " detail=" + detail);
            if (context != null) {
                showToast(context, label + " 실패: " + detail);
            }
        }

        @Override
        public void onFailure(Call<ApiModels.ApiResponse<String>> call, Throwable t) {
            Log.w(TAG, label + " error", t);
            if (context != null) {
                showToast(context, label + " 전송 중 오류가 발생했습니다.");
            }
        }
    }

    private static final class EmptyCallback implements Callback<ResponseBody> {
        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
        }
    }
}
