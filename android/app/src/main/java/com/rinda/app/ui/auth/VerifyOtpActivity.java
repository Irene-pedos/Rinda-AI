package com.rinda.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
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
import com.rinda.app.ui.common.Ui;
import com.rinda.app.ui.home.HomeActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VerifyOtpActivity extends AppCompatActivity {

    public static final String EXTRA_CODE = "extra_code";
    /** Matches the backend's resend limit (error_otp_rate_limited). */
    private static final long RESEND_WAIT_MS = 60_000;

    private ActivityVerifyOtpBinding binding;
    private String phone;
    private CountDownTimer resendTimer;
    private boolean verifying = false;

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

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.tvSubtitle.setText(getString(R.string.otp_subtitle, phone));

        // The button is enabled at 6 digits, and the code is sent as soon as it is complete.
        binding.etCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.tilCode.setError(null);
                boolean complete = s.length() == 6;
                binding.btnVerify.setEnabled(complete && !verifying);
                if (complete && !verifying) {
                    verifyOtp();
                }
            }
        });

        binding.btnVerify.setOnClickListener(v -> verifyOtp());
        binding.btnResend.setOnClickListener(v -> resendOtp());
        binding.btnChangeNumber.setOnClickListener(v -> finish());
        binding.etCode.requestFocus();
        startResendCountdown();
    }

    private void startResendCountdown() {
        binding.btnResend.setEnabled(false);
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        resendTimer = new CountDownTimer(RESEND_WAIT_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                binding.btnResend.setText(getString(R.string.resend_code_in, (int) (millisUntilFinished / 1000) + 1));
            }

            @Override
            public void onFinish() {
                binding.btnResend.setText(R.string.resend_code);
                binding.btnResend.setEnabled(true);
            }
        }.start();
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
                            TokenManager.getInstance(VerifyOtpActivity.this).saveTokens(response.body());
                            goToHome();
                        } else if (response.code() == 409) {
                            // SIGNUP_DETAILS_REQUIRED: this phone number is new
                            Intent intent = new Intent(VerifyOtpActivity.this, SignupActivity.class);
                            intent.putExtra(LoginActivity.EXTRA_PHONE, phone);
                            intent.putExtra(EXTRA_CODE, code);
                            startActivity(intent);
                            finish();
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            binding.tilCode.setError(ApiErrorParser.getFriendlyMessage(VerifyOtpActivity.this, problem));
                            binding.etCode.selectAll();
                        }
                    }

                    @Override
                    public void onFailure(Call<TokenResponse> call, Throwable t) {
                        setLoading(false);
                        binding.tilCode.setError(getString(R.string.error_network_action));
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
                            Ui.toast(binding.getRoot(), R.string.new_code_sent);
                            startResendCountdown();
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            Ui.toast(binding.getRoot(), ApiErrorParser.getFriendlyMessage(VerifyOtpActivity.this, problem));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        setLoading(false);
                        Ui.error(binding.getRoot(), getString(R.string.error_network_action), VerifyOtpActivity.this::resendOtp);
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
        verifying = loading;
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        Editable code = binding.etCode.getText();
        binding.btnVerify.setEnabled(!loading && code != null && code.length() == 6);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) {
            resendTimer.cancel();
        }
    }
}
