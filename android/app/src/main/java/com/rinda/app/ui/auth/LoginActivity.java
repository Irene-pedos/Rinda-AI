package com.rinda.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import androidx.appcompat.app.AppCompatActivity;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.ApiErrorParser;
import com.rinda.app.data.model.ApiProblem;
import com.rinda.app.data.model.OtpRequest;
import com.rinda.app.databinding.ActivityLoginBinding;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    public static final String EXTRA_PHONE = "extra_phone";
    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupDemoChips();

        binding.btnSendCode.setOnClickListener(v -> requestOtp());
        binding.etPhone.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                requestOtp();
                return true;
            }
            return false;
        });
    }

    private void setupDemoChips() {
        binding.chipFarmer1.setOnClickListener(v -> fillDemo("0780000001"));
        binding.chipFarmer2.setOnClickListener(v -> fillDemo("0780000002"));
        binding.chipAgent.setOnClickListener(v -> fillDemo("0780000003"));
        binding.chipNewFarmer.setOnClickListener(v -> fillDemo("0781234567"));
    }

    private void fillDemo(String phone) {
        binding.etPhone.setText(phone);
        binding.etPhone.setSelection(phone.length());
        binding.tilPhone.setError(null);
    }

    private void requestOtp() {
        binding.tilPhone.setError(null);
        String rawInput = binding.etPhone.getText() != null
                ? binding.etPhone.getText().toString().replace(" ", "").trim() : "";

        if (rawInput.length() < 9) {
            binding.tilPhone.setError(getString(R.string.error_phone_invalid));
            binding.etPhone.requestFocus();
            return;
        }

        // Normalise to a Rwandan format the backend accepts: 07XXXXXXXX or +2507XXXXXXXX
        String formattedPhone;
        if (rawInput.startsWith("+250")) {
            formattedPhone = rawInput;
        } else if (rawInput.startsWith("250")) {
            formattedPhone = "+" + rawInput;
        } else if (rawInput.startsWith("0")) {
            formattedPhone = rawInput;
        } else {
            formattedPhone = "0" + rawInput;
        }

        setLoading(true);

        ApiClient.getInstance(this).getService().requestOtp(new OtpRequest(formattedPhone))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        setLoading(false);
                        if (response.isSuccessful() || response.code() == 202) {
                            Intent intent = new Intent(LoginActivity.this, VerifyOtpActivity.class);
                            intent.putExtra(EXTRA_PHONE, formattedPhone);
                            startActivity(intent);
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            binding.tilPhone.setError(ApiErrorParser.getFriendlyMessage(LoginActivity.this, problem));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        setLoading(false);
                        binding.tilPhone.setError(getString(R.string.error_network_action));
                    }
                });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSendCode.setEnabled(!loading);
        binding.etPhone.setEnabled(!loading);
    }
}
