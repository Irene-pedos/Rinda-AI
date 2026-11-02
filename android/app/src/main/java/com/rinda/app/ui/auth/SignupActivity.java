package com.rinda.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.ApiErrorParser;
import com.rinda.app.data.api.TokenManager;
import com.rinda.app.data.model.ApiProblem;
import com.rinda.app.data.model.CooperativeResponse;
import com.rinda.app.data.model.OtpVerifyRequest;
import com.rinda.app.data.model.TokenResponse;
import com.rinda.app.databinding.ActivitySignupBinding;
import com.rinda.app.ui.home.HomeActivity;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignupActivity extends AppCompatActivity {

    private ActivitySignupBinding binding;
    private String phone;
    private String code;
    private final List<CooperativeResponse> cooperatives = new ArrayList<>();
    private ArrayAdapter<CooperativeResponse> spinnerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        phone = getIntent().getStringExtra(LoginActivity.EXTRA_PHONE);
        code = getIntent().getStringExtra(VerifyOtpActivity.EXTRA_CODE);

        if (phone == null || code == null) {
            finish();
            return;
        }

        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cooperatives);
        binding.spinnerCooperative.setAdapter(spinnerAdapter);

        loadCooperatives();

        binding.btnCompleteSignup.setOnClickListener(v -> completeRegistration());
    }

    private void loadCooperatives() {
        binding.pbCoops.setVisibility(View.VISIBLE);
        ApiClient.getInstance(this).getService().getCooperatives()
                .enqueue(new Callback<List<CooperativeResponse>>() {
                    @Override
                    public void onResponse(Call<List<CooperativeResponse>> call, Response<List<CooperativeResponse>> response) {
                        binding.pbCoops.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {
                            cooperatives.clear();
                            cooperatives.addAll(response.body());
                            spinnerAdapter.notifyDataSetChanged();
                        } else {
                            Toast.makeText(SignupActivity.this, "Unable to load cooperatives list", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<CooperativeResponse>> call, Throwable t) {
                        binding.pbCoops.setVisibility(View.GONE);
                        Toast.makeText(SignupActivity.this, getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void completeRegistration() {
        binding.tilFullName.setError(null);
        String fullName = binding.etFullName.getText() != null ? binding.etFullName.getText().toString().trim() : "";

        if (fullName.isEmpty()) {
            binding.tilFullName.setError("Please enter your full name");
            return;
        }

        if (cooperatives.isEmpty()) {
            Toast.makeText(this, "Please wait for cooperatives to load", Toast.LENGTH_SHORT).show();
            return;
        }

        CooperativeResponse selectedCoop = (CooperativeResponse) binding.spinnerCooperative.getSelectedItem();
        if (selectedCoop == null) {
            Toast.makeText(this, "Please select a cooperative", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        OtpVerifyRequest request = new OtpVerifyRequest(phone, code, fullName, selectedCoop.getId());
        ApiClient.getInstance(this).getService().verifyOtp(request)
                .enqueue(new Callback<TokenResponse>() {
                    @Override
                    public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            TokenManager.getInstance(SignupActivity.this).saveTokens(response.body());
                            goToHome();
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            String msg = ApiErrorParser.getFriendlyMessage(SignupActivity.this, problem);
                            binding.tilFullName.setError(msg);
                        }
                    }

                    @Override
                    public void onFailure(Call<TokenResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(SignupActivity.this, getString(R.string.error_network), Toast.LENGTH_SHORT).show();
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
        binding.btnCompleteSignup.setEnabled(!loading);
    }
}
