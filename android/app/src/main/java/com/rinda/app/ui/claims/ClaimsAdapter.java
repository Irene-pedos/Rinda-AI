package com.rinda.app.ui.claims;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.rinda.app.R;
import com.rinda.app.data.local.ClaimEntity;
import com.rinda.app.databinding.ItemClaimBinding;
import com.rinda.app.util.DateTimeUtils;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClaimsAdapter extends RecyclerView.Adapter<ClaimsAdapter.ViewHolder> {

    public interface OnClaimClickListener {
        void onClaimClick(ClaimEntity claim);
    }

    private final List<ClaimEntity> items = new ArrayList<>();
    private final OnClaimClickListener listener;

    public ClaimsAdapter(OnClaimClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ClaimEntity> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemClaimBinding binding = ItemClaimBinding.inflate(
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
        private final ItemClaimBinding binding;

        ViewHolder(ItemClaimBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ClaimEntity claim) {
            Context context = itemView.getContext();

            // Plot Name
            binding.tvPlotName.setText(claim.plotName != null ? claim.plotName : ("Plot #" + claim.plotId));

            // Trigger Date
            if (claim.triggerDate != null && !claim.triggerDate.isEmpty()) {
                binding.tvTriggerDate.setText("Triggered: " + DateTimeUtils.formatDate(claim.triggerDate));
            } else {
                binding.tvTriggerDate.setText("Trigger date pending");
            }

            // Demo Badge
            binding.tvDemoBadge.setVisibility(claim.isDemo ? View.VISIBLE : View.GONE);

            // Amount: if null, show pending text (never 0!)
            if (claim.amount != null) {
                NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
                binding.tvAmount.setText(format.format(claim.amount) + " RWF");
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.rinda_green));
            } else {
                binding.tvAmount.setText("Rate pending confirmation");
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.text_secondary_light));
            }

            // Payout Rate
            if (claim.payoutRate != null) {
                double pct = claim.payoutRate.doubleValue() * 100.0;
                binding.tvPayoutRate.setVisibility(View.VISIBLE);
                binding.tvPayoutRate.setText(String.format(Locale.US, "%.0f%% payout rate", pct));
            } else {
                binding.tvPayoutRate.setVisibility(View.GONE);
            }

            // Status Chip
            bindStatusChip(claim.status);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onClaimClick(claim);
                }
            });
        }

        private void bindStatusChip(String status) {
            Context context = itemView.getContext();
            String s = status != null ? status : "UNDER_REVIEW";

            switch (s) {
                case "APPROVED":
                    binding.tvStatusChip.setText(R.string.claim_status_approved);
                    binding.tvStatusChip.setBackgroundResource(R.drawable.ic_status_healthy_bg);
                    binding.tvStatusChip.setTextColor(ContextCompat.getColor(context, R.color.status_healthy));
                    break;
                case "DECLINED":
                    binding.tvStatusChip.setText(R.string.claim_status_declined);
                    binding.tvStatusChip.setBackgroundResource(R.drawable.ic_status_at_risk_bg);
                    binding.tvStatusChip.setTextColor(ContextCompat.getColor(context, R.color.status_at_risk));
                    break;
                case "PAID":
                    binding.tvStatusChip.setText(R.string.claim_status_paid);
                    binding.tvStatusChip.setBackgroundResource(R.drawable.ic_status_healthy_bg);
                    binding.tvStatusChip.setTextColor(ContextCompat.getColor(context, R.color.rinda_green_dark));
                    break;
                case "FIELD_VISIT_REQUESTED":
                    binding.tvStatusChip.setText(R.string.claim_status_field_visit);
                    binding.tvStatusChip.setBackgroundResource(R.drawable.ic_status_watch_bg);
                    binding.tvStatusChip.setTextColor(ContextCompat.getColor(context, R.color.rinda_blue));
                    break;
                case "REVIEW_REQUESTED":
                    binding.tvStatusChip.setText(R.string.claim_status_review_requested);
                    binding.tvStatusChip.setBackgroundResource(R.drawable.ic_status_pending_bg);
                    binding.tvStatusChip.setTextColor(Color.parseColor("#7B1FA2"));
                    break;
                case "FINAL":
                    binding.tvStatusChip.setText(R.string.claim_status_final);
                    binding.tvStatusChip.setBackgroundResource(R.drawable.ic_status_pending_bg);
                    binding.tvStatusChip.setTextColor(ContextCompat.getColor(context, R.color.text_secondary_light));
                    break;
                case "UNDER_REVIEW":
                default:
                    binding.tvStatusChip.setText(R.string.claim_status_under_review);
                    binding.tvStatusChip.setBackgroundResource(R.drawable.ic_status_pending_bg);
                    binding.tvStatusChip.setTextColor(ContextCompat.getColor(context, R.color.status_pending));
                    break;
            }
        }
    }
}