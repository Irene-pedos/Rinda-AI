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
import com.rinda.app.data.local.NotificationEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.NotificationResponse;
import com.rinda.app.data.model.PageResponse;
import com.rinda.app.databinding.FragmentAlertsBinding;
import com.rinda.app.ui.common.EmptyState;
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

        adapter = new NotificationsAdapter(item -> AlertRouter.open(requireContext(), item));
        binding.rvAlerts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAlerts.setAdapter(adapter);

        EmptyState.bind(binding.layoutEmpty, R.drawable.ic_satellite, R.string.empty_alerts_title, R.string.empty_alerts);

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_primary);
        binding.swipeRefresh.setOnRefreshListener(this::fetchNotifications);

        observeCachedNotifications();
        fetchNotifications();
    }

    private void observeCachedNotifications() {
        RindaDatabase.getInstance(requireContext()).notificationDao().getAllNotificationsLiveData()
                .observe(getViewLifecycleOwner(), list -> {
                    if (binding == null) return;
                    boolean empty = list == null || list.isEmpty();
                    EmptyState.show(binding.layoutEmpty, empty);
                    binding.rvAlerts.setVisibility(empty ? View.GONE : View.VISIBLE);
                    if (!empty) {
                        boolean first = adapter.getItemCount() == 0;
                        adapter.setItems(list);
                        if (first) {
                            binding.rvAlerts.scheduleLayoutAnimation();
                        }
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
        final android.content.Context app = requireContext().getApplicationContext();
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
            RindaDatabase.getInstance(app).notificationDao().insertAll(entities);
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
