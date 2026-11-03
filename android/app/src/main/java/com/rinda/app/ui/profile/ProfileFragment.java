package com.rinda.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.rinda.app.BuildConfig;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.TokenManager;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.Role;
import com.rinda.app.data.model.UserResponse;
import com.rinda.app.databinding.DialogTextInputBinding;
import com.rinda.app.databinding.FragmentProfileBinding;
import com.rinda.app.databinding.ViewSettingsRowBinding;
import com.rinda.app.ui.auth.ConsentActivity;
import com.rinda.app.ui.auth.LanguageActivity;
import com.rinda.app.ui.auth.LoginActivity;
import com.rinda.app.ui.common.InfoSheet;
import com.rinda.app.ui.common.Ui;
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

        row(binding.rowLanguage, R.drawable.ic_language, R.string.profile_language, v -> {
            Intent intent = new Intent(requireContext(), LanguageActivity.class);
            intent.putExtra("is_from_settings", true);
            startActivity(intent);
        });
        row(binding.rowHelp, R.drawable.ic_help, R.string.profile_help, v ->
                InfoSheet.show(requireContext(), R.drawable.ic_help, R.string.help_how_it_works_title, R.string.help_how_it_works_body));
        row(binding.rowPrivacy, R.drawable.ic_lock, R.string.profile_privacy, v -> {
            Intent intent = new Intent(requireContext(), ConsentActivity.class);
            intent.putExtra(ConsentActivity.EXTRA_READ_ONLY, true);
            startActivity(intent);
        });
        // Server address is a testing tool: only in debug builds.
        if (BuildConfig.DEBUG) {
            row(binding.rowServer, R.drawable.ic_server, R.string.profile_server, v -> showServerConfigDialog());
        } else {
            binding.rowServer.getRoot().setVisibility(View.GONE);
        }

        binding.btnLogout.setOnClickListener(v -> confirmLogout());
        binding.tvVersion.setText(getString(R.string.app_version, BuildConfig.VERSION_NAME));

        displayUserProfile();
    }

    private void row(ViewSettingsRowBinding row, int iconRes, int titleRes, View.OnClickListener click) {
        row.ivRowIcon.setImageResource(iconRes);
        row.tvRowTitle.setText(titleRes);
        row.getRoot().setOnClickListener(click);
    }

    private void value(ViewSettingsRowBinding row, String value) {
        row.tvRowValue.setText(value);
        row.tvRowValue.setVisibility(value != null && !value.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        displayUserProfile();
    }

    private void displayUserProfile() {
        if (binding == null) return;
        UserResponse user = TokenManager.getInstance(requireContext()).getUser();

        if (user != null) {
            binding.tvProfileName.setText(user.getFullName());
            binding.tvProfilePhone.setText(user.getPhone());
            binding.tvProfileRole.setText(roleLabel(user.getRole()));
            binding.tvProfileDemo.setVisibility(user.isDemo() ? View.VISIBLE : View.GONE);
            binding.tvAvatarInitials.setText(initials(user.getFullName()));
        }

        value(binding.rowLanguage, getString(LocaleHelper.isKinyarwanda(requireContext())
                ? R.string.lang_kinyarwanda_native : R.string.lang_english_native));
        value(binding.rowServer, ApiClient.getInstance(requireContext()).getActiveBaseUrl());
    }

    private static int roleLabel(Role role) {
        if (role == null) return R.string.role_farmer;
        switch (role) {
            case AGENT:
                return R.string.role_agent;
            case OFFICER:
                return R.string.role_officer;
            case ADMIN:
                return R.string.role_admin;
            case FARMER:
            default:
                return R.string.role_farmer;
        }
    }

    private static String initials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(java.util.Locale.getDefault());
    }

    private void showServerConfigDialog() {
        TokenManager tokenManager = TokenManager.getInstance(requireContext());
        String currentUrl = ApiClient.getInstance(requireContext()).getActiveBaseUrl();

        DialogTextInputBinding input = DialogTextInputBinding.inflate(getLayoutInflater());
        input.etInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.etInput.setText(currentUrl);
        input.etInput.setSelection(currentUrl.length());

        Ui.dialog(requireContext())
                .setTitle(R.string.server_dialog_title)
                .setMessage(R.string.server_dialog_body)
                .setView(input.getRoot())
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String newUrl = input.etInput.getText() != null ? input.etInput.getText().toString().trim() : "";
                    if (!newUrl.isEmpty()) {
                        tokenManager.setCustomBaseUrl(newUrl);
                        ApiClient.resetInstance();
                        displayUserProfile();
                        Ui.toast(binding.getRoot(), R.string.server_updated);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void confirmLogout() {
        Ui.dialog(requireContext())
                .setIcon(R.drawable.ic_logout)
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_body)
                .setPositiveButton(R.string.btn_logout, (dialog, which) -> {
                    TokenManager.getInstance(requireContext()).clear();
                    final android.content.Context app = requireContext().getApplicationContext();
                    new Thread(() -> {
                        RindaDatabase db = RindaDatabase.getInstance(app);
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
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
