package com.rinda.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.rinda.app.R;
import com.rinda.app.databinding.ActivityLanguageBinding;
import com.rinda.app.util.LocaleHelper;

public class LanguageActivity extends AppCompatActivity {

    private ActivityLanguageBinding binding;
    private String selectedLanguage = "en";
    private boolean isFromSettings = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLanguageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        isFromSettings = getIntent().getBooleanExtra("is_from_settings", false);
        selectedLanguage = LocaleHelper.getLanguage(this);

        updateSelectionUI(selectedLanguage);

        binding.cardEnglish.setOnClickListener(v -> {
            selectedLanguage = "en";
            updateSelectionUI("en");
        });

        binding.cardKinyarwanda.setOnClickListener(v -> {
            selectedLanguage = "rw";
            updateSelectionUI("rw");
        });

        binding.btnContinue.setOnClickListener(v -> {
            LocaleHelper.setLocale(this, selectedLanguage);

            if (isFromSettings) {
                finish();
            } else {
                startActivity(new Intent(this, OnboardingActivity.class));
                finish();
            }
        });
    }

    private void updateSelectionUI(String lang) {
        boolean isEn = "en".equalsIgnoreCase(lang);
        binding.rbEnglish.setChecked(isEn);
        binding.rbKinyarwanda.setChecked(!isEn);

        int activeBorder = ContextCompat.getColor(this, R.color.rinda_green);
        int inactiveBorder = ContextCompat.getColor(this, R.color.divider_light);

        binding.cardEnglish.setStrokeColor(isEn ? activeBorder : inactiveBorder);
        binding.cardEnglish.setStrokeWidth(isEn ? 4 : 2);

        binding.cardKinyarwanda.setStrokeColor(!isEn ? activeBorder : inactiveBorder);
        binding.cardKinyarwanda.setStrokeWidth(!isEn ? 4 : 2);
    }
}
