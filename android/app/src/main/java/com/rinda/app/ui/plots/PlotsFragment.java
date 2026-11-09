package com.rinda.app.ui.plots;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.PageResponse;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.databinding.FragmentPlotsBinding;
import com.rinda.app.ui.common.EmptyState;
import com.rinda.app.ui.common.Ui;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlotsFragment extends Fragment {

    private FragmentPlotsBinding binding;
    private PlotsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPlotsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new PlotsAdapter(plot -> {
            Intent intent = new Intent(requireContext(), PlotDetailActivity.class);
            intent.putExtra(PlotDetailActivity.EXTRA_PLOT_ID, plot.id);
            intent.putExtra(PlotDetailActivity.EXTRA_PLOT_NAME, plot.name);
            startActivity(intent);
        });

        binding.rvPlots.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPlots.setAdapter(adapter);
        // The button shrinks to an icon while scrolling, so it never hides a card for long.
        binding.rvPlots.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 8 && binding.fabAddPlot.isExtended()) {
                    binding.fabAddPlot.shrink();
                } else if (dy < -8 && !binding.fabAddPlot.isExtended()) {
                    binding.fabAddPlot.extend();
                }
            }
        });

        EmptyState.bind(binding.layoutEmpty, R.drawable.ic_nav_plots, R.string.empty_plots_title,
                R.string.empty_plots, R.string.getting_started_cta, v -> openRegister());

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_primary);
        binding.swipeRefresh.setOnRefreshListener(this::fetchPlots);

        binding.fabAddPlot.setOnClickListener(v -> openRegister());

        observeCachedPlots();
        fetchPlots();
    }

    private void openRegister() {
        startActivity(new Intent(requireContext(), RegisterPlotActivity.class));
    }

    private void observeCachedPlots() {
        RindaDatabase.getInstance(requireContext()).plotDao().getAllPlotsLiveData()
                .observe(getViewLifecycleOwner(), plotEntities -> {
                    if (plotEntities != null && binding != null) {
                        boolean wasEmpty = adapter.getItemCount() == 0;
                        adapter.setItems(plotEntities);
                        if (wasEmpty && !plotEntities.isEmpty()) {
                            binding.rvPlots.scheduleLayoutAnimation();
                        }
                        boolean empty = plotEntities.isEmpty();
                        EmptyState.show(binding.layoutEmpty, empty);
                        binding.fabAddPlot.setVisibility(empty ? View.GONE : View.VISIBLE);
                    }
                });
    }

    private void fetchPlots() {
        if (!Ui.isOnline(requireContext())) {
            binding.swipeRefresh.setRefreshing(false);
            Ui.toast(binding.getRoot(), R.string.error_network);
            return;
        }
        binding.swipeRefresh.setRefreshing(true);
        ApiClient.getInstance(requireContext()).getService().getPlots(0, 50)
                .enqueue(new Callback<PageResponse<PlotResponse>>() {
                    @Override
                    public void onResponse(Call<PageResponse<PlotResponse>> call, Response<PageResponse<PlotResponse>> response) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            saveToLocalCache(response.body().getItems());
                        }
                    }

                    @Override
                    public void onFailure(Call<PageResponse<PlotResponse>> call, Throwable t) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        Ui.error(binding.getRoot(), getString(R.string.error_network_action), PlotsFragment.this::fetchPlots);
                    }
                });
    }

    private void saveToLocalCache(List<PlotResponse> plots) {
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
