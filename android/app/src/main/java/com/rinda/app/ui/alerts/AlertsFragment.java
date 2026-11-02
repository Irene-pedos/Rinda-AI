package com.rinda.app.ui.alerts;

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
import com.rinda.app.data.model.NotificationResponse;
import com.rinda.app.data.model.PageResponse;
import com.rinda.app.databinding.FragmentAlertsBinding;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlertsFragment extends Fragment {

    private FragmentAlertsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAlertsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.rvAlerts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_green);
        binding.swipeRefresh.setOnRefreshListener(this::fetchNotifications);

        RindaDatabase.getInstance(requireContext()).notificationDao().getAllNotificationsLiveData()
                .observe(getViewLifecycleOwner(), notificationEntities -> {
                    if (notificationEntities == null || notificationEntities.isEmpty()) {
                        binding.layoutEmpty.setVisibility(View.VISIBLE);
                        binding.rvAlerts.setVisibility(View.GONE);
                    } else {
                        binding.layoutEmpty.setVisibility(View.GONE);
                        binding.rvAlerts.setVisibility(View.VISIBLE);
                    }
                });

        fetchNotifications();
    }

    private void fetchNotifications() {
        binding.swipeRefresh.setRefreshing(true);
        ApiClient.getInstance(requireContext()).getService().getNotifications(0, 20)
                .enqueue(new Callback<PageResponse<NotificationResponse>>() {
                    @Override
                    public void onResponse(Call<PageResponse<NotificationResponse>> call, Response<PageResponse<NotificationResponse>> response) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                    }

                    @Override
                    public void onFailure(Call<PageResponse<NotificationResponse>> call, Throwable t) {
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
