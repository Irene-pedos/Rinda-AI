package com.rinda.app.ui.claims;

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
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.ClaimResponse;
import com.rinda.app.data.model.PageResponse;
import com.rinda.app.databinding.FragmentClaimsBinding;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ClaimsFragment extends Fragment {

    private FragmentClaimsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentClaimsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.rvClaims.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_green);
        binding.swipeRefresh.setOnRefreshListener(this::fetchClaims);

        RindaDatabase.getInstance(requireContext()).claimDao().getAllClaimsLiveData()
                .observe(getViewLifecycleOwner(), claimEntities -> {
                    if (claimEntities == null || claimEntities.isEmpty()) {
                        binding.layoutEmpty.setVisibility(View.VISIBLE);
                        binding.rvClaims.setVisibility(View.GONE);
                    } else {
                        binding.layoutEmpty.setVisibility(View.GONE);
                        binding.rvClaims.setVisibility(View.VISIBLE);
                    }
                });

        fetchClaims();
    }

    private void fetchClaims() {
        binding.swipeRefresh.setRefreshing(true);
        ApiClient.getInstance(requireContext()).getService().getClaims(0, 20)
                .enqueue(new Callback<PageResponse<ClaimResponse>>() {
                    @Override
                    public void onResponse(Call<PageResponse<ClaimResponse>> call, Response<PageResponse<ClaimResponse>> response) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                    }

                    @Override
                    public void onFailure(Call<PageResponse<ClaimResponse>> call, Throwable t) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
