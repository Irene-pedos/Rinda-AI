package com.rinda.app.ui.agent;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.RindaApiService;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.FarmerSummaryResponse;
import com.rinda.app.data.model.PageResponse;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.data.model.PlotStatus;
import com.rinda.app.databinding.FragmentAgentFarmersBinding;
import com.rinda.app.ui.common.EmptyState;
import com.rinda.app.ui.common.Ui;
import com.rinda.app.ui.home.HomeActivity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AgentFarmersFragment extends Fragment {

    private FragmentAgentFarmersBinding binding;
    private AgentFarmersAdapter adapter;
    private RindaApiService apiService;

    private final List<FarmerSummaryResponse> allFarmers = new ArrayList<>();
    private int currentFilter = R.id.chipAll;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAgentFarmersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        apiService = ApiClient.getInstance(requireContext()).getService();

        setupRecyclerView();
        setupFilters();
        setupActions();

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_primary);
        binding.swipeRefresh.setOnRefreshListener(this::fetchFarmers);

        fetchFarmers();
    }

    private void setupRecyclerView() {
        adapter = new AgentFarmersAdapter(farmer -> {
            // When farmer clicked, go to Plots tab to view their plots
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).selectTab(R.id.nav_plots);
            }
        });
        binding.rvFarmers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvFarmers.setAdapter(adapter);
    }

    private void setupFilters() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                currentFilter = checkedIds.get(0);
                applyFilter();
            }
        });
    }

    private void setupActions() {
        binding.cardQuickVisit.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), FieldVisitActivity.class));
        });
    }

    private void fetchFarmers() {
        binding.swipeRefresh.setRefreshing(true);

        apiService.getAgentFarmers().enqueue(new Callback<List<FarmerSummaryResponse>>() {
            @Override
            public void onResponse(Call<List<FarmerSummaryResponse>> call, Response<List<FarmerSummaryResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    binding.swipeRefresh.setRefreshing(false);
                    allFarmers.clear();
                    allFarmers.addAll(response.body());
                    applyFilter();
                } else {
                    // Fallback: derive farmer summaries from GET /plots (which returns all coop plots for AGENT)
                    fetchFromPlotsFallback();
                }
            }

            @Override
            public void onFailure(Call<List<FarmerSummaryResponse>> call, Throwable t) {
                fetchFromPlotsFallback();
            }
        });
    }

    private void fetchFromPlotsFallback() {
        apiService.getPlots(0, 100).enqueue(new Callback<PageResponse<PlotResponse>>() {
            @Override
            public void onResponse(Call<PageResponse<PlotResponse>> call, Response<PageResponse<PlotResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    processPlotList(response.body().getItems());
                } else {
                    loadFromLocalCache();
                }
            }

            @Override
            public void onFailure(Call<PageResponse<PlotResponse>> call, Throwable t) {
                loadFromLocalCache();
            }
        });
    }

    private void loadFromLocalCache() {
        new Thread(() -> {
            List<PlotEntity> localPlots = RindaDatabase.getInstance(requireContext()).plotDao().getAllPlots();
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                binding.swipeRefresh.setRefreshing(false);
                processLocalPlots(localPlots);
            });
        }).start();
    }

    private void processPlotList(List<PlotResponse> plotList) {
        if (binding == null) return;
        binding.swipeRefresh.setRefreshing(false);

        Map<Long, FarmerSummaryResponse> map = new HashMap<>();
        if (plotList != null) {
            for (PlotResponse p : plotList) {
                long farmerId = p.getFarmerId() > 0 ? p.getFarmerId() : p.getId();
                FarmerSummaryResponse summary = map.get(farmerId);
                if (summary == null) {
                    summary = createFarmerSummary(farmerId, "Farmer #" + farmerId, "+250 780 000 00" + (farmerId % 10));
                    map.put(farmerId, summary);
                }
                incrementPlots(summary);
                if (p.getStatus() == PlotStatus.PENDING_VERIFICATION) {
                    incrementPending(summary);
                }
            }
        }

        allFarmers.clear();
        allFarmers.addAll(map.values());
        applyFilter();
    }

    private void processLocalPlots(List<PlotEntity> localPlots) {
        Map<Long, FarmerSummaryResponse> map = new HashMap<>();
        if (localPlots != null) {
            for (PlotEntity p : localPlots) {
                long farmerId = p.farmerId > 0 ? p.farmerId : p.id;
                FarmerSummaryResponse summary = map.get(farmerId);
                if (summary == null) {
                    summary = createFarmerSummary(farmerId, "Farmer #" + farmerId, "+250 780 000 00" + (farmerId % 10));
                    map.put(farmerId, summary);
                }
                incrementPlots(summary);
                if (p.status == PlotStatus.PENDING_VERIFICATION) {
                    incrementPending(summary);
                }
            }
        }
        allFarmers.clear();
        allFarmers.addAll(map.values());
        applyFilter();
    }

    private void applyFilter() {
        if (binding == null) return;
        List<FarmerSummaryResponse> filtered = new ArrayList<>();

        int totalPending = 0;
        int totalAtRisk = 0;

        for (FarmerSummaryResponse f : allFarmers) {
            if (f.getPendingVerificationCount() > 0) totalPending += f.getPendingVerificationCount();
            if (f.getAtRiskCount() > 0) totalAtRisk += f.getAtRiskCount();

            if (currentFilter == R.id.chipPending) {
                if (f.getPendingVerificationCount() > 0) {
                    filtered.add(f);
                }
            } else if (currentFilter == R.id.chipAtRisk) {
                if (f.getAtRiskCount() > 0) {
                    filtered.add(f);
                }
            } else {
                filtered.add(f);
            }
        }

        binding.chipPending.setText(getString(R.string.agent_filter_pending) + " (" + totalPending + ")");
        binding.chipAtRisk.setText(getString(R.string.agent_filter_at_risk) + " (" + totalAtRisk + ")");

        adapter.setItems(filtered);

        if (filtered.isEmpty()) {
            binding.rvFarmers.setVisibility(View.GONE);
            binding.layoutEmpty.getRoot().setVisibility(View.VISIBLE);
            EmptyState.bind(binding.layoutEmpty, R.drawable.ic_group,
                    R.string.agent_farmers_title, R.string.agent_farmers_empty, 0, null);
        } else {
            binding.rvFarmers.setVisibility(View.VISIBLE);
            binding.layoutEmpty.getRoot().setVisibility(View.GONE);
        }
    }

    private static FarmerSummaryResponse createFarmerSummary(long id, String name, String phone) {
        FarmerSummaryResponse s = new FarmerSummaryResponse();
        setField(s, "farmerId", id);
        setField(s, "fullName", name);
        setField(s, "phone", phone);
        return s;
    }

    private static void incrementPlots(FarmerSummaryResponse s) {
        setField(s, "plotsCount", s.getPlotsCount() + 1);
    }

    private static void incrementPending(FarmerSummaryResponse s) {
        setField(s, "pendingVerificationCount", s.getPendingVerificationCount() + 1);
    }

    private static void setField(Object target, String fieldName, Object val) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, val);
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
