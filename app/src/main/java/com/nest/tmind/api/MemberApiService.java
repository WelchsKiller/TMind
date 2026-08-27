package com.nest.tmind.api;

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

    @POST("/api/member/session")
    Call<ApiModels.ApiResponse<ApiModels.StartSessionData>> startSession(
            @Body ApiModels.StartSessionRequest request);

    @Multipart
    @POST("/api/member/session/{sessionId}/hrv")
    Call<ApiModels.ApiResponse<String>> uploadHrv(
            @Path("sessionId") long sessionId,
            @Part("data") RequestBody data,
            @Part MultipartBody.Part signal);

    @POST("/api/member/session/{sessionId}/hrv/skip")
    Call<ApiModels.ApiResponse<String>> skipHrv(
            @Path("sessionId") long sessionId,
            @Body ApiModels.SkipHrvRequest request);

    @POST("/api/member/session/{sessionId}/prediction")
    Call<ApiModels.ApiResponse<String>> savePrediction(
            @Path("sessionId") long sessionId,
            @Body ApiModels.SavePredictionRequest request);

    @POST("/api/member/session/{sessionId}/feedback")
    Call<ApiModels.ApiResponse<String>> submitFeedback(
            @Path("sessionId") long sessionId,
            @Body ApiModels.FeedbackRequest request);

    @GET("/api/member/session/{sessionId}/ema/questions")
    Call<ApiModels.ApiResponse<java.util.List<ApiModels.QuestionResponse>>> getEmaQuestions(
            @Path("sessionId") long sessionId);

    @POST("/api/member/session/{sessionId}/ema")
    Call<ApiModels.ApiResponse<String>> submitEma(
            @Path("sessionId") long sessionId,
            @Body ApiModels.SubmitEmaListRequest request);

    @Multipart
    @POST("/api/member/session/{sessionId}/voice-diary")
    Call<ApiModels.ApiResponse<String>> uploadVoiceDiary(
            @Path("sessionId") long sessionId,
            @Query("durationSec") int durationSec,
            @Part("recordedAt") RequestBody recordedAt,
            @Part MultipartBody.Part audio);

    @PUT("/api/member/fcm-token")
    Call<ResponseBody> putFcmToken(@Body ApiModels.FcmTokenRequest request);

    @DELETE("/api/member/fcm-token")
    Call<ResponseBody> deleteFcmToken();
}
