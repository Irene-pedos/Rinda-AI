package com.rinda.app.ui.home;

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
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.PageResponse;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.data.model.UserResponse;
import com.rinda.app.data.model.WeatherResponse;
import com.rinda.app.databinding.FragmentHomeBinding;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

        TokenManager tokenManager = TokenManager.getInstance(requireContext());
        UserResponse user = tokenManager.getUser();

        if (user != null) {
            binding.tvGreeting.setText("Welcome, " + user.getFullName());
            if (user.isDemo()) {
                binding.cardDemoNotice.setVisibility(View.VISIBLE);
            }
        }

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_green);
        binding.swipeRefresh.setOnRefreshListener(this::loadPlotsData);

        binding.cardPlotsSummary.setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).selectTab(R.id.nav_plots);
            }
        });

        binding.btnQuickRegister.setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).selectTab(R.id.nav_plots);
            }
        });

        binding.btnQuickReport.setOnClickListener(v -> {
            // Future step: problem report form
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).selectTab(R.id.nav_alerts);
            }
        });

        loadCachedPlots();
        loadPlotsData();
    }

    private void loadCachedPlots() {
        RindaDatabase.getInstance(requireContext()).plotDao().getAllPlotsLiveData()
                .observe(getViewLifecycleOwner(), plotEntities -> {
                    if (plotEntities != null) {
                        updatePlotsSummaryUI(plotEntities);
                    }
                });
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
                                long firstPlotId = response.body().getItems().get(0).getId();
                                loadWeatherForPlot(firstPlotId);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<PageResponse<PlotResponse>> call, Throwable t) {
                        if (binding == null) return;
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
                            binding.tvRainStatus.setText("Latest observation: " + latest.getRainMm() + " mm rain (" + latest.getDate() + ")");
                        }
                    }

                    @Override
                    public void onFailure(Call<List<WeatherResponse>> call, Throwable t) {
                        // Keep default text
                    }
                });
    }

    private void savePlotsToCache(List<PlotResponse> plots) {
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
            RindaDatabase.getInstance(requireContext()).plotDao().insertOrUpdateAll(entities);
        }).start();
    }

    private void updatePlotsSummaryUI(List<PlotEntity> plots) {
        binding.tvPlotsCount.setText(String.valueOf(plots.size()));

        BigDecimal totalArea = BigDecimal.ZERO;
        for (PlotEntity p : plots) {
            if (p.areaHa != null) {
                totalArea = totalArea.add(p.areaHa);
            }
        }
        binding.tvTotalArea.setText(String.format("%.2f ha", totalArea.doubleValue()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
