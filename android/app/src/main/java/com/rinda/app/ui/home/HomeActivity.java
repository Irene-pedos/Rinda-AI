package com.rinda.app.ui.home;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.rinda.app.R;
import com.rinda.app.data.api.TokenManager;
import com.rinda.app.data.model.Role;
import com.rinda.app.data.model.UserResponse;
import com.rinda.app.databinding.ActivityHomeBinding;
import com.rinda.app.ui.alerts.AlertsFragment;
import com.rinda.app.ui.claims.ClaimsFragment;
import com.rinda.app.ui.plots.PlotsFragment;
import com.rinda.app.ui.profile.ProfileFragment;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private final Fragment homeFragment = new HomeFragment();
    private final Fragment plotsFragment = new PlotsFragment();
    private final Fragment alertsFragment = new AlertsFragment();
    private final Fragment claimsFragment = new ClaimsFragment();
    private final Fragment profileFragment = new ProfileFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupBottomNavigation();

        if (savedInstanceState == null) {
            loadFragment(homeFragment);
        }
    }

    private void setupToolbar() {
        TokenManager tokenManager = TokenManager.getInstance(this);
        UserResponse user = tokenManager.getUser();

        if (user != null) {
            // Display Demo label if isDemo == true
            binding.tvDemoBadge.setVisibility(user.isDemo() ? View.VISIBLE : View.GONE);

            // Display Role badge
            if (user.getRole() == Role.AGENT) {
                binding.tvRoleBadge.setText(R.string.role_agent);
                binding.tvRoleBadge.setBackgroundResource(R.drawable.ic_status_watch_bg);
                binding.tvRoleBadge.setTextColor(getColor(R.color.rinda_gold_dark));
            } else {
                binding.tvRoleBadge.setText(R.string.role_farmer);
                binding.tvRoleBadge.setBackgroundResource(R.drawable.ic_status_healthy_bg);
                binding.tvRoleBadge.setTextColor(getColor(R.color.rinda_green));
            }
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                binding.tvToolbarTitle.setText(R.string.app_name);
                loadFragment(homeFragment);
                return true;
            } else if (itemId == R.id.nav_plots) {
                binding.tvToolbarTitle.setText(R.string.nav_plots);
                loadFragment(plotsFragment);
                return true;
            } else if (itemId == R.id.nav_alerts) {
                binding.tvToolbarTitle.setText(R.string.nav_alerts);
                loadFragment(alertsFragment);
                return true;
            } else if (itemId == R.id.nav_claims) {
                binding.tvToolbarTitle.setText(R.string.nav_claims);
                loadFragment(claimsFragment);
                return true;
            } else if (itemId == R.id.nav_profile) {
                binding.tvToolbarTitle.setText(R.string.nav_profile);
                loadFragment(profileFragment);
                return true;
            }
            return false;
        });
    }

    public void selectTab(int navItemId) {
        binding.bottomNav.setSelectedItemId(navItemId);
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
