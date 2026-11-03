package com.rinda.app.ui.alerts;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
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
import com.rinda.app.data.local.NotificationEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.NotificationResponse;
import com.rinda.app.data.model.PageResponse;
import com.rinda.app.databinding.FragmentAlertsBinding;
import com.rinda.app.ui.claims.ClaimDetailActivity;
import com.rinda.app.ui.plots.PlotDetailActivity;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlertsFragment extends Fragment {

    private FragmentAlertsBinding binding;
    private NotificationsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAlertsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new NotificationsAdapter(this::onNotificationClicked);
        binding.rvAlerts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAlerts.setAdapter(adapter);

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_green);
        binding.swipeRefresh.setOnRefreshListener(this::fetchNotifications);

        observeCachedNotifications();
        fetchNotifications();
    }

    private void observeCachedNotifications() {
        RindaDatabase.getInstance(requireContext()).notificationDao().getAllNotificationsLiveData()
                .observe(getViewLifecycleOwner(), notificationEntities -> {
                    if (notificationEntities == null || notificationEntities.isEmpty()) {
                        binding.layoutEmpty.setVisibility(View.VISIBLE);
                        binding.rvAlerts.setVisibility(View.GONE);
                    } else {
                        binding.layoutEmpty.setVisibility(View.GONE);
                        binding.rvAlerts.setVisibility(View.VISIBLE);
                        adapter.setItems(notificationEntities);
                    }
                });
    }

    private void fetchNotifications() {
        binding.swipeRefresh.setRefreshing(true);
        ApiClient.getInstance(requireContext()).getService().getNotifications(0, 50)
                .enqueue(new Callback<PageResponse<NotificationResponse>>() {
                    @Override
                    public void onResponse(Call<PageResponse<NotificationResponse>> call, Response<PageResponse<NotificationResponse>> response) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            saveNotificationsToCache(response.body().getItems());
                        }
                    }

                    @Override
                    public void onFailure(Call<PageResponse<NotificationResponse>> call, Throwable t) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                    }
                });
    }

    private void saveNotificationsToCache(List<NotificationResponse> list) {
        if (list == null) return;
        new Thread(() -> {
            List<NotificationEntity> entities = new ArrayList<>();
            for (NotificationResponse r : list) {
                NotificationEntity e = new NotificationEntity();
                e.id = r.getId();
                e.type = r.getType();
                e.code = r.getCode();
                e.title = r.getTitle();
                e.body = r.getBody();
                e.plotId = r.getPlotId();
                e.plotName = r.getPlotName();
                e.link = r.getLink();
                e.isRead = r.isRead();
                e.isDemo = r.isDemo();
                e.createdAt = r.getCreatedAt();
                e.cachedAt = System.currentTimeMillis();
                entities.add(e);
            }
            RindaDatabase.getInstance(requireContext()).notificationDao().insertAll(entities);
        }).start();
    }

    private void onNotificationClicked(NotificationEntity item) {
        // 1. Mark read on backend and locally
        if (!item.isRead) {
            ApiClient.getInstance(requireContext()).getService().markNotificationRead(item.id)
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                        }
                    });
            new Thread(() -> RindaDatabase.getInstance(requireContext()).notificationDao().markAsRead(item.id)).start();
        }

        // 2. Route based on type
        String type = item.type != null ? item.type : "SYSTEM";
        if ("HEALTH".equalsIgnoreCase(type)) {
            long plotId = item.plotId != null ? item.plotId : parsePlotIdFromLink(item.link);
            if (plotId > 0) {
                Intent intent = new Intent(requireContext(), PlotDetailActivity.class);
                intent.putExtra(PlotDetailActivity.EXTRA_PLOT_ID, plotId);
                intent.putExtra(PlotDetailActivity.EXTRA_PLOT_NAME, item.plotName);
                startActivity(intent);
            }
        } else if ("CLAIM".equalsIgnoreCase(type)) {
            long claimId = parseClaimIdFromLink(item.link);
            Intent intent = new Intent(requireContext(), ClaimDetailActivity.class);
            intent.putExtra(ClaimDetailActivity.EXTRA_CLAIM_ID, claimId);
            intent.putExtra(ClaimDetailActivity.EXTRA_PLOT_NAME, item.plotName);
            startActivity(intent);
        } else {
            // SYSTEM notice dialog
            new AlertDialog.Builder(requireContext())
                    .setTitle(item.title != null ? item.title : "Notice")
                    .setMessage(item.body != null ? item.body : "")
                    .setPositiveButton(R.string.btn_done, null)
                    .show();
        }
    }

    private long parsePlotIdFromLink(String link) {
        if (link == null) return -1;
        try {
            // e.g. "/plots/9001"
            String cleaned = link.replace("/plots/", "").replace("/", "").trim();
            return Long.parseLong(cleaned);
        } catch (Exception e) {
            return -1;
        }
    }

    private long parseClaimIdFromLink(String link) {
        if (link == null) return -1;
        try {
            // e.g. "/claims/101"
            String cleaned = link.replace("/claims/", "").replace("/", "").trim();
            return Long.parseLong(cleaned);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}