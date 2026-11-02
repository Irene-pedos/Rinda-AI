package com.rinda.app.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.TokenManager;
import com.rinda.app.data.model.UserResponse;
import com.rinda.app.databinding.ActivitySplashBinding;
import com.rinda.app.ui.home.HomeActivity;
import com.rinda.app.util.LocaleHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashActivity extends AppCompatActivity {

    public static final String PREF_CONSENT = "rinda_consent_accepted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apply saved language preference
        LocaleHelper.setLocale(this, LocaleHelper.getLanguage(this));

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthAndNavigate, 800);
    }

    private void checkAuthAndNavigate() {
        TokenManager tokenManager = TokenManager.getInstance(this);

        if (tokenManager.isLoggedIn()) {
            // Verify session or update profile
            ApiClient.getInstance(this).getService().getMe().enqueue(new Callback<UserResponse>() {
                @Override
                public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        tokenManager.saveUser(response.body());
                        goToHome();
                    } else if (response.code() == 401) {
                        tokenManager.clear();
                        goToLoginOrLanguage();
                    } else {
                        // Offline or server issue: proceed with cached user profile
                        goToHome();
                    }
                }

                @Override
                public void onFailure(Call<UserResponse> call, Throwable t) {
                    // Offline fallback: server data wins, but allow offline access
                    goToHome();
                }
            });
        } else {
            goToLoginOrLanguage();
        }
    }

    private void goToHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void goToLoginOrLanguage() {
        SharedPreferences prefs = getSharedPreferences("rinda_prefs", MODE_PRIVATE);
        boolean consentAccepted = prefs.getBoolean(PREF_CONSENT, false);

        if (consentAccepted) {
            startActivity(new Intent(this, LoginActivity.class));
        } else {
            startActivity(new Intent(this, LanguageActivity.class));
        }
        finish();
    }
}
