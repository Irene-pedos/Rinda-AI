package com.rinda.app.ui.alerts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rinda.app.R;
import com.rinda.app.data.local.NotificationEntity;
import com.rinda.app.databinding.ItemNotificationBinding;
import com.rinda.app.util.DateTimeUtils;
import java.util.ArrayList;
import java.util.List;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(NotificationEntity item);
    }

    private final List<NotificationEntity> items = new ArrayList<>();
    private final OnNotificationClickListener listener;

    public NotificationsAdapter(OnNotificationClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<NotificationEntity> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNotificationBinding binding = ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemNotificationBinding binding;

        ViewHolder(ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(NotificationEntity item) {
            // Unread dot and style
            binding.vUnreadDot.setVisibility(item.isRead ? View.GONE : View.VISIBLE);
            binding.tvTitle.setText(getLocalizedTitle(item));
            binding.tvBody.setText(item.body != null ? item.body : "");

            // Relative time
            binding.tvTime.setText(DateTimeUtils.formatRelativeTime(item.createdAt));

            // Plot Name Chip
            if (item.plotName != null && !item.plotName.isEmpty()) {
                binding.tvPlotName.setVisibility(View.VISIBLE);
                binding.tvPlotName.setText(item.plotName);
            } else if (item.plotId != null && item.plotId > 0) {
                binding.tvPlotName.setVisibility(View.VISIBLE);
                binding.tvPlotName.setText("Plot #" + item.plotId);
            } else {
                binding.tvPlotName.setVisibility(View.GONE);
            }

            // Demo Badge
            binding.tvDemoBadge.setVisibility(item.isDemo ? View.VISIBLE : View.GONE);

            // Icon & Background based on type / code
            String type = item.type != null ? item.type : "SYSTEM";
            switch (type) {
                case "HEALTH":
                    if ("HEALTH_AT_RISK".equalsIgnoreCase(item.code)) {
                        binding.layoutIconContainer.setBackgroundResource(R.drawable.ic_status_at_risk_bg);
                        binding.ivTypeIcon.setImageResource(R.drawable.ic_status_at_risk);
                    } else if ("HEALTH_WATCH".equalsIgnoreCase(item.code)) {
                        binding.layoutIconContainer.setBackgroundResource(R.drawable.ic_status_watch_bg);
                        binding.ivTypeIcon.setImageResource(R.drawable.ic_status_watch);
                    } else {
                        binding.layoutIconContainer.setBackgroundResource(R.drawable.ic_status_healthy_bg);
                        binding.ivTypeIcon.setImageResource(R.drawable.ic_status_healthy);
                    }
                    break;
                case "CLAIM":
                    binding.layoutIconContainer.setBackgroundResource(R.drawable.ic_status_pending_bg);
                    binding.ivTypeIcon.setImageResource(R.drawable.ic_nav_claims);
                    break;
                case "SYSTEM":
                default:
                    binding.layoutIconContainer.setBackgroundResource(R.drawable.ic_status_pending_bg);
                    binding.ivTypeIcon.setImageResource(R.drawable.ic_nav_alerts);
                    break;
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificationClick(item);
                }
            });
        }

        private String getLocalizedTitle(NotificationEntity item) {
            if ("HEALTH_AT_RISK".equalsIgnoreCase(item.code)) {
                return itemView.getContext().getString(R.string.health_at_risk) + " Alert";
            } else if ("HEALTH_WATCH".equalsIgnoreCase(item.code)) {
                return itemView.getContext().getString(R.string.health_watch) + " Alert";
            } else if ("HEALTH_RECOVERED".equalsIgnoreCase(item.code)) {
                return itemView.getContext().getString(R.string.health_healthy) + " Notice";
            }
            return item.title != null ? item.title : "Notification";
        }
    }
}