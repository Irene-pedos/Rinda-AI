package com.rinda.app.ui.claims;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.rinda.app.R;
import com.rinda.app.data.local.ClaimEntity;
import com.rinda.app.databinding.ItemClaimBinding;
import com.rinda.app.ui.common.StatusUi;
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
        return new ViewHolder(ItemClaimBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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

            binding.tvPlotName.setText(claim.plotName != null ? claim.plotName : context.getString(R.string.plot_number, claim.plotId));

            if (claim.triggerDate != null && !claim.triggerDate.isEmpty()) {
                binding.tvTriggerDate.setText(context.getString(R.string.claim_triggered_on, DateTimeUtils.formatDate(claim.triggerDate)));
            } else {
                binding.tvTriggerDate.setText(R.string.claim_trigger_date_pending);
            }

            binding.tvDemoBadge.setVisibility(claim.isDemo ? View.VISIBLE : View.GONE);

            // Amount: only what the server says; null means "not decided", never 0 (spec §0 rule 2)
            if (claim.amount != null) {
                binding.tvAmount.setText(context.getString(R.string.claim_amount_rwf,
                        NumberFormat.getNumberInstance(Locale.getDefault()).format(claim.amount)));
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.rinda_text_primary));
            } else {
                binding.tvAmount.setText(R.string.claim_amount_pending);
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.rinda_text_secondary));
            }

            if (claim.payoutRate != null) {
                binding.tvPayoutRate.setText(context.getString(R.string.claim_rate, claim.payoutRate.doubleValue() * 100.0));
                binding.tvPayoutRate.setVisibility(View.VISIBLE);
            } else {
                binding.tvPayoutRate.setVisibility(View.GONE);
            }

            StatusUi.applyPill(binding.tvStatusChip, StatusUi.forClaim(claim.status));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onClaimClick(claim);
                }
            });
        }
    }
}
