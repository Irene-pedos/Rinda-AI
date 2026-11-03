package com.rinda.app.ui.profile;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.ApiErrorParser;
import com.rinda.app.data.api.RindaApiService;
import com.rinda.app.data.api.TokenManager;
import com.rinda.app.data.model.ApiProblem;
import com.rinda.app.data.model.PayoutAccountRequest;
import com.rinda.app.data.model.PayoutAccountResponse;
import com.rinda.app.data.model.PayoutVerifyRequest;
import com.rinda.app.data.model.UserResponse;
import com.rinda.app.databinding.ActivityPayoutAccountBinding;
import com.rinda.app.ui.common.Ui;
import java.util.regex.Pattern;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 2-step wizard for configuring simulated mobile money payout account
 * according to ANDROID_UI_GUIDELINES.md §7.2 and RINDA_PROJECT.md §2.2 #12.
 *
 * Step 1: Provider selection (MTN MoMo vs Airtel Money) and phone number.
 * Step 2: 6-digit SMS OTP verification (60s countdown, auto-verify on 6 digits).
 * Step 3: Success animation with verified badge, persisting to secure storage.
 */
public class PayoutAccountActivity extends AppCompatActivity {

    private static final String PROVIDER_MOMO = "MTN_MOMO";
    private static final String PROVIDER_AIRTEL = "AIRTEL";
    private static final long RESEND_WAIT_MS = 60_000;
    private static final Pattern RW_PHONE_PATTERN = Pattern.compile("^(\\+?250|0)?7[2389]\\d{7}$");

    private ActivityPayoutAccountBinding binding;
    private RindaApiService apiService;
    private TokenManager tokenManager;

    private int currentStep = 1;
    private String selectedProvider = PROVIDER_MOMO;
    private String formattedPhone = "";
    private CountDownTimer resendTimer;
    private boolean isVerifying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPayoutAccountBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        tokenManager = TokenManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getService();

        setupToolbar();
        setupProviderCards();
        setupPhoneInput();
        setupOtpInput();
        setupNavigationButtons();

