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
import com.rinda.app.ui.common.EmptyState;
import com.rinda.app.ui.common.InfoSheet;
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

        EmptyState.bind(binding.layoutEmpty, R.drawable.ic_shield, R.string.empty_claims_title, R.string.empty_claims);
        binding.cardClaimsInfo.setOnClickListener(v ->
                InfoSheet.show(requireContext(), R.drawable.ic_shield, R.string.help_claims_title, R.string.help_claims_body));

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_primary);
        binding.swipeRefresh.setOnRefreshListener(this::fetchClaims);
        // The list is not the direct child of the refresh layout, so tell it when the list can scroll up.
        binding.swipeRefresh.setOnChildScrollUpCallback((parent, child) -> binding.rvClaims.canScrollVertically(-1));

        observeCachedClaims();
        fetchClaims();
    }

    private void observeCachedClaims() {
        RindaDatabase.getInstance(requireContext()).claimDao().getAllClaimsLiveData()
                .observe(getViewLifecycleOwner(), list -> {
                    if (binding == null) return;
                    boolean empty = list == null || list.isEmpty();
                    EmptyState.show(binding.layoutEmpty, empty);
                    binding.rvClaims.setVisibility(empty ? View.GONE : View.VISIBLE);
                    if (!empty) {
                        boolean first = adapter.getItemCount() == 0;
                        adapter.setItems(list);
                        if (first) {
                            binding.rvClaims.scheduleLayoutAnimation();
                        }
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
        final android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            List<ClaimEntity> entities = new ArrayList<>();
            for (ClaimResponse r : list) {
                entities.add(ClaimDetailActivity.toEntity(r));
            }
            RindaDatabase.getInstance(app).claimDao().insertAll(entities);
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
