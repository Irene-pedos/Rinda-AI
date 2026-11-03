package com.rinda.app.ui.alerts;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rinda.app.R;
import com.rinda.app.data.local.NotificationEntity;
import com.rinda.app.databinding.ItemNotificationBinding;
import com.rinda.app.ui.common.StatusUi;
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
        return new ViewHolder(ItemNotificationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
            Context context = itemView.getContext();

            // Unread: dot + bold title (not colour alone)
            binding.vUnreadDot.setVisibility(item.isRead ? View.GONE : View.VISIBLE);
            binding.tvTitle.setTypeface(null, item.isRead ? Typeface.NORMAL : Typeface.BOLD);
            binding.tvTitle.setText(AlertRouter.title(context, item));
            binding.tvBody.setText(item.body != null ? item.body : "");
            binding.tvBody.setVisibility(item.body != null && !item.body.isEmpty() ? View.VISIBLE : View.GONE);
            binding.tvTime.setText(DateTimeUtils.formatRelativeTime(item.createdAt));

            if (item.plotName != null && !item.plotName.isEmpty()) {
                binding.tvPlotName.setVisibility(View.VISIBLE);
                binding.tvPlotName.setText(item.plotName);
            } else if (item.plotId != null && item.plotId > 0) {
                binding.tvPlotName.setVisibility(View.VISIBLE);
                binding.tvPlotName.setText(context.getString(R.string.plot_number, item.plotId));
            } else {
                binding.tvPlotName.setVisibility(View.GONE);
            }

            binding.tvDemoBadge.setVisibility(item.isDemo ? View.VISIBLE : View.GONE);

            StatusUi.Spec look = AlertRouter.look(item);
            StatusUi.applyBadge(binding.ivTypeIcon, look);
            binding.ivTypeIcon.setImageResource(AlertRouter.icon(item));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificationClick(item);
                }
            });
        }
    }
}
