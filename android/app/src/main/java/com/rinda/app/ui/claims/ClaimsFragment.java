package com.rinda.app.ui.claims;

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
import com.rinda.app.data.local.ClaimEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.ClaimResponse;
import com.rinda.app.data.model.PageResponse;
import com.rinda.app.databinding.FragmentClaimsBinding;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ClaimsFragment extends Fragment {

    private FragmentClaimsBinding binding;
    private ClaimsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentClaimsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new ClaimsAdapter(this::onClaimClicked);
        binding.rvClaims.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvClaims.setAdapter(adapter);

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_green);
        binding.swipeRefresh.setOnRefreshListener(this::fetchClaims);

        observeCachedClaims();
        fetchClaims();
    }

    private void observeCachedClaims() {
        RindaDatabase.getInstance(requireContext()).claimDao().getAllClaimsLiveData()
                .observe(getViewLifecycleOwner(), claimEntities -> {
                    if (claimEntities == null || claimEntities.isEmpty()) {
                        binding.layoutEmpty.setVisibility(View.VISIBLE);
                        binding.rvClaims.setVisibility(View.GONE);
                    } else {
                        binding.layoutEmpty.setVisibility(View.GONE);
                        binding.rvClaims.setVisibility(View.VISIBLE);
                        adapter.setItems(claimEntities);
                    }
                });
    }

    private void fetchClaims() {
        binding.swipeRefresh.setRefreshing(true);
        ApiClient.getInstance(requireContext()).getService().getClaims(0, 50)
                .enqueue(new Callback<PageResponse<ClaimResponse>>() {
                    @Override
                    public void onResponse(Call<PageResponse<ClaimResponse>> call, Response<PageResponse<ClaimResponse>> response) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            saveClaimsToCache(response.body().getItems());
                        }
                    }

                    @Override
                    public void onFailure(Call<PageResponse<ClaimResponse>> call, Throwable t) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                    }
                });
    }

    private void saveClaimsToCache(List<ClaimResponse> list) {
        if (list == null) return;
        new Thread(() -> {
            List<ClaimEntity> entities = new ArrayList<>();
            for (ClaimResponse r : list) {
                ClaimEntity e = new ClaimEntity();
                e.id = r.getId();
                e.plotId = r.getPlotId();
                e.plotName = r.getPlotName();
                e.seasonId = r.getSeasonId();
                e.triggerDate = r.getTriggerDate();
                e.status = r.getStatus();
                e.payoutRate = r.getPayoutRate();
                e.amount = r.getAmount();
                e.evidenceSnapshotJson = r.getEvidenceSnapshot() != null ? r.getEvidenceSnapshot().toString() : null;
                if (r.getDecision() != null) {
                    e.officerDecision = r.getDecision().getDecision();
                    e.officerReason = r.getDecision().getReason();
                }
                e.modelVersion = r.getModelVersion();
                e.isDemo = r.isDemo();
                e.createdAt = r.getCreatedAt();
                e.cachedAt = System.currentTimeMillis();
                entities.add(e);
            }
            RindaDatabase.getInstance(requireContext()).claimDao().insertAll(entities);
        }).start();
    }

    private void onClaimClicked(ClaimEntity claim) {
        Intent intent = new Intent(requireContext(), ClaimDetailActivity.class);
        intent.putExtra(ClaimDetailActivity.EXTRA_CLAIM_ID, claim.id);
        intent.putExtra(ClaimDetailActivity.EXTRA_PLOT_NAME, claim.plotName);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}