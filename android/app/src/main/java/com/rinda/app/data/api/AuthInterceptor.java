package com.rinda.app.data.api;

import androidx.annotation.NonNull;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final TokenManager tokenManager;

    public AuthInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String path = original.url().encodedPath();

        // Skip auth header on public authentication endpoints
        if (path.contains("/auth/otp/") || path.contains("/auth/refresh") || path.contains("/auth/cooperatives")) {
            return chain.proceed(original);
        }

        String token = tokenManager.getAccessToken();
        if (token != null && !token.trim().isEmpty()) {
            Request authenticated = original.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build();
            return chain.proceed(authenticated);
        }

        return chain.proceed(original);
    }
}
