package com.nest.tmind.api;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.util.SessionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType TEXT_PLAIN = MediaType.parse("text/plain");

    private MemberApiManager() {
    }

    public interface ResultCallback<T> {
        void onSuccess(T data);

        void onError(String message);
    }

    public static void loginAndConsent(Context context, String code, ResultCallback<ApiModels.TokenPair> callback) {
        MemberApiClient.service(context).login(new ApiModels.MemberLoginRequest(code))
                .enqueue(new Callback<ApiModels.ApiResponse<ApiModels.TokenPair>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<ApiModels.TokenPair>> call,
                                           Response<ApiModels.ApiResponse<ApiModels.TokenPair>> response) {
                        ApiModels.ApiResponse<ApiModels.TokenPair> body = response.body();
                        ApiModels.TokenPair pair = unwrapToken(body);
                        if (!response.isSuccessful() || pair == null
                                || pair.accessToken == null || pair.accessToken.isEmpty()) {
                            callback.onError(errorMessage(body, response.code(), "로그인에 실패했습니다."));
                            return;
                        }
                        SessionManager session = new SessionManager(context);
                        session.setTokens(pair.accessToken, pair.refreshToken);
                        MemberApiClient.service(context).consent()
                                .enqueue(new Callback<ApiModels.ApiResponse<ApiModels.TokenPair>>() {
                                    @Override
                                    public void onResponse(Call<ApiModels.ApiResponse<ApiModels.TokenPair>> call2,
                                                           Response<ApiModels.ApiResponse<ApiModels.TokenPair>> response2) {
                                        ApiModels.TokenPair consented = unwrapToken(response2.body());
                                        if (response2.isSuccessful() && consented != null
                                                && consented.accessToken != null
                                                && !consented.accessToken.isEmpty()) {
                                            session.setTokens(consented.accessToken, consented.refreshToken);
                                            callback.onSuccess(consented);
                                            return;
                                        }
                                        // 동의 실패해도 로그인 토큰으로 진행 가능
                                        callback.onSuccess(pair);
                                    }

                                    @Override
                                    public void onFailure(Call<ApiModels.ApiResponse<ApiModels.TokenPair>> call2,
                                                          Throwable t) {
                                        callback.onSuccess(pair);
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<ApiModels.TokenPair>> call, Throwable t) {
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
                ApiModels.ApiResponse<ApiModels.TodayResponse> body = response.body();
                if (!response.isSuccessful() || body == null || !body.isSuccess()) {
                    callback.onError(errorMessage(body, response.code(), "오늘 세션 정보를 불러오지 못했습니다."));
                    return;
                }
                SessionManager session = new SessionManager(context);
                session.setRemoteEventActive(body.data.eventActive);
                if (body.data.currentSessionId != null && body.data.currentSessionId > 0) {
                    session.setCurrentSessionId(false, body.data.currentSessionId);
                }
                session.setRemoteHrvStatus(body.data.hrvStatus);
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

    public static void ensureSessionStarted(Context context, boolean event, ResultCallback<Long> callback) {
        SessionManager session = new SessionManager(context);
        if (session.getAccessToken() == null || session.getAccessToken().isEmpty()) {
            callback.onError("로그인이 필요합니다. 다시 로그인해 주세요.");
            return;
        }
        long cached = session.getCurrentSessionId(event);
        if (cached > 0) {
            callback.onSuccess(cached);
            return;
        }
        long startedAt = System.currentTimeMillis();
        MemberApiClient.service(context).startSession(new ApiModels.StartSessionRequest(event, startedAt))
                .enqueue(new Callback<ApiModels.ApiResponse<ApiModels.StartSessionData>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<ApiModels.StartSessionData>> call,
                                           Response<ApiModels.ApiResponse<ApiModels.StartSessionData>> response) {
                        ApiModels.ApiResponse<ApiModels.StartSessionData> body = response.body();
                        if (response.code() == 401) {
                            session.clearTokens();
                            callback.onError("인증이 만료되었습니다. 다시 로그인해 주세요.");
                            return;
                        }
                        if (!response.isSuccessful() || body == null || !body.isSuccess()
                                || body.data.sessionId <= 0) {
                            callback.onError(errorMessage(body, response.code(), "세션을 시작하지 못했습니다."));
                            return;
                        }
                        session.setCurrentSessionId(event, body.data.sessionId);
                        if (body.data.hrvStatus != null) {
                            session.setRemoteHrvStatus(body.data.hrvStatus);
                        }
                        callback.onSuccess(body.data.sessionId);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<ApiModels.StartSessionData>> call, Throwable t) {
                        callback.onError("세션을 시작하지 못했습니다. 네트워크를 확인해 주세요.");
                    }
                });
    }

    /** 유효 측정만 호출. measurementValid 는 항상 true. */
    public static void uploadHrv(Context context, boolean event, long measuredAtMs) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) return;
        File signalFile = buildSignalFile(context);
        if (signalFile == null) {
            showToast(context, "ECG 신호 파일이 없어 HRV를 전송하지 못했습니다.");
            return;
        }
        long at = measuredAtMs > 0 ? measuredAtMs : System.currentTimeMillis();
        int fs = LastEcgResult.lastFs > 0 ? LastEcgResult.lastFs : 250;
        ApiModels.HrvUploadData data = new ApiModels.HrvUploadData(
                at,
                true,
                LastEcgResult.lastHrBpm,
                LastEcgResult.lastHrvMs,
                LastEcgResult.lastRrMs,
                LastEcgResult.lastStressScore,
                fs
        );
        RequestBody dataPart = RequestBody.create(GSON.toJson(data), JSON);
        RequestBody fileBody = RequestBody.create(signalFile, MediaType.parse("text/csv"));
        MultipartBody.Part signal = MultipartBody.Part.createFormData("signal", signalFile.getName(), fileBody);
        MemberApiClient.service(context).uploadHrv(sessionId, dataPart, signal)
                .enqueue(new LoggingCallback(context, "HRV 업로드"));
        new SessionManager(context).setRemoteHrvStatus("VALID");
    }

    public static void skipHrv(Context context, boolean event, String reason) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) return;
        MemberApiClient.service(context).skipHrv(sessionId, new ApiModels.SkipHrvRequest(reason))
                .enqueue(new LoggingCallback(context, "HRV 건너뛰기"));
        new SessionManager(context).setRemoteHrvStatus("SKIPPED");
    }

    public static void savePrediction(Context context, boolean event,
                                      float valence, float arousal, String text) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) return;
        MemberApiClient.service(context).savePrediction(sessionId,
                        new ApiModels.SavePredictionRequest(valence, arousal, text))
                .enqueue(new LoggingCallback(context, "예측 저장"));
    }

    /**
     * MATCH/UNKNOWN: 정정 좌표·사유 없이 전송.
     * MISMATCH: correctedValence/Arousal 필수, reasonCode 선택.
     */
    public static void submitFeedback(Context context, boolean event, String choice,
                                      Float correctedValence, Float correctedArousal) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) return;
        String match = matchResult(choice);
        String reason = null;
        Float cv = null;
        Float ca = null;
        if ("MISMATCH".equals(match)) {
            if (correctedValence == null || correctedArousal == null) {
                showToast(context, "다른 감정 위치를 선택한 뒤 다시 제출해 주세요.");
                return;
            }
            cv = correctedValence;
            ca = correctedArousal;
            reason = "MANUAL_EDIT";
        }
        MemberApiClient.service(context).submitFeedback(sessionId,
                        new ApiModels.FeedbackRequest(match, reason, cv, ca, System.currentTimeMillis()))
                .enqueue(new LoggingCallback(context, "피드백 제출"));
    }

    public static void fetchEmaQuestions(Context context, boolean event,
                                         ResultCallback<List<ApiModels.QuestionResponse>> callback) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) {
            callback.onError("세션이 없습니다.");
            return;
        }
        MemberApiClient.service(context).getEmaQuestions(sessionId)
                .enqueue(new Callback<ApiModels.ApiResponse<List<ApiModels.QuestionResponse>>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<List<ApiModels.QuestionResponse>>> call,
                                           Response<ApiModels.ApiResponse<List<ApiModels.QuestionResponse>>> response) {
                        ApiModels.ApiResponse<List<ApiModels.QuestionResponse>> body = response.body();
                        if (!response.isSuccessful() || body == null || !body.isSuccess()) {
                            callback.onError(errorMessage(body, response.code(), "문항을 불러오지 못했습니다."));
                            return;
                        }
                        callback.onSuccess(body.data);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<List<ApiModels.QuestionResponse>>> call,
                                          Throwable t) {
                        callback.onError("문항을 불러오지 못했습니다.");
                    }
                });
    }

    public static void submitEma(Context context, boolean event,
                                 List<ApiModels.SubmitEmaRequest> responses,
                                 float valence, float arousal) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0 || responses == null || responses.isEmpty()) return;
        MemberApiClient.service(context).submitEma(sessionId,
                        new ApiModels.SubmitEmaListRequest(
                                responses, valence, arousal, System.currentTimeMillis()))
                .enqueue(new LoggingCallback(context, "설문 제출"));
    }

    public static void uploadVoiceDiary(Context context, boolean event, File audioFile,
                                        int durationSec, long recordedAtMs) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0 || audioFile == null || !audioFile.exists()) return;
        long at = recordedAtMs > 0 ? recordedAtMs : System.currentTimeMillis();
        RequestBody recordedAt = RequestBody.create(String.valueOf(at), TEXT_PLAIN);
        RequestBody body = RequestBody.create(audioFile, MediaType.parse("audio/*"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("audio", audioFile.getName(), body);
        MemberApiClient.service(context).uploadVoiceDiary(sessionId, durationSec, recordedAt, part)
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

    public static String predictionText(float valence, float arousal) {
        if (valence >= 0.2f && arousal >= 0.2f) return "기분이 좋고 활력이 느껴집니다.";
        if (valence >= 0.2f) return "기분이 안정되고 편안한 상태로 보입니다.";
        if (arousal >= 0.2f) return "긴장되거나 예민한 상태로 보입니다.";
        return "기운이 떨어지거나 가라앉은 상태로 보입니다.";
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

    private static ApiModels.TokenPair unwrapToken(ApiModels.ApiResponse<ApiModels.TokenPair> body) {
        if (body == null) return null;
        if (body.isSuccess()) return body.data;
        // 일부 환경에서 code 없이 data만 오는 경우 대비
        if (body.data != null && body.data.accessToken != null && !body.data.accessToken.isEmpty()) {
            return body.data;
        }
        return null;
    }

    private static String matchResult(String choice) {
        if ("agree".equalsIgnoreCase(choice)) return "MATCH";
        if ("unknown".equalsIgnoreCase(choice)) return "UNKNOWN";
        return "MISMATCH";
    }

    private static String errorMessage(ApiModels.ApiResponse<?> body, int httpCode, String fallback) {
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
        if (httpCode > 0) return fallback + " (HTTP " + httpCode + ")";
        return fallback;
    }

    private static File buildSignalFile(Context context) {
        try {
            float[] wave = LastEcgResult.lastSpike;
            if (wave == null || wave.length == 0) return null;
            File out = new File(context.getCacheDir(), "last-hrv-signal.csv");
            FileOutputStream fos = new FileOutputStream(out, false);
            StringBuilder sb = new StringBuilder();
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
            if (questionIds[i] > 0 && answers[i] >= 1 && answers[i] <= 5) {
                out.add(new ApiModels.SubmitEmaRequest(questionIds[i], answers[i]));
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
            if (!response.isSuccessful()) {
                Log.w(TAG, label + " failed: " + response.code());
                if (context != null) {
                    showToast(context, label + " 전송에 실패했습니다. (코드: " + response.code() + ")");
                }
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
