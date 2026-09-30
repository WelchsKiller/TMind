package com.nest.tmind.api;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.ui.LoginActivity;
import com.nest.tmind.util.EcgUploadCrypto;
import com.nest.tmind.util.InterventionClassifier;
import com.nest.tmind.util.MissionManager;
import com.nest.tmind.util.SessionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType CSV = MediaType.parse("text/csv");
    /** 공개키 조회가 실패했을 때 재시도 간격. 서버 배포 후엔 이 간격 안에 암호화로 전환된다. */
    private static final long CRYPTO_RETRY_BACKOFF_MS = 6L * 60L * 60L * 1000L;
    /** E00503/E00504 는 한 번만 재전송한다. 원인이 그대로면 반복해도 같은 결과다. */
    private static final int HRV_MAX_ATTEMPTS = 2;
    private static volatile ApiModels.TodayResponse lastToday;

    private MemberApiManager() {
    }

    public static ApiModels.TodayResponse lastToday() {
        return lastToday;
    }

    /** 심박·설문·일기 저장 성공 후, 그 시점의 /today 로 eventAvailable 등을 다시 받는다. */
    public static void refreshTodayAfterSave(Context context) {
        if (context == null) return;
        Log.i("TMindToday", "refresh /today after save");
        fetchToday(context.getApplicationContext(), new ResultCallback<ApiModels.TodayResponse>() {
            @Override
            public void onSuccess(ApiModels.TodayResponse data) {
                Log.i("TMindToday", "refresh /today after save ok eventAvailable="
                        + (data != null ? data.eventAvailable : null));
            }

            @Override
            public void onError(String message) {
                Log.w("TMindToday", "refresh /today after save failed: " + message);
            }
        });
    }

    public interface ResultCallback<T> {
        void onSuccess(T data);

        void onError(String message);
    }

    public static void login(Context context, String code, ResultCallback<ApiModels.TokenPair> callback) {
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
                        callback.onSuccess(pair);
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        callback.onError("서버에 연결할 수 없습니다.");
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
                    if (callback != null) {
                        callback.onError(errorMessage(response, body, "오늘 세션 정보를 불러오지 못했습니다."));
                    }
                    return;
                }
                SessionManager session = new SessionManager(context);
                lastToday = body.data;
                session.setRemoteEventActive(body.data.eventActive);
                session.setRemoteEventAvailable(resolveEventAvailable(body.data));
                logToday(body.data);
                applyTodaySessionState(session, body.data);
                session.setEventGuideText(body.data.eventGuideText);
                session.setServerDate(body.data.serverDate);
                warnOnDateSkew(context, body.data.serverDate);
                if (callback != null) callback.onSuccess(body.data);
            }

            @Override
            public void onFailure(Call<ApiModels.ApiResponse<ApiModels.TodayResponse>> call, Throwable t) {
                if (callback != null) {
                    callback.onError("오늘 세션 정보를 불러오지 못했습니다.");
                }
            }
        });
    }

    public static boolean isTodayHrvValid(ApiModels.TodayResponse data) {
        return data != null && data.hrvStatus != null
                && "VALID".equalsIgnoreCase(data.hrvStatus.trim());
    }

    public static boolean isTodayHrvInvalid(ApiModels.TodayResponse data) {
        return data != null && data.hrvStatus != null
                && "INVALID".equalsIgnoreCase(data.hrvStatus.trim());
    }

    public static boolean isTodayHrvStageDone(ApiModels.TodayResponse data) {
        return data != null && Boolean.TRUE.equals(data.hrvDone);
    }

    /** SKIPPED 는 서버에서 폐지됨. 예전 값이 남아 있어도 미수행으로 본다. */
    private static String normalizeHrvStatus(String raw) {
        if (raw == null) return "";
        String status = raw.trim();
        if (status.isEmpty() || "SKIPPED".equalsIgnoreCase(status)) return "";
        return status;
    }

    /** 추가 측정 버튼은 eventAvailable 만 본다. eventActive 는 관리자 on/off. */
    public static boolean resolveEventAvailable(ApiModels.TodayResponse data) {
        return data != null && Boolean.TRUE.equals(data.eventAvailable);
    }

    public static void logToday(ApiModels.TodayResponse data) {
        if (data == null) {
            Log.i("TMindToday", "GET /today data=null");
            return;
        }
        Log.i("TMindToday", "GET /today"
                + " currentType=" + data.currentType
                + " currentSessionId=" + data.currentSessionId
                + " hrvDone=" + data.hrvDone
                + " hrvStatus=" + data.hrvStatus
                + " emaDone=" + data.emaDone
                + " diaryDone=" + data.diaryDone
                + " eventActive=" + data.eventActive
                + " eventAvailable=" + data.eventAvailable
                + " missedType=" + data.missedType
                + " completedCount=" + data.completedCount
                + " totalCount=" + data.totalCount
                + " participatedDays=" + data.participatedDays
                + " stars=" + data.stars
                + " serverDate=" + data.serverDate
                + " eventGuideText=" + data.eventGuideText);
        Log.i("TMindToday", "GET /today json=" + GSON.toJson(data));
    }

    /**
     * /today 값을 그대로 반영한다. currentSessionId 가 있으면 그 세션을 이어가고,
     * 없으면 다음 시작 때 POST /session 한다. 심박 완료·재측정 여부는 hrvDone / hrvStatus.
     */
    private static void applyTodaySessionState(SessionManager session, ApiModels.TodayResponse data) {
        if (data == null) return;
        String prevDate = session.getServerDate();
        String newDate = data.serverDate != null ? data.serverDate.trim() : "";
        if (!newDate.isEmpty() && !prevDate.isEmpty() && !newDate.equals(prevDate)) {
            Log.i(TAG, "server date changed " + prevDate + " -> " + newDate
                    + ", drop cached sessions");
            session.clearDayScopedServerState();
        }

        long todaySid = data.currentSessionId != null ? data.currentSessionId : 0L;
        session.setServerTodaySessionId(todaySid);
        if (todaySid > 0) {
            session.setCurrentSessionId(false, todaySid);
        } else {
            session.clearCurrentSession(false);
        }

        session.setRemoteHrvStatus(normalizeHrvStatus(data.hrvStatus));
        session.setTodayEmaDone(Boolean.TRUE.equals(data.emaDone));
        session.setTodayDiaryDone(Boolean.TRUE.equals(data.diaryDone));

        if (Boolean.TRUE.equals(data.emaDone) && todaySid > 0) {
            session.setRemoteEmaDone(todaySid);
        }
    }

    /**
     * 미션 진행 상태는 단말 날짜(yyyyMMdd)로 키를 만들기 때문에, 단말 시계가 틀어지면
     * 서버가 판정한 날짜와 하루치 진행이 어긋난다. serverDate 로 그 상황을 감지한다.
     */
    private static boolean dateSkewWarned = false;

    private static void warnOnDateSkew(Context context, String serverDate) {
        if (serverDate == null || serverDate.trim().isEmpty()) return;
        String device = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date());
        if (device.equals(serverDate.trim())) {
            dateSkewWarned = false;
            return;
        }
        Log.w(TAG, "date skew: server=" + serverDate + " device=" + device);
        if (dateSkewWarned) return;
        dateSkewWarned = true;
        showToast(context, "휴대폰 날짜(" + device + ")가 서버 기준 날짜(" + serverDate.trim()
                + ")와 다릅니다. 날짜·시간 자동 설정을 켜주세요.");
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
                            callback.onError(errorMessage(response, body, "참여 현황을 불러오지 못했습니다."));
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

    private interface Continuation {
        void proceed();
    }

    /**
     * 서버 공개키를 확보한 뒤 다음 단계로 넘긴다. 공개키 API 는 아직 미구현이라
     * 실패해도 진행을 막지 않고, 평문 CSV 폴백으로 업로드하게 둔다.
     * 실패가 반복되면 백오프를 걸어 매 측정마다 500 을 유발하지 않는다.
     */
    private static void ensureCryptoPublicKey(Context context, Continuation next) {
        SessionManager session = new SessionManager(context);
        if (session.hasCryptoPublicKey() || session.isCryptoPrefetchBlocked()) {
            next.proceed();
            return;
        }
        MemberApiClient.service(context).getPublicKey()
                .enqueue(new Callback<ApiModels.ApiResponse<ApiModels.PublicKeyResponse>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<ApiModels.PublicKeyResponse>> call,
                                           Response<ApiModels.ApiResponse<ApiModels.PublicKeyResponse>> response) {
                        ApiModels.ApiResponse<ApiModels.PublicKeyResponse> body = response.body();
                        ApiModels.PublicKeyResponse data = body != null ? body.data : null;
                        boolean ok = response.isSuccessful() && body != null && body.isSuccess()
                                && data != null
                                && data.keyId != null && !data.keyId.isEmpty()
                                && data.publicKey != null && !data.publicKey.isEmpty();
                        if (ok) {
                            session.setCryptoPublicKey(data.keyId, data.publicKey);
                            session.clearCryptoPrefetchBlock();
                            Log.i(TAG, "crypto public key ready: keyId=" + data.keyId);
                        } else {
                            session.blockCryptoPrefetchUntil(
                                    System.currentTimeMillis() + CRYPTO_RETRY_BACKOFF_MS);
                            Log.i(TAG, "crypto public key unavailable (http=" + response.code()
                                    + "), falling back to plain CSV");
                        }
                        next.proceed();
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<ApiModels.PublicKeyResponse>> call,
                                          Throwable t) {
                        session.blockCryptoPrefetchUntil(
                                System.currentTimeMillis() + CRYPTO_RETRY_BACKOFF_MS);
                        Log.i(TAG, "crypto public key fetch failed: " + t.getMessage());
                        next.proceed();
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
        if (!event) {
            long todayId = session.getServerTodaySessionId();
            if (todayId > 0) {
                session.setCurrentSessionId(false, todayId);
                callback.onSuccess(todayId);
                return;
            }
            postStartSession(context, session, false, callback);
            return;
        }
        long cachedEvent = session.getCurrentSessionId(true);
        if (isUsableEventSession(session, cachedEvent)) {
            callback.onSuccess(cachedEvent);
            return;
        }
        if (cachedEvent > 0) {
            Log.w(TAG, "drop stale event sessionId=" + cachedEvent
                    + " main=" + session.getCurrentSessionId(false)
                    + " today=" + session.getServerTodaySessionId());
            session.clearCurrentSession(true);
        }
        postStartSession(context, session, true, callback);
    }

    /** 추가 측정은 예측이 끝난 정규 세션과 다른 Event 세션이 필요하다. */
    public static void startFreshEventSession(Context context, ResultCallback<Long> callback) {
        SessionManager session = new SessionManager(context);
        if (!session.hasAccessToken()) {
            forceRelogin(context, "로그인이 필요합니다. 다시 로그인해 주세요.");
            return;
        }
        session.clearCurrentSession(true);
        postStartSession(context, session, true, callback);
    }

    private static boolean isUsableEventSession(SessionManager session, long eventId) {
        if (eventId <= 0) return false;
        long mainId = session.getCurrentSessionId(false);
        long todayId = session.getServerTodaySessionId();
        if (mainId > 0 && eventId == mainId) return false;
        if (todayId > 0 && eventId == todayId) return false;
        return !session.isPredictionSaved(eventId);
    }

    private static void postStartSession(Context context, SessionManager session, boolean event,
                                         ResultCallback<Long> callback) {
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
                        if (event && !isUsableEventSession(session, data.sessionId)) {
                            Log.w(TAG, "startSession isEvent=true returned regular sessionId="
                                    + data.sessionId);
                            callback.onError("추가 측정용 세션을 만들지 못했습니다. 홈에서 다시 시도해 주세요.");
                            return;
                        }
                        session.setCurrentSessionId(event, data.sessionId);
                        if (data.hrvStatus != null) {
                            session.setRemoteHrvStatus(normalizeHrvStatus(data.hrvStatus));
                        }
                        if (data.emaDone != null) {
                            session.setTodayEmaDone(data.emaDone);
                        }
                        if (data.diaryDone != null) {
                            session.setTodayDiaryDone(data.diaryDone);
                        }
                        Log.i(TAG, "startSession ok isEvent=" + event + " sessionId=" + data.sessionId);
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
        SessionManager session = new SessionManager(context);
        long sessionId = session.getCurrentSessionId(event);
        if (sessionId <= 0) {
            reportHrvFailure(context, callback, "세션이 없어 심박변이도를 전송하지 못했습니다.");
            return;
        }
        if (event && !isUsableEventSession(session, sessionId)) {
            reportHrvFailure(context, callback,
                    "추가 측정용 세션이 없습니다. 홈에서 추가 측정을 다시 시작해 주세요.");
            return;
        }
        if (session.isPredictionSaved(sessionId)) {
            long prev = session.getLastHrvMeasuredAt(sessionId);
            long at = measuredAtMs > 0 ? measuredAtMs : 0L;
            if (prev <= 0L || at != prev) {
                reportHrvFailure(context, callback,
                        "결과를 확인한 뒤에는 심박변이도를 다시 측정할 수 없어요.");
                return;
            }
        }
        ensureCryptoPublicKey(context, new Continuation() {
            @Override
            public void proceed() {
                sendHrv(context, sessionId, measuredAtMs, measurementValid, 1, callback);
            }
        });
    }

    /** 5분 원본 CSV 직렬화와 암호화는 MB 단위 작업이라 메인 스레드에서 돌리지 않는다. */
    private static void sendHrv(Context context, long sessionId, long measuredAtMs,
                                boolean measurementValid, int attempt,
                                ResultCallback<Void> callback) {
        long measuredAt = measuredAtMs > 0 ? measuredAtMs : System.currentTimeMillis();
        new Thread(() -> {
            SignalPart part = buildSignalPart(context, measuredAt);
            new android.os.Handler(Looper.getMainLooper()).post(() -> {
                if (part == null) {
                    reportHrvFailure(context, callback,
                            "ECG 신호 파일이 없어 심박변이도를 전송하지 못했습니다.");
                    return;
                }
                postHrv(context, sessionId, measuredAt, measurementValid, part, attempt, callback);
            });
        }, "ecg-signal-build").start();
    }

    private static void postHrv(Context context, long sessionId, long measuredAt,
                                boolean measurementValid, SignalPart signalPart, int attempt,
                                ResultCallback<Void> callback) {
        MultipartBody.Part data = MultipartBody.Part.createFormData("data", null,
                RequestBody.create(GSON.toJson(buildHrvSaveRequest(measuredAt, measurementValid)),
                        JSON));
        MultipartBody.Part signal = MultipartBody.Part.createFormData("signal",
                signalPart.uploadName, RequestBody.create(signalPart.file, signalPart.contentType));
        Log.d(TAG, "uploadHrv sessionId=" + sessionId + " measuredAt=" + measuredAt
                + " valid=" + measurementValid
                + " signalName=" + signalPart.uploadName
                + " signalBytes=" + signalPart.file.length()
                + " samples=" + (LastEcgResult.lastRawSignal != null
                        ? LastEcgResult.lastRawSignal.length
                        : (LastEcgResult.lastSpike != null ? LastEcgResult.lastSpike.length : 0)));
        MemberApiClient.service(context).uploadHrv(sessionId, data, signal)
                .enqueue(new Callback<ApiModels.ApiResponse<JsonElement>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<JsonElement>> call,
                                           Response<ApiModels.ApiResponse<JsonElement>> response) {
                        if (handleAuthFailure(context, response.code(), response)) {
                            return;
                        }
                        ApiModels.ApiResponse<JsonElement> body = response.body();
                        if (!response.isSuccessful() || (body != null && !body.isSuccess())) {
                            String fallback = "심박변이도 전송에 실패했습니다.";
                            // errorBody 는 한 번만 읽을 수 있어, 코드와 메시지를 같은 raw 에서 뽑는다.
                            String raw = body != null ? null : readErrorBody(response);
                            String code = body != null ? body.code : errorCodeFromRaw(raw);
                            String detail = body != null
                                    ? apiErrorMessage(body, response.code(), fallback)
                                    : httpErrorMessage(response, raw, fallback);
                            Log.w(TAG, "uploadHrv failed: http=" + response.code()
                                    + " code=" + code
                                    + " url=" + call.request().url() + " detail=" + detail);
                            if (retryHrv(context, sessionId, measuredAt, measurementValid,
                                    attempt, code, callback)) {
                                return;
                            }
                            reportHrvFailure(context, callback, detail);
                            return;
                        }
                        Log.d(TAG, "uploadHrv ok: sessionId=" + sessionId);
                        applyHrvUploadSuccess(context, sessionId, measuredAt, measurementValid);
                        if (callback != null) callback.onSuccess(null);
                        else refreshTodayAfterSave(context);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<JsonElement>> call, Throwable t) {
                        Log.w(TAG, "uploadHrv error", t);
                        reportHrvFailure(context, callback,
                                "심박변이도 전송 중 오류가 발생했습니다. 네트워크를 확인해 주세요.");
                    }
                });
    }

    /**
     * E00503(ECG 누락)은 신호를 다시 만들어, E00504(복호화 실패)는 공개키를 다시 받아
     * 재암호화해서 보내야 한다. 같은 요청을 그대로 재시도하면 동일하게 거부된다.
     * 거부된 요청은 서버에 아무것도 저장되지 않으므로 재전송해도 중복이 생기지 않는다.
     *
     * @return 재전송을 시작했으면 true (호출부는 실패 보고를 건너뛴다)
     */
    private static boolean retryHrv(Context context, long sessionId, long measuredAt,
                                    boolean measurementValid, int attempt, String errorCode,
                                    ResultCallback<Void> callback) {
        if (errorCode == null || attempt >= HRV_MAX_ATTEMPTS) return false;
        if ("E00504".equals(errorCode)) {
            Log.i(TAG, "uploadHrv E00504: 공개키 재조회 후 재암호화 재전송");
            new SessionManager(context).clearCryptoPublicKey();
            ensureCryptoPublicKey(context, () -> sendHrv(context, sessionId, measuredAt,
                    measurementValid, attempt + 1, callback));
            return true;
        }
        if ("E00503".equals(errorCode)) {
            Log.i(TAG, "uploadHrv E00503: ECG 신호 재생성 후 재전송");
            sendHrv(context, sessionId, measuredAt, measurementValid, attempt + 1, callback);
            return true;
        }
        return false;
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

    /**
     * measuredAt 이 기존과 다르면 재측정이다. 설문·일기 Done 을 로컬에서도 되돌린다.
     * 같으면 같은 측정의 재전송이라 진행 상태는 유지한다.
     */
    private static void applyHrvUploadSuccess(Context context, long sessionId, long measuredAt,
                                              boolean measurementValid) {
        SessionManager session = new SessionManager(context);
        long prev = session.getLastHrvMeasuredAt(sessionId);
        boolean remeasure = prev > 0 && prev != measuredAt;
        session.setRemoteHrvStatus(measurementValid ? "VALID" : "INVALID");
        session.setLastHrvMeasuredAt(sessionId, measuredAt);
        ApiModels.TodayResponse cached = lastToday;
        if (cached != null) {
            cached.hrvDone = true;
            cached.hrvStatus = measurementValid ? "VALID" : "INVALID";
        }
        if (!remeasure) return;
        Log.i(TAG, "HRV remeasure: sessionId=" + sessionId
                + " prevMeasuredAt=" + prev + " newMeasuredAt=" + measuredAt);
        session.setTodayEmaDone(false);
        session.setTodayDiaryDone(false);
        session.clearRemoteEmaDone();
        session.clearEmaQuestionsCache();
        session.clearEmaProgress();
        MissionManager mm = new MissionManager(context);
        MissionManager.Session s = mm.isAdditionalMeasureMode()
                ? MissionManager.Session.EVENT
                : MissionManager.mainSessionByHour();
        mm.clearFollowUpMissions(s);
        if (cached != null) {
            cached.emaDone = false;
            cached.diaryDone = false;
        }
    }

    public static void savePrediction(Context context, boolean event,
                                      float valence, float arousal) {
        savePrediction(context, event, valence, arousal, null);
    }

    /** HRV·EMA·일기 저장을 기다리며 보류해 둔 예측을 보낸다. 일기 업로드 성공 직후 호출. */
    private static void flushPendingPrediction(Context context) {
        SessionManager session = new SessionManager(context);
        long pending = session.getPredictionPendingSessionId();
        if (pending <= 0) return;
        if (!session.isRemoteHrvDone() || !session.isRemoteEmaDone(pending)
                || !session.isTodayDiaryDone()) {
            return;
        }
        session.clearPredictionPending();
        Log.i(TAG, "sending deferred prediction sessionId=" + pending);
        savePrediction(context, session.getCurrentSessionId(true) == pending,
                session.getLastPredictionValence(), session.getLastPredictionArousal(), null);
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
        // 세션당 1회 제약이라, 결과 화면 재진입으로 중복 POST 하지 않는다.
        if (session.isPredictionSaved(sessionId)) {
            Log.d(TAG, "savePrediction skip: already saved sessionId=" + sessionId);
            if (callback != null) callback.onSuccess(null);
            return;
        }
        // 서버 순서: HRV → EMA → 일기 → 예측. 일기 전이면 E00212 이다.
        if (!session.isRemoteHrvDone()) {
            session.setPredictionPending(sessionId);
            Log.i(TAG, "savePrediction deferred until HRV saved. sessionId=" + sessionId);
            if (callback != null) {
                callback.onError("심박변이도 서버 저장이 끝나지 않았습니다. 잠시 후 다시 시도해 주세요.");
            }
            return;
        }
        if (!session.isRemoteEmaDone(sessionId)) {
            session.setPredictionPending(sessionId);
            Log.i(TAG, "savePrediction deferred until EMA submitted. sessionId=" + sessionId);
            if (callback != null) {
                callback.onError("마음상태 설문을 먼저 제출해야 합니다.");
            }
            return;
        }
        if (!session.isTodayDiaryDone()) {
            session.setPredictionPending(sessionId);
            Log.i(TAG, "savePrediction deferred until diary uploaded. sessionId=" + sessionId);
            if (callback != null) {
                callback.onError("마음일기를 먼저 저장해야 합니다.");
            }
            return;
        }
        Log.d(TAG, "savePrediction sessionId=" + sessionId
                + " valence=" + v + " arousal=" + a);
        MemberApiClient.service(context).savePrediction(sessionId,
                        new ApiModels.SavePredictionRequest(v, a))
                .enqueue(new Callback<ApiModels.ApiResponse<JsonElement>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<JsonElement>> call,
                                           Response<ApiModels.ApiResponse<JsonElement>> response) {
                        if (handleAuthFailure(context, response.code(), response)) {
                            return;
                        }
                        ApiModels.ApiResponse<JsonElement> body = response.body();
                        if (!response.isSuccessful() || (body != null && !body.isSuccess())) {
                            String fallback = "예측 저장에 실패했습니다.";
                            // 409 를 "이미 저장됨"으로 넘겼더니 직후 피드백이 E00702(예측 미저장)로
                            // 거부됐다. 즉 409 에서 실제 저장은 안 된다. 서버 응답을 그대로 드러낸다.
                            String raw = body != null ? null : readErrorBody(response);
                            String code = body != null ? body.code : errorCodeFromRaw(raw);
                            String detail = body != null
                                    ? apiErrorMessage(body, response.code(), fallback)
                                    : httpErrorMessage(response, raw, fallback);
                            Log.w(TAG, "savePrediction failed: http=" + response.code()
                                    + " code=" + code + " detail=" + detail);
                            // E00206 = HRV 미저장, E00207 = EMA 미제출, E00212 = 일기 미저장.
                            if ("E00206".equals(code) || "E00207".equals(code) || "E00212".equals(code)) {
                                session.setPredictionPending(sessionId);
                            }
                            if (callback != null) {
                                callback.onError(detail);
                            } else if (!"E00206".equals(code) && !"E00207".equals(code)
                                    && !"E00212".equals(code)) {
                                showToast(context, "예측 저장 실패: " + detail);
                            }
                            return;
                        }
                        Log.d(TAG, "savePrediction ok: sessionId=" + sessionId);
                        session.setPredictionSaved(sessionId);
                        session.clearPredictionPending();
                        refreshTodayAfterSave(context);
                        if (callback != null) callback.onSuccess(null);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<JsonElement>> call, Throwable t) {
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
     * MATCH/UNKNOWN: matchResult + occurredAt 만 전송.
     * MISMATCH: 사분면으로 고른 좌표만 전송. reasonCode 필드는 서버에서 제거됨.
     * 일치·모름에 좌표를 넣으면 서버가 E00802 로 거절한다.
     */
    public static void submitFeedback(Context context, boolean event, String choice,
                                      Float correctedValence, Float correctedArousal) {
        SessionManager session = new SessionManager(context);
        long sessionId = session.getCurrentSessionId(event);
        if (sessionId <= 0) return;
        String match = matchResult(choice);
        Float cv = null;
        Float ca = null;
        if ("MISMATCH".equals(match)) {
            if (correctedValence == null || correctedArousal == null) {
                showToast(context, "다른 감정 위치를 선택한 뒤 다시 제출해 주세요.");
                return;
            }
            cv = safeFloat(correctedValence, -1.2f, 1.2f);
            ca = safeFloat(correctedArousal, -1.2f, 1.2f);
        }
        Log.d(TAG, "submitFeedback sessionId=" + sessionId + " match=" + match
                + " valence=" + cv + " arousal=" + ca);
        final Float sendV = cv;
        final Float sendA = ca;
        if (session.isPredictionSaved(sessionId)) {
            sendFeedback(context, sessionId, match, sendV, sendA, true);
            return;
        }
        savePredictionThen(context, event, session,
                () -> sendFeedback(context, sessionId, match, sendV, sendA, false));
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
                                     Float cv, Float ca, boolean allowRetry) {
        MemberApiClient.service(context).submitFeedback(sessionId,
                        new ApiModels.FeedbackRequest(match, cv, ca,
                                System.currentTimeMillis()))
                .enqueue(new Callback<ApiModels.ApiResponse<JsonElement>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<JsonElement>> call,
                                           Response<ApiModels.ApiResponse<JsonElement>> response) {
                        if (handleAuthFailure(context, response.code(), response)) {
                            return;
                        }
                        ApiModels.ApiResponse<JsonElement> body = response.body();
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
                                retryFeedbackAfterPrediction(context, sessionId, match, cv, ca);
                                return;
                            }
                            showToast(context, "피드백 제출 실패: " + detail);
                            return;
                        }
                        Log.d(TAG, "submitFeedback ok: sessionId=" + sessionId);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<JsonElement>> call, Throwable t) {
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
                                                     Float cv, Float ca) {
        SessionManager session = new SessionManager(context);
        savePrediction(context, session.getCurrentSessionId(true) == sessionId,
                session.getLastPredictionValence(), session.getLastPredictionArousal(),
                new ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        sendFeedback(context, sessionId, match, cv, ca, false);
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
            callback.onError("세션이 없습니다. 심박변이도 측정을 먼저 완료해 주세요.");
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
        submitEma(context, event, responses, valence, arousal, null);
    }

    public static void submitEma(Context context, boolean event,
                                 List<ApiModels.SubmitEmaRequest> responses,
                                 float valence, float arousal,
                                 ResultCallback<Void> callback) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) {
            String msg = "세션이 없어 설문을 전송하지 못했습니다.";
            if (callback != null) callback.onError(msg);
            else showToast(context, msg);
            return;
        }
        if (responses == null || responses.isEmpty()) {
            String msg = "서버 문항 ID가 없어 설문 결과를 서버에 보내지 못했습니다.";
            if (callback != null) callback.onError(msg);
            else showToast(context, msg);
            return;
        }
        SessionManager session = new SessionManager(context);
        if (session.isPredictionSaved(sessionId)) {
            String msg = "결과를 확인한 뒤에는 설문을 다시 제출할 수 없어요.";
            if (callback != null) callback.onError(msg);
            else showToast(context, msg);
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
                .enqueue(new Callback<ApiModels.ApiResponse<JsonElement>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<JsonElement>> call,
                                           Response<ApiModels.ApiResponse<JsonElement>> response) {
                        if (handleAuthFailure(context, response.code(), response)) {
                            return;
                        }
                        ApiModels.ApiResponse<JsonElement> body = response.body();
                        boolean failed = !response.isSuccessful()
                                || (body != null && !body.isSuccess());
                        if (failed) {
                            String fallback = "설문 제출 전송에 실패했습니다.";
                            String detail = body != null
                                    ? apiErrorMessage(body, response.code(), fallback)
                                    : httpErrorMessage(response, fallback);
                            Log.w(TAG, "submitEma failed: http=" + response.code()
                                    + " detail=" + detail);
                            if (callback != null) callback.onError(detail);
                            else showToast(context, "설문 제출 실패: " + detail);
                            return;
                        }
                        Log.d(TAG, "submitEma ok: sessionId=" + sessionId);
                        SessionManager session = new SessionManager(context);
                        session.setRemoteEmaDone(sessionId);
                        session.setTodayEmaDone(true);
                        if (callback != null) callback.onSuccess(null);
                        else refreshTodayAfterSave(context);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<JsonElement>> call, Throwable t) {
                        Log.w(TAG, "submitEma error", t);
                        String msg = "설문 제출 전송 중 오류가 발생했습니다.";
                        if (callback != null) callback.onError(msg);
                        else showToast(context, msg);
                    }
                });
    }

    public static void uploadVoiceDiary(Context context, boolean event, File audioFile,
                                        int durationSec, long recordedAtMs) {
        uploadVoiceDiary(context, event, audioFile, durationSec, recordedAtMs, null);
    }

    public static void uploadVoiceDiary(Context context, boolean event, File audioFile,
                                        int durationSec, long recordedAtMs,
                                        ResultCallback<Void> callback) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0 || audioFile == null || !audioFile.exists()) {
            String msg = "세션 또는 녹음 파일이 없어 음성 일기를 전송하지 못했습니다.";
            if (callback != null) callback.onError(msg);
            else showToast(context, msg);
            return;
        }
        if (durationSec <= 0) {
            String msg = "녹음 길이가 0초라 음성 일기를 전송하지 못했습니다.";
            if (callback != null) callback.onError(msg);
            else showToast(context, msg);
            return;
        }
        SessionManager session = new SessionManager(context);
        if (session.isPredictionSaved(sessionId)) {
            String msg = "결과를 확인한 뒤에는 마음일기를 다시 저장할 수 없어요.";
            if (callback != null) callback.onError(msg);
            else showToast(context, msg);
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
                .enqueue(new LoggingCallback(context, "음성 일기 업로드", callback));
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
     * 인증 실패 시 자동 로그아웃 후 8자리 로그인 화면으로 이동.
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
        return false;
    }

    /** 로그인/refresh 응답: 루트 TokenPair 또는 ApiResponse.data 모두 지원 */
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
        return httpErrorMessage(response, readErrorBody(response), fallback);
    }

    /** 에러 코드와 메시지를 모두 써야 하는 곳에서는 raw 를 한 번만 읽어 넘긴다. */
    private static String httpErrorMessage(Response<?> response, String raw, String fallback) {
        if (response == null) return fallback;
        String parsed = parseErrorRaw(raw);
        if (parsed != null) return parsed;
        if (response.code() == 401) return "인증이 만료되었습니다. 다시 로그인해 주세요.";
        if (response.code() == 403) return "권한이 없습니다. 다시 로그인해 주세요.";
        if (response.code() == 404) return "요청한 정보를 찾을 수 없습니다.";
        // nginx 기본 client_max_body_size(1MB) 에 5분 ECG 원본이 걸린다. 앱에서 재시도해도 같다.
        if (response.code() == 413) {
            return "ECG 원본이 서버 업로드 용량 제한을 초과했습니다. 서버 설정 확인이 필요합니다. (HTTP 413)";
        }
        return fallback + " (HTTP " + response.code() + ")";
    }

    /** errorBody 는 스트림이라 한 번만 읽을 수 있다. */
    private static String readErrorBody(Response<?> response) {
        if (response == null) return null;
        try {
            // 성공 응답인데 파싱 실패인 경우 body 를 이미 소비했을 수 있음
            ResponseBody errBody = response.errorBody();
            if (errBody == null) return null;
            String raw = errBody.string();
            Log.w(TAG, "error body(" + response.code() + "): " + raw);
            return raw;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String errorCodeFromRaw(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            ApiModels.ApiResponse<?> err = GSON.fromJson(raw, ApiModels.ApiResponse.class);
            return err != null ? err.code : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String parseErrorRaw(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            ApiModels.ApiResponse<?> err = GSON.fromJson(raw, ApiModels.ApiResponse.class);
            if (err != null && "E00401".equals(err.code)) {
                return "진행 중인 연구 기간이 없습니다. 관리자에게 문의해 주세요.";
            }
            if (err != null && err.message != null && !err.message.isEmpty()) {
                if (err.code != null && !err.code.isEmpty()) {
                    return err.message + " (" + err.code + ")";
                }
                return err.message;
            }
            com.google.gson.JsonObject obj = GSON.fromJson(raw, com.google.gson.JsonObject.class);
            if (obj != null && obj.has("message") && !obj.get("message").isJsonNull()) {
                return obj.get("message").getAsString();
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

    private static String errorMessage(Response<?> response, ApiModels.ApiResponse<?> body,
                                       String fallback) {
        if (body != null) {
            return apiErrorMessage(body, response != null ? response.code() : 0, fallback);
        }
        return httpErrorMessage(response, fallback);
    }

    private static String apiErrorMessage(ApiModels.ApiResponse<?> body, int httpCode, String fallback) {
        if (body != null && body.code != null) {
            if ("E00201".equals(body.code)) {
                return "세션이 없습니다. 심박변이도 측정 후 다시 시도해 주세요.";
            }
            if ("E00301".equals(body.code)) {
                return "설문 문항이 아직 준비되지 않았습니다.";
            }
            if ("E00205".equals(body.code)) {
                return "오늘 오전 또는 오후 세션을 완료한 뒤에 추가 측정을 할 수 있습니다.";
            }
            if ("E00203".equals(body.code)) {
                return "오늘은 추가 측정을 더 할 수 없습니다. (하루 5회, 보류로 끝난 측정도 포함)";
            }
            if ("E00802".equals(body.code)) {
                return "일치하거나 모르겠다고 한 경우에는 다른 위치를 보낼 수 없습니다.";
            }
            if ("E00401".equals(body.code)) {
                return "진행 중인 연구 기간이 없습니다. 관리자에게 문의해 주세요.";
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
        if (httpCode == 403) return "권한이 없습니다. 다시 로그인해 주세요.";
        if (httpCode == 404) return "요청한 정보를 찾을 수 없습니다.";
        if (httpCode > 0) return fallback + " (HTTP " + httpCode + ")";
        return fallback;
    }

    /** multipart signal 파트. 공개키가 있으면 암호화 JSON, 없으면 평문 CSV 폴백. */
    private static final class SignalPart {
        final File file;
        final String uploadName;
        final MediaType contentType;

        SignalPart(File file, String uploadName, MediaType contentType) {
            this.file = file;
            this.uploadName = uploadName;
            this.contentType = contentType;
        }
    }

    private static SignalPart buildSignalPart(Context context, long measuredAt) {
        byte[] csv = buildSignalCsvBytes();
        if (csv == null) return null;
        SessionManager session = new SessionManager(context);
        if (session.hasCryptoPublicKey()) {
            SignalPart encrypted = buildEncryptedSignalPart(context, csv, measuredAt,
                    session.getCryptoKeyId(), session.getCryptoPublicKey());
            if (encrypted != null) return encrypted;
        }
        File out = writeCacheFile(context, "ecg-raw-signal.csv", csv);
        return out != null ? new SignalPart(out, out.getName(), CSV) : null;
    }

    private static SignalPart buildEncryptedSignalPart(Context context, byte[] csv, long measuredAt,
                                                       String keyId, String publicKeyPem) {
        try {
            EcgUploadCrypto.EncryptedPayload payload = EcgUploadCrypto.encrypt(
                    csv, EcgUploadCrypto.parseRsaPublicKey(publicKeyPem), keyId);
            File out = writeCacheFile(context, "ecg-signal-encrypted.json",
                    EcgUploadCrypto.toJsonBytes(payload));
            if (out == null) return null;
            return new SignalPart(out, "ecg_" + measuredAt + ".json", JSON);
        } catch (Exception e) {
            Log.w(TAG, "signal encryption failed, falling back to plain CSV", e);
            return null;
        }
    }

    private static byte[] buildSignalCsvBytes() {
        float[] wave = LastEcgResult.lastRawSignal;
        if (wave == null || wave.length == 0) {
            wave = LastEcgResult.lastSpike;
        }
        if (wave == null || wave.length == 0) return null;
        StringBuilder sb = new StringBuilder(wave.length * 13);
        sb.append("index,value\n");
        for (int i = 0; i < wave.length; i++) {
            sb.append(i).append(',');
            appendMilli(sb, wave[i]);
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 기기는 12bit ADC 값을 0.003mV 단위로 올려주므로 모든 샘플이 소수 3자리로 정확히
     * 표현된다. Float.toString 의 긴 표현(-0.043361254)을 쓰면 정보량 없이 본문만 커진다.
     * String.format 은 샘플당 호출 비용이 커서 직접 조립하고, 로케일 소수점 문제도 피한다.
     */
    private static void appendMilli(StringBuilder sb, float mv) {
        int scaled = Math.round(mv * 1000f);
        if (scaled < 0) {
            sb.append('-');
            scaled = -scaled;
        }
        int frac = scaled % 1000;
        sb.append(scaled / 1000).append('.')
                .append((char) ('0' + frac / 100))
                .append((char) ('0' + frac / 10 % 10))
                .append((char) ('0' + frac % 10));
    }

    private static File writeCacheFile(Context context, String name, byte[] content) {
        try {
            File out = new File(context.getCacheDir(), name);
            FileOutputStream fos = new FileOutputStream(out, false);
            try {
                fos.write(content);
            } finally {
                fos.close();
            }
            return out;
        } catch (Exception e) {
            Log.w(TAG, "signal file write failed: " + name, e);
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

    private static final class LoggingCallback implements Callback<ApiModels.ApiResponse<JsonElement>> {
        private final String label;
        private final Context context;
        private final ResultCallback<Void> callback;

        LoggingCallback(Context context, String label) {
            this(context, label, null);
        }

        LoggingCallback(Context context, String label, ResultCallback<Void> callback) {
            this.label = label;
            this.context = context;
            this.callback = callback;
        }

        @Override
        public void onResponse(Call<ApiModels.ApiResponse<JsonElement>> call,
                               Response<ApiModels.ApiResponse<JsonElement>> response) {
            if (handleAuthFailure(context, response.code(), response)) {
                return;
            }
            ApiModels.ApiResponse<JsonElement> body = response.body();
            // HTTP 성공 + 본문 없음은 정상 처리 (서버가 빈 본문을 주는 경우가 있음)
            boolean failed = !response.isSuccessful() || (body != null && !body.isSuccess());
            if (!failed) {
                new SessionManager(context).setTodayDiaryDone(true);
                flushPendingPrediction(context);
                if (callback != null) callback.onSuccess(null);
                else refreshTodayAfterSave(context);
                return;
            }
            String fallback = label + " 전송에 실패했습니다.";
            String detail = body != null
                    ? apiErrorMessage(body, response.code(), fallback)
                    : httpErrorMessage(response, fallback);
            Log.w(TAG, label + " failed: http=" + response.code()
                    + " url=" + call.request().url()
                    + " detail=" + detail);
            if (callback != null) {
                callback.onError(detail);
                return;
            }
            if (context != null) {
                showToast(context, label + " 실패: " + detail);
            }
        }

        @Override
        public void onFailure(Call<ApiModels.ApiResponse<JsonElement>> call, Throwable t) {
            Log.w(TAG, label + " error", t);
            String msg = label + " 전송 중 오류가 발생했습니다.";
            if (callback != null) {
                callback.onError(msg);
                return;
            }
            if (context != null) {
                showToast(context, msg);
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
