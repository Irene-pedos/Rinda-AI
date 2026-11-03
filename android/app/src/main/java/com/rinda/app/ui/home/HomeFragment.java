package com.rinda.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.TokenManager;
import com.rinda.app.data.local.NotificationEntity;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.PageResponse;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.data.model.PlotStatus;
import com.rinda.app.data.model.UserResponse;
import com.rinda.app.data.model.WeatherResponse;
import com.rinda.app.databinding.FragmentHomeBinding;
import com.rinda.app.databinding.ViewActionTileBinding;
import com.rinda.app.databinding.ViewStepRowBinding;
import com.rinda.app.ui.alerts.AlertRouter;
import com.rinda.app.ui.common.InfoSheet;
import com.rinda.app.ui.common.StatusUi;
import com.rinda.app.ui.common.Ui;
import com.rinda.app.ui.plots.RegisterPlotActivity;
import com.rinda.app.util.DateTimeUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Home tab. Every value shown is real (API or cache). Where there is nothing yet,
 * the screen says so and shows the next step, instead of a placeholder status.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindGreeting();
        bindGettingStarted();
        bindTiles();

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_primary);
        binding.swipeRefresh.setOnRefreshListener(this::loadPlotsData);

        binding.cardPlotsSummary.setOnClickListener(v -> selectTab(R.id.nav_plots));
        binding.btnSeeAlerts.setOnClickListener(v -> selectTab(R.id.nav_alerts));
        binding.btnHowItWorks.setOnClickListener(v ->
                InfoSheet.show(requireContext(), R.drawable.ic_help, R.string.help_how_it_works_title, R.string.help_how_it_works_body));

        observeCachedPlots();
        observeLatestAlert();
        loadPlotsData();
    }

    private void bindGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        binding.tvGreetingLabel.setText(hour < 12 ? R.string.greeting_morning
                : hour < 17 ? R.string.greeting_afternoon : R.string.greeting_evening);

        UserResponse user = TokenManager.getInstance(requireContext()).getUser();
        if (user != null) {
            String name = user.getFullName();
            binding.tvGreeting.setText(name != null && !name.trim().isEmpty() ? firstName(name) : getString(R.string.greeting_fallback));
            binding.cardDemoNotice.setVisibility(user.isDemo() ? View.VISIBLE : View.GONE);
        }
    }

    private static String firstName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private void bindGettingStarted() {
        bindStep(binding.step1, 1, R.string.getting_started_step_1);
        bindStep(binding.step2, 2, R.string.getting_started_step_2);
        bindStep(binding.step3, 3, R.string.getting_started_step_3);
        binding.btnGettingStarted.setOnClickListener(v -> openRegisterPlot());
    }

    private void bindStep(ViewStepRowBinding step, int number, int textRes) {
        step.tvStepNumber.setText(String.format(Locale.getDefault(), "%d", number));
        step.tvStepText.setText(textRes);
    }

    private void bindTiles() {
        bindTile(binding.tileAddPlot, R.drawable.ic_add, R.string.home_action_add_plot,
                R.color.rinda_primary_container, R.color.rinda_primary, v -> openRegisterPlot());
        bindTile(binding.tileMyPlots, R.drawable.ic_nav_plots, R.string.home_action_my_plots,
                R.color.rinda_secondary_container, R.color.rinda_secondary, v -> selectTab(R.id.nav_plots));
        bindTile(binding.tileAlerts, R.drawable.ic_nav_alerts, R.string.home_action_alerts,
                R.color.status_watch_bg, R.color.status_watch, v -> selectTab(R.id.nav_alerts));
        bindTile(binding.tileClaims, R.drawable.ic_nav_claims, R.string.home_action_claims,
                R.color.rinda_tertiary_container, R.color.rinda_tertiary, v -> selectTab(R.id.nav_claims));
    }

    private void bindTile(ViewActionTileBinding tile, int iconRes, int labelRes, int bgColor, int fgColor,
                          View.OnClickListener click) {
        tile.ivTileIcon.setImageResource(iconRes);
        tile.ivTileIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(), bgColor)));
        tile.ivTileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(), fgColor)));
        tile.tvTileLabel.setText(labelRes);
        tile.getRoot().setContentDescription(getString(labelRes));
        tile.getRoot().setOnClickListener(click);
    }

    private void openRegisterPlot() {
        startActivity(new Intent(requireContext(), RegisterPlotActivity.class));
    }

    private void selectTab(int navId) {
        if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) getActivity()).selectTab(navId);
        }
    }

    private void observeCachedPlots() {
        RindaDatabase.getInstance(requireContext()).plotDao().getAllPlotsLiveData()
                .observe(getViewLifecycleOwner(), plots -> {
                    if (plots != null) {
                        updatePlotsSummaryUI(plots);
                    }
                });
    }

    private void observeLatestAlert() {
        RindaDatabase.getInstance(requireContext()).notificationDao().getAllNotificationsLiveData()
                .observe(getViewLifecycleOwner(), list -> {
                    if (binding == null) return;
                    if (list == null || list.isEmpty()) {
                        showNoAlert();
                    } else {
                        showAlert(list.get(0));
                    }
                });
    }

    private void showNoAlert() {
        // Neutral look: "no alerts" is not a health status.
        binding.ivAlertIcon.setImageResource(R.drawable.ic_nav_alerts);
        binding.ivAlertIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.rinda_primary_container)));
        binding.ivAlertIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.rinda_primary)));
        binding.tvAlertTitle.setText(R.string.home_no_alerts);
        binding.tvAlertBody.setVisibility(View.GONE);
        binding.tvAlertTime.setVisibility(View.GONE);
        binding.ivAlertChevron.setVisibility(View.GONE);
        binding.cardLatestAlert.setClickable(false);
        binding.cardLatestAlert.setOnClickListener(null);
    }

    private void showAlert(NotificationEntity item) {
        StatusUi.Spec look = AlertRouter.look(item);
        StatusUi.applyBadge(binding.ivAlertIcon, look);
        binding.ivAlertIcon.setImageResource(AlertRouter.icon(item));
        binding.tvAlertTitle.setText(AlertRouter.title(requireContext(), item));
        if (item.body != null && !item.body.isEmpty()) {
            binding.tvAlertBody.setText(item.body);
            binding.tvAlertBody.setVisibility(View.VISIBLE);
        } else {
            binding.tvAlertBody.setVisibility(View.GONE);
        }
        String time = DateTimeUtils.formatRelativeTime(item.createdAt);
        binding.tvAlertTime.setText(item.plotName != null ? item.plotName + " • " + time : time);
        binding.tvAlertTime.setVisibility(View.VISIBLE);
        binding.ivAlertChevron.setVisibility(View.VISIBLE);
        binding.cardLatestAlert.setOnClickListener(v -> AlertRouter.open(requireContext(), item));
    }

    private void loadPlotsData() {
        binding.swipeRefresh.setRefreshing(true);
        ApiClient.getInstance(requireContext()).getService().getPlots(0, 50)
                .enqueue(new Callback<PageResponse<PlotResponse>>() {
                    @Override
                    public void onResponse(Call<PageResponse<PlotResponse>> call, Response<PageResponse<PlotResponse>> response) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            savePlotsToCache(response.body().getItems());
                            if (!response.body().getItems().isEmpty()) {
                                loadWeatherForPlot(response.body().getItems().get(0).getId());
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<PageResponse<PlotResponse>> call, Throwable t) {
                        if (binding == null) return;
                        // Offline: cached data stays on screen and the shell shows the offline banner.
                        binding.swipeRefresh.setRefreshing(false);
                    }
                });
    }

    private void loadWeatherForPlot(long plotId) {
        ApiClient.getInstance(requireContext()).getService().getWeather(plotId, null, null)
                .enqueue(new Callback<List<WeatherResponse>>() {
                    @Override
                    public void onResponse(Call<List<WeatherResponse>> call, Response<List<WeatherResponse>> response) {
                        if (binding == null) return;
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            WeatherResponse latest = response.body().get(response.body().size() - 1);
                            if (latest.getRainMm() != null) {
                                binding.tvRainStatus.setText(getString(R.string.home_weather_latest,
                                        latest.getRainMm().stripTrailingZeros().toPlainString(),
                                        DateTimeUtils.formatDate(latest.getDate())));
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<List<WeatherResponse>> call, Throwable t) {
                        // Keep the "no rain data yet" text
                    }
                });
    }

    private void savePlotsToCache(List<PlotResponse> plots) {
        final android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            List<PlotEntity> entities = new ArrayList<>();
            for (PlotResponse p : plots) {
                PlotEntity e = new PlotEntity();
                e.id = p.getId();
                e.farmerId = p.getFarmerId();
                e.name = p.getName();
                e.crop = p.getCrop();
                e.boundary = p.getBoundary();
                e.areaHa = p.getAreaHa();
                e.plantingDate = p.getPlantingDate();
                e.status = p.getStatus();
                e.dataStatus = p.getDataStatus();
                e.isDemo = p.isDemo();
                e.createdAt = p.getCreatedAt();
                e.updatedAt = p.getUpdatedAt();
                e.cachedAt = System.currentTimeMillis();
                entities.add(e);
            }
            RindaDatabase.getInstance(app).plotDao().insertOrUpdateAll(entities);
        }).start();
    }

    private void updatePlotsSummaryUI(List<PlotEntity> plots) {
        if (binding == null) return;
        BigDecimal totalArea = BigDecimal.ZERO;
        int verified = 0;
        for (PlotEntity p : plots) {
            if (p.areaHa != null) {
                totalArea = totalArea.add(p.areaHa);
            }
            if (p.status == PlotStatus.ACTIVE) {
                verified++;
            }
        }
        binding.tvPlotsCount.setText(String.format(Locale.getDefault(), "%d", plots.size()));
        binding.tvTotalArea.setText(String.format(Locale.getDefault(), "%.1f", totalArea.doubleValue()));
        binding.tvVerifiedCount.setText(String.format(Locale.getDefault(), "%d", verified));

        // New farmers see the 3 steps; once they have a plot, the guide goes away.
        if (plots.isEmpty()) {
            Ui.reveal(binding.cardGettingStarted);
        } else {
            binding.cardGettingStarted.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