        goToStep(1);
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> handleBack());
    }

    private void setupProviderCards() {
        String existingProvider = tokenManager.getPayoutProvider();
        if (PROVIDER_AIRTEL.equalsIgnoreCase(existingProvider)) {
            selectedProvider = PROVIDER_AIRTEL;
        } else {
            selectedProvider = PROVIDER_MOMO;
        }
        updateProviderSelectionUI();

        binding.cardProviderMomo.setOnClickListener(v -> {
            selectedProvider = PROVIDER_MOMO;
            updateProviderSelectionUI();
        });

        binding.cardProviderAirtel.setOnClickListener(v -> {
            selectedProvider = PROVIDER_AIRTEL;
            updateProviderSelectionUI();
        });
    }

    private void updateProviderSelectionUI() {
        boolean isMomo = PROVIDER_MOMO.equals(selectedProvider);
        int selectedStroke = ContextCompat.getColor(this, R.color.rinda_primary);
        int unselectedStroke = ContextCompat.getColor(this, R.color.rinda_outline);

        binding.cardProviderMomo.setStrokeColor(isMomo ? selectedStroke : unselectedStroke);
        binding.cardProviderMomo.setStrokeWidth(isMomo ? 4 : 2);
        binding.ivCheckMomo.setVisibility(isMomo ? View.VISIBLE : View.GONE);

        binding.cardProviderAirtel.setStrokeColor(!isMomo ? selectedStroke : unselectedStroke);
        binding.cardProviderAirtel.setStrokeWidth(!isMomo ? 4 : 2);
        binding.ivCheckAirtel.setVisibility(!isMomo ? View.VISIBLE : View.GONE);
    }

    private void setupPhoneInput() {
        String existingPhone = tokenManager.getPayoutMsisdn();
        if (existingPhone != null && !existingPhone.isEmpty()) {
            binding.etPhone.setText(existingPhone);
        } else {
            UserResponse user = tokenManager.getUser();
            if (user != null && user.getPhone() != null) {
                binding.etPhone.setText(user.getPhone());
            }
        }

        binding.etPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                binding.tilPhone.setError(null);
            }
        });
    }

    private void setupOtpInput() {
        binding.etOtpCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                binding.tilOtpCode.setError(null);
                boolean complete = s != null && s.length() == 6;
                binding.btnNext.setEnabled(complete && !isVerifying);
                if (complete && !isVerifying) {
                    verifyOtp();
                }
            }
        });

        binding.btnResendOtp.setOnClickListener(v -> resendOtp());
    }

    private void setupNavigationButtons() {
        binding.btnBack.setOnClickListener(v -> handleBack());
        binding.btnNext.setOnClickListener(v -> {
            if (currentStep == 1) {
                submitProviderAndPhone();
            } else if (currentStep == 2) {
                verifyOtp();
            } else if (currentStep == 3) {
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    private void goToStep(int step) {
        currentStep = step;
        binding.tilPhone.setError(null);
        binding.tilOtpCode.setError(null);

        if (step == 1) {
            binding.stepHeader.setVisibility(View.VISIBLE);
            binding.stepProgress.setProgress(1);
            binding.tvStepLabel.setText(getString(R.string.step_of, 1, 2));
            binding.tvStepTitle.setText(R.string.payout_step_provider);

            Ui.reveal(binding.stepProvider);
            binding.stepOtp.setVisibility(View.GONE);
            binding.stepSuccess.setVisibility(View.GONE);

            binding.btnBack.setVisibility(View.GONE);
            binding.btnNext.setText(R.string.btn_send_payout_otp);
            binding.btnNext.setIconResource(R.drawable.ic_arrow_forward);
            binding.btnNext.setEnabled(true);
        } else if (step == 2) {
            binding.stepHeader.setVisibility(View.VISIBLE);
            binding.stepProgress.setProgress(2);
            binding.tvStepLabel.setText(getString(R.string.step_of, 2, 2));
            binding.tvStepTitle.setText(R.string.payout_step_otp);

            binding.tvOtpSubtitle.setText(getString(R.string.payout_otp_subtitle, formattedPhone));

            binding.stepProvider.setVisibility(View.GONE);
            Ui.reveal(binding.stepOtp);
            binding.stepSuccess.setVisibility(View.GONE);

            binding.btnBack.setVisibility(View.VISIBLE);
            binding.btnNext.setText(R.string.btn_confirm_payout);
            binding.btnNext.setIcon(null);

            Editable code = binding.etOtpCode.getText();
            binding.btnNext.setEnabled(code != null && code.length() == 6);

            startResendCountdown();
            binding.etOtpCode.requestFocus();
        } else if (step == 3) {
            if (resendTimer != null) {
                resendTimer.cancel();
            }
            binding.stepHeader.setVisibility(View.GONE);
            binding.stepProvider.setVisibility(View.GONE);
            binding.stepOtp.setVisibility(View.GONE);
            Ui.reveal(binding.stepSuccess);

            // Animate checkmark badge
            binding.ivSuccessBadge.setScaleX(0f);
            binding.ivSuccessBadge.setScaleY(0f);
            binding.ivSuccessBadge.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator(1.4f))
                    .start();

            binding.btnBack.setVisibility(View.GONE);
            binding.btnNext.setText(R.string.btn_done);
            binding.btnNext.setIcon(null);
            binding.btnNext.setEnabled(true);
        }
    }

    private void submitProviderAndPhone() {
        String rawPhone = binding.etPhone.getText() != null ? binding.etPhone.getText().toString().trim() : "";
        String cleaned = rawPhone.replaceAll("[\\s-]", "");

        if (cleaned.isEmpty() || !RW_PHONE_PATTERN.matcher(cleaned).matches()) {
            binding.tilPhone.setError(getString(R.string.error_phone_invalid));
            return;
        }

        // Format to standard Rwandan mobile number format (+2507XXXXXXXX)
        if (cleaned.startsWith("0")) {
            formattedPhone = "+250" + cleaned.substring(1);
        } else if (!cleaned.startsWith("+")) {
            formattedPhone = "+" + cleaned;
        } else {
            formattedPhone = cleaned;
        }

        binding.btnNext.setEnabled(false);

        // Call backend POST /payout-account
        apiService.savePayoutAccount(new PayoutAccountRequest(selectedProvider, formattedPhone))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        binding.btnNext.setEnabled(true);
                        if (response.isSuccessful() || response.code() == 404) {
                            // HTTP 200..299 or 404 (planned backend endpoint fallback for dev/demo)
                            goToStep(2);
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            binding.tilPhone.setError(problem.getDetail() != null ? problem.getDetail() : getString(R.string.error_phone_invalid));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        binding.btnNext.setEnabled(true);
                        // If offline or connecting to dev backend with network timeout, proceed with simulated flow
                        goToStep(2);
                    }
                });
    }

    private void verifyOtp() {
        if (isVerifying) return;
        String code = binding.etOtpCode.getText() != null ? binding.etOtpCode.getText().toString().trim() : "";
        if (code.length() != 6) {
            binding.tilOtpCode.setError(getString(R.string.error_otp_invalid));
            return;
        }

        isVerifying = true;
        binding.btnNext.setEnabled(false);

        apiService.verifyPayoutAccount(new PayoutVerifyRequest(code))
                .enqueue(new Callback<PayoutAccountResponse>() {
                    @Override
                    public void onResponse(Call<PayoutAccountResponse> call, Response<PayoutAccountResponse> response) {
                        isVerifying = false;
                        if (response.isSuccessful() || response.code() == 404) {
                            // Verified with backend or simulated fallback
                            tokenManager.savePayoutAccount(selectedProvider, formattedPhone, true);
                            goToStep(3);
                        } else {
                            binding.btnNext.setEnabled(true);
                            ApiProblem problem = ApiErrorParser.parse(response);
                            binding.tilOtpCode.setError(problem.getDetail() != null ? problem.getDetail() : getString(R.string.error_otp_invalid));
                        }
                    }

                    @Override
                    public void onFailure(Call<PayoutAccountResponse> call, Throwable t) {
                        isVerifying = false;
                        // For academic simulation offline / dev fallback
                        tokenManager.savePayoutAccount(selectedProvider, formattedPhone, true);
                        goToStep(3);
                    }
                });
    }

    private void resendOtp() {
        startResendCountdown();
        binding.tilOtpCode.setError(null);
        Ui.toast(binding.getRoot(), R.string.new_code_sent);
        apiService.savePayoutAccount(new PayoutAccountRequest(selectedProvider, formattedPhone)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {}
            @Override
            public void onFailure(Call<Void> call, Throwable t) {}
        });
    }

    private void startResendCountdown() {
        binding.btnResendOtp.setEnabled(false);
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        resendTimer = new CountDownTimer(RESEND_WAIT_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                binding.btnResendOtp.setText(getString(R.string.resend_code_in, (int) (millisUntilFinished / 1000) + 1));
            }

            @Override
            public void onFinish() {
                binding.btnResendOtp.setText(R.string.resend_code);
                binding.btnResendOtp.setEnabled(true);
            }
        }.start();
    }

    private void handleBack() {
        if (currentStep == 2) {
            goToStep(1);
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        super.onDestroy();
    }
}
