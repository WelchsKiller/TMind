package com.nest.tmind.api;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.util.MissionManager;
import com.nest.tmind.util.SessionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

    private MemberApiManager() {
    }

    public interface ResultCallback<T> {
        void onSuccess(T data);

        void onError(String message);
    }

    public static void loginAndConsent(Context context, String code, ResultCallback<ApiModels.TokenPair> callback) {
        MemberApiClient.service(context).login(new ApiModels.MemberLoginRequest(code))
                .enqueue(new Callback<ApiModels.TokenPair>() {
                    @Override
                    public void onResponse(Call<ApiModels.TokenPair> call, Response<ApiModels.TokenPair> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            callback.onError("로그인에 실패했습니다.");
                            return;
                        }
                        SessionManager session = new SessionManager(context);
                        ApiModels.TokenPair pair = response.body();
                        session.setTokens(pair.accessToken, pair.refreshToken);
                        MemberApiClient.service(context).consent().enqueue(new Callback<ApiModels.TokenPair>() {
                            @Override
                            public void onResponse(Call<ApiModels.TokenPair> call2,
                                                   Response<ApiModels.TokenPair> response2) {
                                if (response2.isSuccessful() && response2.body() != null) {
                                    ApiModels.TokenPair consented = response2.body();
                                    session.setTokens(consented.accessToken, consented.refreshToken);
                                    callback.onSuccess(consented);
                                    return;
                                }
                                callback.onSuccess(pair);
                            }

                            @Override
                            public void onFailure(Call<ApiModels.TokenPair> call2, Throwable t) {
                                callback.onSuccess(pair);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<ApiModels.TokenPair> call, Throwable t) {
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
                if (!response.isSuccessful() || body == null || body.data == null) {
                    callback.onError("오늘 세션 정보를 불러오지 못했습니다.");
                    return;
                }
                new SessionManager(context).setRemoteEventActive(body.data.eventActive);
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
                        if (!response.isSuccessful() || body == null || body.data == null) {
                            callback.onError("참여 현황을 불러오지 못했습니다.");
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
        long cached = session.getCurrentSessionId(event);
        if (cached > 0) {
            callback.onSuccess(cached);
            return;
        }
        MemberApiClient.service(context).startSession(new ApiModels.StartSessionRequest(event))
                .enqueue(new Callback<ApiModels.ApiResponse<ApiModels.StartSessionData>>() {
                    @Override
                    public void onResponse(Call<ApiModels.ApiResponse<ApiModels.StartSessionData>> call,
                                           Response<ApiModels.ApiResponse<ApiModels.StartSessionData>> response) {
                        ApiModels.ApiResponse<ApiModels.StartSessionData> body = response.body();
                        if (!response.isSuccessful() || body == null || body.data == null || body.data.sessionId <= 0) {
                            callback.onError("세션을 시작하지 못했습니다.");
                            return;
                        }
                        session.setCurrentSessionId(event, body.data.sessionId);
                        callback.onSuccess(body.data.sessionId);
                    }

                    @Override
                    public void onFailure(Call<ApiModels.ApiResponse<ApiModels.StartSessionData>> call, Throwable t) {
                        callback.onError("세션을 시작하지 못했습니다.");
                    }
                });
    }

    public static void uploadHrv(Context context, boolean event, long measuredAtMs) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) return;
        File signalFile = buildSignalFile(context);
        if (signalFile == null) return;
        RequestBody body = RequestBody.create(signalFile, MediaType.parse("text/csv"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("signal", signalFile.getName(), body);
        String measuredAt = toIsoDateTime(measuredAtMs);
        MemberApiClient.service(context).uploadHrv(sessionId, measuredAt, part)
                .enqueue(new LoggingCallback(context, "HRV 업로드"));
    }

    public static void skipHrv(Context context, boolean event, String reason) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) return;
        MemberApiClient.service(context).skipHrv(sessionId, new ApiModels.SkipHrvRequest(reason))
                .enqueue(new LoggingCallback("skipHrv"));
    }

    public static void savePrediction(Context context, boolean event,
                                      float valence, float arousal, String text) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) return;
        MemberApiClient.service(context).savePrediction(sessionId,
                        new ApiModels.SavePredictionRequest(valence, arousal, text))
                .enqueue(new LoggingCallback(context, "예측 저장"));
    }

    public static void submitFeedback(Context context, boolean event, String choice, String reasonCode) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0) return;
        MemberApiClient.service(context).submitFeedback(sessionId,
                        new ApiModels.FeedbackRequest(matchResult(choice), reasonCode))
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
                        if (!response.isSuccessful() || body == null || body.data == null) {
                            callback.onError("문항을 불러오지 못했습니다.");
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
                                 List<ApiModels.SubmitEmaRequest> responses) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0 || responses == null || responses.isEmpty()) return;
        MemberApiClient.service(context).submitEma(sessionId, new ApiModels.SubmitEmaListRequest(responses))
                .enqueue(new LoggingCallback(context, "설문 제출"));
    }

    public static void uploadVoiceDiary(Context context, boolean event, File audioFile, int durationSec) {
        long sessionId = new SessionManager(context).getCurrentSessionId(event);
        if (sessionId <= 0 || audioFile == null || !audioFile.exists()) return;
        RequestBody body = RequestBody.create(audioFile, MediaType.parse("audio/*"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("audio", audioFile.getName(), body);
        MemberApiClient.service(context).uploadVoiceDiary(sessionId, durationSec, part)
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

    /**
     * UI 스레드에 관계없이 안전하게 오류 다이얼로그를 표시합니다.
     * 흐름이 막히는 치명적 오류(세션 시작 실패, 문항 로딩 실패 등)에 사용하세요.
     */
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

    /**
     * 백그라운드 전송 실패처럼 흐름을 막지 않는 오류에 사용하는 가벼운 Toast 알림입니다.
     */
    public static void showToast(Context context, String message) {
        new android.os.Handler(Looper.getMainLooper()).post(
                () -> Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        );
    }

    private static String matchResult(String choice) {
        if ("agree".equalsIgnoreCase(choice)) return "MATCH";
        if ("unknown".equalsIgnoreCase(choice)) return "UNKNOWN";
        return "MISMATCH";
    }

    private static String toIsoDateTime(long timeMs) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA).format(new Date(timeMs));
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

        LoggingCallback(String label) {
            this.label = label;
            this.context = null;
        }

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
