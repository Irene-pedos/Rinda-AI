package com.rinda.app.ui.profile;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.TokenManager;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.UserResponse;
import com.rinda.app.databinding.FragmentProfileBinding;
import com.rinda.app.ui.auth.LanguageActivity;
import com.rinda.app.ui.auth.LoginActivity;
import com.rinda.app.util.LocaleHelper;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        displayUserProfile();

        binding.btnLanguageOption.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), LanguageActivity.class);
            intent.putExtra("is_from_settings", true);
            startActivity(intent);
        });

        binding.btnServerConfig.setOnClickListener(v -> showServerConfigDialog());

        binding.btnLogout.setOnClickListener(v -> confirmLogout());
    }

    @Override
    public void onResume() {
        super.onResume();
        displayUserProfile();
    }

    private void displayUserProfile() {
        if (binding == null) return;
        TokenManager tokenManager = TokenManager.getInstance(requireContext());
        UserResponse user = tokenManager.getUser();

        if (user != null) {
            binding.tvProfileName.setText(user.getFullName());
            binding.tvProfilePhone.setText(user.getPhone());
            binding.tvProfileRole.setText(user.getRole() != null ? user.getRole().name() : "FARMER");
            binding.tvProfileDemo.setVisibility(user.isDemo() ? View.VISIBLE : View.GONE);

            if (user.getFullName() != null && !user.getFullName().isEmpty()) {
                binding.tvAvatarInitials.setText(user.getFullName().substring(0, 1).toUpperCase());
            }
        }

        binding.tvCurrentLanguage.setText(LocaleHelper.isKinyarwanda(requireContext()) ? "Ikinyarwanda" : "English");
        binding.tvActiveServerUrl.setText(ApiClient.getInstance(requireContext()).getActiveBaseUrl());
    }

    private void showServerConfigDialog() {
        TokenManager tokenManager = TokenManager.getInstance(requireContext());
        String currentUrl = ApiClient.getInstance(requireContext()).getActiveBaseUrl();

        EditText input = new EditText(requireContext());
        input.setText(currentUrl);
        input.setSelection(currentUrl.length());

        new AlertDialog.Builder(requireContext())
                .setTitle("Backend Server URL")
                .setMessage("• USB Reverse (Physical Phone): http://127.0.0.1:8080/api/v1/\n• Android Emulator: http://10.0.2.2:8080/api/v1/\n• Wi-Fi: http://<PC_IP>:8080/api/v1/")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newUrl = input.getText().toString().trim();
                    if (!newUrl.isEmpty()) {
                        tokenManager.setCustomBaseUrl(newUrl);
                        ApiClient.resetInstance();
                        displayUserProfile();
                        Toast.makeText(requireContext(), "Server URL updated!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.btn_logout)
                .setMessage("Are you sure you want to log out of Rinda?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    TokenManager.getInstance(requireContext()).clear();
                    new Thread(() -> {
                        RindaDatabase db = RindaDatabase.getInstance(requireContext());
                        db.plotDao().deleteAll();
                        db.notificationDao().deleteAll();
                        db.claimDao().deleteAll();
                    }).start();

                    Intent intent = new Intent(requireContext(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
