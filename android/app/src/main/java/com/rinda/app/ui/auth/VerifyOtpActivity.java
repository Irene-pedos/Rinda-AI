package com.rinda.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.ApiErrorParser;
import com.rinda.app.data.api.TokenManager;
import com.rinda.app.data.model.ApiProblem;
import com.rinda.app.data.model.OtpRequest;
import com.rinda.app.data.model.OtpVerifyRequest;
import com.rinda.app.data.model.TokenResponse;
import com.rinda.app.databinding.ActivityVerifyOtpBinding;
import com.rinda.app.ui.home.HomeActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VerifyOtpActivity extends AppCompatActivity {

    public static final String EXTRA_CODE = "extra_code";
    private ActivityVerifyOtpBinding binding;
    private String phone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVerifyOtpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        phone = getIntent().getStringExtra(LoginActivity.EXTRA_PHONE);
        if (phone == null || phone.isEmpty()) {
            finish();
            return;
        }

        binding.tvSubtitle.setText(getString(R.string.otp_subtitle, phone));

        binding.btnVerify.setOnClickListener(v -> verifyOtp());
        binding.btnResend.setOnClickListener(v -> resendOtp());
    }

    private void verifyOtp() {
        binding.tilCode.setError(null);
        String code = binding.etCode.getText() != null ? binding.etCode.getText().toString().trim() : "";

        if (code.length() != 6) {
            binding.tilCode.setError(getString(R.string.error_otp_invalid));
            return;
        }

        setLoading(true);

        OtpVerifyRequest request = new OtpVerifyRequest(phone, code);
        ApiClient.getInstance(this).getService().verifyOtp(request)
                .enqueue(new Callback<TokenResponse>() {
                    @Override
                    public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            // Existing user successfully logged in
                            TokenManager.getInstance(VerifyOtpActivity.this).saveTokens(response.body());
                            goToHome();
                        } else if (response.code() == 409) {
                            // 409 SIGNUP_DETAILS_REQUIRED: New phone number!
                            Intent intent = new Intent(VerifyOtpActivity.this, SignupActivity.class);
                            intent.putExtra(LoginActivity.EXTRA_PHONE, phone);
                            intent.putExtra(EXTRA_CODE, code);
                            startActivity(intent);
                            finish();
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            binding.tilCode.setError(ApiErrorParser.getFriendlyMessage(VerifyOtpActivity.this, problem));
                        }
                    }

                    @Override
                    public void onFailure(Call<TokenResponse> call, Throwable t) {
                        setLoading(false);
                        binding.tilCode.setError(getString(R.string.error_network));
                    }
                });
    }

    private void resendOtp() {
        setLoading(true);
        ApiClient.getInstance(this).getService().requestOtp(new OtpRequest(phone))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        setLoading(false);
                        if (response.isSuccessful() || response.code() == 202) {
                            Toast.makeText(VerifyOtpActivity.this, "New code sent! Check backend terminal", Toast.LENGTH_SHORT).show();
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            Toast.makeText(VerifyOtpActivity.this, ApiErrorParser.getFriendlyMessage(VerifyOtpActivity.this, problem), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(VerifyOtpActivity.this, getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnVerify.setEnabled(!loading);
    }
}
