package com.rinda.app.data.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.rinda.app.data.model.RefreshRequest;
import com.rinda.app.data.model.TokenResponse;
import java.io.IOException;
import okhttp3.Authenticator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Handles automatic token refreshing on HTTP 401.
 * Strict synchronization prevents concurrent refresh attempts (each refresh token is single-use).
 */
public class TokenAuthenticator implements Authenticator {

    private static final Object REFRESH_LOCK = new Object();
    private final TokenManager tokenManager;
    private final String baseUrl;
    private final Gson gson = new Gson();

    public TokenAuthenticator(TokenManager tokenManager, String baseUrl) {
        this.tokenManager = tokenManager;
        this.baseUrl = baseUrl;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        // Do not attempt refresh on auth endpoints themselves
        String path = response.request().url().encodedPath();
        if (path.contains("/auth/")) {
            return null;
        }

        // Limit retries
        if (responseCount(response) >= 3) {
            return null;
        }

        String originalRequestToken = extractBearerToken(response.request());

        synchronized (REFRESH_LOCK) {
            String currentAccessToken = tokenManager.getAccessToken();

            // If another thread already refreshed the token while we waited, retry immediately
            if (currentAccessToken != null && !currentAccessToken.equals(originalRequestToken)) {
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + currentAccessToken)
                        .build();
            }

            String refreshToken = tokenManager.getRefreshToken();
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                tokenManager.clear();
                return null;
            }

            // Perform single synchronous refresh call
            OkHttpClient directClient = new OkHttpClient.Builder().build();
            String refreshUrl = baseUrl.replaceAll("/+$", "") + "/auth/refresh";
            String jsonBody = gson.toJson(new RefreshRequest(refreshToken));

            Request refreshRequest = new Request.Builder()
                    .url(refreshUrl)
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8")))
                    .build();

            try (Response refreshResponse = directClient.newCall(refreshRequest).execute()) {
                if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
                    String responseString = refreshResponse.body().string();
                    TokenResponse tokenResponse = gson.fromJson(responseString, TokenResponse.class);
                    tokenManager.saveTokens(tokenResponse);

                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + tokenResponse.getAccessToken())
                            .build();
                } else {
                    // Token invalid or revoked - clear tokens to force user login
                    tokenManager.clear();
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }

    private String extractBearerToken(Request request) {
        String authHeader = request.header("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
