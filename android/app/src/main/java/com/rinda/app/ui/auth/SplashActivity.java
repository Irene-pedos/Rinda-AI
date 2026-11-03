package com.rinda.app.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.OvershootInterpolator;
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

        // Logo grows in gently, then the text fades up.
        binding.brandGroup.setScaleX(0.85f);
        binding.brandGroup.setScaleY(0.85f);
        binding.brandGroup.setAlpha(0f);
        binding.brandGroup.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthAndNavigate, 900);
    }

    private void checkAuthAndNavigate() {
        TokenManager tokenManager = TokenManager.getInstance(this);

        if (tokenManager.isLoggedIn()) {
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
                        // Server issue: continue with the cached profile
                        goToHome();
                    }
                }

                @Override
                public void onFailure(Call<UserResponse> call, Throwable t) {
                    // Offline: continue with the cached profile; screens show the offline banner
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
