package com.rinda.app.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.rinda.app.R;
import com.rinda.app.databinding.ActivityConsentBinding;
import com.rinda.app.databinding.ViewIconPointBinding;

public class ConsentActivity extends AppCompatActivity {

    /** Opened from Profile to re-read the consent; then the button just closes the screen. */
    public static final String EXTRA_READ_ONLY = "extra_read_only";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityConsentBinding binding = ActivityConsentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        bindPoint(binding.point1, R.drawable.ic_map_pin, R.string.consent_point_1_title, R.string.consent_point_1_body);
        bindPoint(binding.point2, R.drawable.ic_nav_profile, R.string.consent_point_2_title, R.string.consent_point_2_body);
        bindPoint(binding.point3, R.drawable.ic_lock, R.string.consent_point_3_title, R.string.consent_point_3_body);
        bindPoint(binding.point4, R.drawable.ic_info, R.string.consent_point_4_title, R.string.consent_point_4_body);

        boolean readOnly = getIntent().getBooleanExtra(EXTRA_READ_ONLY, false);
        if (readOnly) {
            binding.cbAgree.setChecked(true);
            binding.cbAgree.setEnabled(false);
            binding.btnAgree.setEnabled(true);
            binding.btnAgree.setText(R.string.btn_close);
            binding.btnAgree.setOnClickListener(v -> finish());
            return;
        }

        // Consent must be an explicit choice, so the button waits for the checkbox.
        binding.cbAgree.setOnCheckedChangeListener((button, checked) -> binding.btnAgree.setEnabled(checked));
        binding.btnAgree.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("rinda_prefs", MODE_PRIVATE);
            prefs.edit()
                    .putBoolean(SplashActivity.PREF_CONSENT, true)
                    .putLong(SplashActivity.PREF_CONSENT + "_at", System.currentTimeMillis())
                    .apply();

            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void bindPoint(ViewIconPointBinding point, int iconRes, int titleRes, int bodyRes) {
        point.ivPointIcon.setImageResource(iconRes);
        point.tvPointTitle.setText(titleRes);
        point.tvPointBody.setText(bodyRes);
    }
}
