package com.rinda.app.ui.agent;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rinda.app.R;
import com.rinda.app.data.model.FarmerSummaryResponse;
import com.rinda.app.databinding.ItemAgentFarmerBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AgentFarmersAdapter extends RecyclerView.Adapter<AgentFarmersAdapter.ViewHolder> {

    public interface OnFarmerClickListener {
        void onFarmerClick(FarmerSummaryResponse farmer);
    }

    private final List<FarmerSummaryResponse> items = new ArrayList<>();
    private final OnFarmerClickListener listener;

    public AgentFarmersAdapter(OnFarmerClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<FarmerSummaryResponse> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAgentFarmerBinding binding = ItemAgentFarmerBinding.inflate(
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
        private final ItemAgentFarmerBinding binding;

        ViewHolder(ItemAgentFarmerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FarmerSummaryResponse farmer) {
            binding.tvFarmerName.setText(farmer.getFullName() != null ? farmer.getFullName() : "Farmer");
            binding.tvFarmerPhone.setText(farmer.getPhone() != null ? farmer.getPhone() : "");
            binding.tvAvatarInitials.setText(initials(farmer.getFullName()));

            // Plots count
            binding.tagPlotsCount.setText(itemView.getContext().getString(R.string.agent_farmer_plots_fmt, farmer.getPlotsCount()));

            // Pending verification count
            if (farmer.getPendingVerificationCount() > 0) {
                binding.tagPendingCount.setVisibility(View.VISIBLE);
                binding.tagPendingCount.setText(itemView.getContext().getString(
                        R.string.agent_farmer_pending_tag, farmer.getPendingVerificationCount()
                ));
            } else {
                binding.tagPendingCount.setVisibility(View.GONE);
            }

            // At risk count
            if (farmer.getAtRiskCount() > 0) {
                binding.tagAtRiskCount.setVisibility(View.VISIBLE);
                binding.tagAtRiskCount.setText(itemView.getContext().getString(
                        R.string.agent_farmer_at_risk_tag, farmer.getAtRiskCount()
                ));
            } else {
                binding.tagAtRiskCount.setVisibility(View.GONE);
            }

            binding.cardFarmer.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFarmerClick(farmer);
                }
            });
        }

        private String initials(String name) {
            if (name == null || name.trim().isEmpty()) return "F";
            String[] parts = name.trim().split("\\s+");
            String first = parts[0].substring(0, 1);
            String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
            return (first + last).toUpperCase(Locale.getDefault());
        }
    }
}
