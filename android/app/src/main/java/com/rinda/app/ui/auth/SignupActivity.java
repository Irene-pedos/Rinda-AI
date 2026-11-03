package com.rinda.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
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
import com.rinda.app.ui.common.Ui;
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
    private ArrayAdapter<CooperativeResponse> coopAdapter;
    private CooperativeResponse selectedCoop;

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

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        coopAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, cooperatives);
        binding.actCooperative.setAdapter(coopAdapter);
        binding.actCooperative.setOnItemClickListener((parent, view, position, id) -> {
            selectedCoop = coopAdapter.getItem(position);
            binding.tilCooperative.setError(null);
        });

        loadCooperatives();

        binding.btnCompleteSignup.setOnClickListener(v -> completeRegistration());
    }

    private void loadCooperatives() {
        binding.tilCooperative.setHelperText(getString(R.string.cooperative_loading));
        ApiClient.getInstance(this).getService().getCooperatives()
                .enqueue(new Callback<List<CooperativeResponse>>() {
                    @Override
                    public void onResponse(Call<List<CooperativeResponse>> call, Response<List<CooperativeResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            cooperatives.clear();
                            cooperatives.addAll(response.body());
                            coopAdapter.notifyDataSetChanged();
                            binding.tilCooperative.setHelperText(getString(R.string.cooperative_select));
                            if (cooperatives.size() == 1) {
                                selectedCoop = cooperatives.get(0);
                                binding.actCooperative.setText(selectedCoop.toString(), false);
                            }
                        } else {
                            showCoopLoadError();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<CooperativeResponse>> call, Throwable t) {
                        showCoopLoadError();
                    }
                });
    }

    private void showCoopLoadError() {
        binding.tilCooperative.setHelperText(getString(R.string.cooperative_load_failed));
        Ui.error(binding.getRoot(), getString(R.string.cooperative_load_failed), this::loadCooperatives);
    }

    private void completeRegistration() {
        binding.tilFullName.setError(null);
        binding.tilCooperative.setError(null);
        String fullName = binding.etFullName.getText() != null ? binding.etFullName.getText().toString().trim() : "";

        boolean ok = true;
        if (fullName.isEmpty()) {
            binding.tilFullName.setError(getString(R.string.error_name_required));
            ok = false;
        }
        if (selectedCoop == null) {
            binding.tilCooperative.setError(getString(R.string.error_cooperative_required));
            ok = false;
        }
        if (!ok) {
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
                            Ui.toast(binding.getRoot(), ApiErrorParser.getFriendlyMessage(SignupActivity.this, problem));
                        }
                    }

                    @Override
                    public void onFailure(Call<TokenResponse> call, Throwable t) {
                        setLoading(false);
                        Ui.error(binding.getRoot(), getString(R.string.error_network_action), SignupActivity.this::completeRegistration);
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
