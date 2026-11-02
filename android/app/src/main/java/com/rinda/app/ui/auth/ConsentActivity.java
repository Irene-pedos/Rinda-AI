package com.rinda.app.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.rinda.app.databinding.ActivityConsentBinding;

public class ConsentActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityConsentBinding binding = ActivityConsentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnAgree.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("rinda_prefs", MODE_PRIVATE);
            prefs.edit().putBoolean(SplashActivity.PREF_CONSENT, true).apply();

            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}
