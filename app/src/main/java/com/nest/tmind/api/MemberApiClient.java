package com.nest.tmind.api;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nest.tmind.util.SessionManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class MemberApiClient {

    private static final String BASE_URL = "http://218.54.105.230:8080/";
    private static volatile MemberApiService cachedService;

    /** BASE_URL 변경 등 설정이 바뀐 경우 캐시를 초기화합니다. */
    public static void resetClient() {
        synchronized (MemberApiClient.class) {
            cachedService = null;
        }
    }

    private MemberApiClient() {
    }

    public static MemberApiService service(Context context) {
        if (cachedService == null) {
            synchronized (MemberApiClient.class) {
                if (cachedService == null) {
                    cachedService = buildRetrofit(context.getApplicationContext(), true)
                            .create(MemberApiService.class);
                }
            }
        }
        return cachedService;
    }

    private static Retrofit buildRetrofit(Context context, boolean withAuth) {
        Gson gson = new GsonBuilder().create();
        OkHttpClient.Builder client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // HEADERS: 요청 URL·쿼리·헤더까지 남김 (BODY는 ECG CSV 때문에 로그가 과도해짐)
        logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);
        client.addInterceptor(logging);

        if (withAuth) {
            client.addInterceptor(new AuthHeaderInterceptor(context));
            client.authenticator((Route route, Response response) -> refreshRequest(context, response));
        }

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    private static Request refreshRequest(Context context, Response response) {
        if (responseCount(response) >= 2) return null;
        SessionManager session = new SessionManager(context);
        String refreshToken = session.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) return null;
        try {
            MemberApiService plain = buildRetrofit(context, false).create(MemberApiService.class);
            retrofit2.Response<okhttp3.ResponseBody> refresh = plain
                    .refresh(new ApiModels.RefreshRequest(refreshToken))
                    .execute();
            ApiModels.TokenPair pair = MemberApiManager.parseTokenResponse(refresh);
            if (!refresh.isSuccessful() || pair == null) {
                session.clearTokens();
                return null;
            }
            session.setTokens(pair.accessToken, pair.refreshToken);
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + pair.accessToken)
                    .build();
        } catch (IOException e) {
            return null;
        }
    }

    private static int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }

    private static final class AuthHeaderInterceptor implements Interceptor {
        private final Context appContext;

        AuthHeaderInterceptor(Context appContext) {
            this.appContext = appContext.getApplicationContext();
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request req = chain.request();
            String path = req.url().encodedPath();
            if (path.endsWith("/auth/login") || path.endsWith("/auth/refresh")) {
                return chain.proceed(req);
            }
            String accessToken = new SessionManager(appContext).getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                return chain.proceed(req);
            }
            return chain.proceed(req.newBuilder()
                    .header("Authorization", "Bearer " + accessToken)
                    .build());
        }
    }
}
