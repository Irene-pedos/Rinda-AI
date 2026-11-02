package com.rinda.app.ui.plots;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.PageResponse;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.databinding.FragmentPlotsBinding;
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
            Toast.makeText(requireContext(), "Plot: " + plot.name + " (" + plot.status + ")", Toast.LENGTH_SHORT).show();
            // Step 3 will wire this to PlotDetailActivity
        });

        binding.rvPlots.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPlots.setAdapter(adapter);

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_green);
        binding.swipeRefresh.setOnRefreshListener(this::fetchPlots);

        binding.fabAddPlot.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Register Plot opens in Step 3", Toast.LENGTH_SHORT).show();
            // Step 3 will open RegisterPlotActivity with osmdroid map
        });

        observeCachedPlots();
        fetchPlots();
    }

    private void observeCachedPlots() {
        RindaDatabase.getInstance(requireContext()).plotDao().getAllPlotsLiveData()
                .observe(getViewLifecycleOwner(), plotEntities -> {
                    if (plotEntities != null) {
                        adapter.setItems(plotEntities);
                        binding.layoutEmpty.setVisibility(plotEntities.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void fetchPlots() {
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
                        Toast.makeText(requireContext(), "Offline: Showing cached plots", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveToLocalCache(List<PlotResponse> plots) {
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
