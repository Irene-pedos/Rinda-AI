package com.rinda.app.data.api;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rinda.app.BuildConfig;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static volatile ApiClient INSTANCE;
    private final RindaApiService service;
    private final String activeBaseUrl;

    private ApiClient(Context context) {
        TokenManager tokenManager = TokenManager.getInstance(context);

        String customUrl = tokenManager.getCustomBaseUrl();
        if (customUrl != null && !customUrl.trim().isEmpty()) {
            this.activeBaseUrl = customUrl.endsWith("/") ? customUrl : customUrl + "/";
        } else {
            this.activeBaseUrl = BuildConfig.DEFAULT_BASE_URL;
        }

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor(tokenManager))
                .authenticator(new TokenAuthenticator(tokenManager, this.activeBaseUrl))
                .addInterceptor(loggingInterceptor)
                .build();

        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(this.activeBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        this.service = retrofit.create(RindaApiService.class);
    }

    public static ApiClient getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ApiClient.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ApiClient(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public static void resetInstance() {
        synchronized (ApiClient.class) {
            INSTANCE = null;
        }
    }

    public RindaApiService getService() {
        return service;
    }

    public String getActiveBaseUrl() {
        return activeBaseUrl;
    }
}
