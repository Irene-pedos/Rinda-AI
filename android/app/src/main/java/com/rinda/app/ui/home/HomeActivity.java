package com.rinda.app.ui.home;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.rinda.app.R;
import com.rinda.app.data.api.TokenManager;
import com.rinda.app.data.model.Role;
import com.rinda.app.data.model.UserResponse;
import com.rinda.app.databinding.ActivityHomeBinding;
import com.rinda.app.ui.agent.AgentFarmersFragment;
import com.rinda.app.ui.agent.FieldVisitActivity;
import com.rinda.app.ui.alerts.AlertsFragment;
import com.rinda.app.ui.claims.ClaimsFragment;
import com.rinda.app.ui.common.Ui;
import com.rinda.app.ui.plots.PlotsFragment;
import com.rinda.app.ui.profile.ProfileFragment;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private final Fragment homeFragment = new HomeFragment();
    private final Fragment plotsFragment = new PlotsFragment();
    private final Fragment alertsFragment = new AlertsFragment();
    private final Fragment claimsFragment = new ClaimsFragment();
    private final Fragment profileFragment = new ProfileFragment();
    private final Fragment agentFarmersFragment = new AgentFarmersFragment();

    private ConnectivityManager connectivityManager;
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            runOnUiThread(() -> updateOfflineBanner());
        }

        @Override
        public void onLost(@NonNull Network network) {
            runOnUiThread(() -> updateOfflineBanner());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupBottomNavigation();

        if (savedInstanceState == null) {
            boolean isAgent = TokenManager.getInstance(this).getUserRole() == Role.AGENT;
            if (isAgent) {
                show(R.string.agent_farmers_title, agentFarmersFragment);
            } else {
                loadFragment(homeFragment, false);
            }
        }

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (connectivityManager != null) {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, networkCallback);
        }
        updateOfflineBanner();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered
            }
        }
    }

    private void updateOfflineBanner() {
        boolean offline = !Ui.isOnline(this);
        if (offline) {
            new Thread(() -> {
                java.util.List<com.rinda.app.data.local.PlotEntity> plots =
                        com.rinda.app.data.local.RindaDatabase.getInstance(this).plotDao().getAllPlots();
                long newestCachedAt = 0;
                if (plots != null) {
                    for (com.rinda.app.data.local.PlotEntity p : plots) {
                        if (p.cachedAt > newestCachedAt) newestCachedAt = p.cachedAt;
                    }
                }
                final long lastCached = newestCachedAt;
                runOnUiThread(() -> {
                    if (binding == null) return;
                    if (lastCached > 0) {
                        String relTime = com.rinda.app.util.DateTimeUtils.formatRelativeTime(lastCached);
                        binding.tvOfflineBanner.setText(getString(R.string.offline_banner_with_time, relTime));
                    } else {
                        binding.tvOfflineBanner.setText(R.string.offline_banner);
                    }
                    Ui.reveal(binding.tvOfflineBanner);
                });
            }).start();
        } else {
            binding.tvOfflineBanner.setVisibility(View.GONE);
        }
    }

    private void setupToolbar() {
        UserResponse user = TokenManager.getInstance(this).getUser();
        if (user != null) {
            binding.tvDemoBadge.setVisibility(user.isDemo() ? View.VISIBLE : View.GONE);
            binding.tvRoleBadge.setText(user.getRole() == Role.AGENT ? R.string.role_agent : R.string.role_farmer);
        }
    }

    private void setupBottomNavigation() {
        boolean isAgent = TokenManager.getInstance(this).getUserRole() == Role.AGENT;
        if (isAgent) {
            binding.bottomNav.getMenu().clear();
            binding.bottomNav.inflateMenu(R.menu.bottom_nav_agent_menu);
        }

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                show(R.string.app_name, homeFragment);
            } else if (itemId == R.id.nav_agent_farmers) {
                show(R.string.agent_farmers_title, agentFarmersFragment);
            } else if (itemId == R.id.nav_plots) {
                show(R.string.my_plots_title, plotsFragment);
            } else if (itemId == R.id.nav_agent_visits) {
                startActivity(new Intent(this, FieldVisitActivity.class));
                return false;
            } else if (itemId == R.id.nav_alerts) {
                show(R.string.nav_alerts, alertsFragment);
            } else if (itemId == R.id.nav_claims) {
                show(R.string.claims_title, claimsFragment);
            } else if (itemId == R.id.nav_profile) {
                show(R.string.nav_profile, profileFragment);
            } else {
                return false;
            }
            return true;
        });
        // Tapping the current tab again does nothing (no reload flash).
        binding.bottomNav.setOnItemReselectedListener(item -> {
        });
    }

    private void show(int titleRes, Fragment fragment) {
        binding.tvToolbarTitle.setText(titleRes);
        loadFragment(fragment, true);
    }

    public void selectTab(int navItemId) {
        binding.bottomNav.setSelectedItemId(navItemId);
    }

    private void loadFragment(Fragment fragment, boolean animate) {
        androidx.fragment.app.FragmentTransaction tx = getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true);
        if (animate) {
            tx.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);
        }
        tx.replace(R.id.fragmentContainer, fragment).commit();
    }
}
