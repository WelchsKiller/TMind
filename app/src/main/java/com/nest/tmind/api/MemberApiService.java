package com.nest.tmind.api;

import com.google.gson.JsonElement;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 응답의 data 를 쓰지 않는 API 는 JsonElement 로 받는다. 서버가 data 를 문자열에서
 * 객체로 바꿔도 파싱이 깨지지 않는다(예: EMA 제출이 200 인데 파싱 실패로 처리되던 문제).
 */
public interface MemberApiService {

    @POST("/api/member/auth/login")
    Call<ResponseBody> login(@Body ApiModels.MemberLoginRequest request);

    @POST("/api/member/auth/refresh")
    Call<ResponseBody> refresh(@Body ApiModels.RefreshRequest request);

    @POST("/api/member/auth/logout")
    Call<ResponseBody> logout(@Body ApiModels.RefreshRequest request);

    @POST("/api/member/consent")
    Call<ResponseBody> consent();

    @GET("/api/member/today")
    Call<ApiModels.ApiResponse<ApiModels.TodayResponse>> getToday();

    @GET("/api/member/participation")
    Call<ApiModels.ApiResponse<ApiModels.ParticipationResponse>> getParticipation();

    @GET("/api/member/crypto/public-key")
    Call<ApiModels.ApiResponse<ApiModels.PublicKeyResponse>> getPublicKey();

    @POST("/api/member/session")
    Call<ApiModels.ApiResponse<ApiModels.StartSessionData>> startSession(
            @Body ApiModels.StartSessionRequest request);

    /** data(HRV 지표 JSON) + signal(ECG 원본) 두 파트를 한 요청으로 보낸다. */
    @Multipart
    @POST("/api/member/session/{sessionId}/hrv")
    Call<ApiModels.ApiResponse<JsonElement>> uploadHrv(
            @Path("sessionId") long sessionId,
            @Part MultipartBody.Part data,
            @Part MultipartBody.Part signal);

    @POST("/api/member/session/{sessionId}/prediction")
    Call<ApiModels.ApiResponse<JsonElement>> savePrediction(
            @Path("sessionId") long sessionId,
            @Body ApiModels.SavePredictionRequest request);

    @POST("/api/member/session/{sessionId}/feedback")
    Call<ApiModels.ApiResponse<JsonElement>> submitFeedback(
            @Path("sessionId") long sessionId,
            @Body ApiModels.FeedbackRequest request);

    @GET("/api/member/session/{sessionId}/ema/questions")
    Call<ApiModels.ApiResponse<java.util.List<ApiModels.QuestionResponse>>> getEmaQuestions(
            @Path("sessionId") long sessionId);

    @POST("/api/member/session/{sessionId}/ema")
    Call<ApiModels.ApiResponse<JsonElement>> submitEma(
            @Path("sessionId") long sessionId,
            @Body ApiModels.SubmitEmaListRequest request);

    /** durationSec(0 초과, 200 이하)·recordedAt(epoch ms) 은 query, audio 만 multipart */
    @Multipart
    @POST("/api/member/session/{sessionId}/voice-diary")
    Call<ApiModels.ApiResponse<JsonElement>> uploadVoiceDiary(
            @Path("sessionId") long sessionId,
            @Query("durationSec") int durationSec,
            @Query("recordedAt") long recordedAt,
            @Part MultipartBody.Part audio);

    @PUT("/api/member/fcm-token")
    Call<ResponseBody> putFcmToken(@Body ApiModels.FcmTokenRequest request);

    @DELETE("/api/member/fcm-token")
    Call<ResponseBody> deleteFcmToken();
}
