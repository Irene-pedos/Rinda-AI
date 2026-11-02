package com.rinda.app.ui.plots;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.rinda.app.R;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.model.PlotStatus;
import com.rinda.app.databinding.ItemPlotCardBinding;
import java.util.ArrayList;
import java.util.List;

public class PlotsAdapter extends RecyclerView.Adapter<PlotsAdapter.PlotViewHolder> {

    public interface OnPlotClickListener {
        void onPlotClick(PlotEntity plot);
    }

    private final List<PlotEntity> items = new ArrayList<>();
    private final OnPlotClickListener listener;

    public PlotsAdapter(OnPlotClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<PlotEntity> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlotCardBinding binding = ItemPlotCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PlotViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlotViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class PlotViewHolder extends RecyclerView.ViewHolder {
        private final ItemPlotCardBinding binding;

        PlotViewHolder(ItemPlotCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PlotEntity plot) {
            Context context = itemView.getContext();
            binding.tvPlotName.setText(plot.name);

            // Demo label per ZERO-MISMATCH RULES
            binding.tvPlotDemoBadge.setVisibility(plot.isDemo ? View.VISIBLE : View.GONE);

            // Area
            double area = plot.areaHa != null ? plot.areaHa.doubleValue() : 0.0;
            binding.tvArea.setText(String.format("%.2f ha", area));

            // Crop (always Maize)
            binding.tvCrop.setText(plot.crop != null ? plot.crop : "MAIZE");

            // Planting date
            if (plot.plantingDate != null && !plot.plantingDate.isEmpty()) {
                binding.tvPlantingDate.setText("Planted: " + plot.plantingDate);
            } else {
                binding.tvPlantingDate.setText("Date unconfirmed");
            }

            // Status chip: Never colour alone!
            PlotStatus status = plot.status != null ? plot.status : PlotStatus.PENDING_VERIFICATION;
            switch (status) {
                case ACTIVE:
                    binding.layoutStatusChip.setBackgroundResource(R.drawable.ic_status_healthy_bg);
                    binding.ivStatusIcon.setImageResource(R.drawable.ic_status_healthy);
                    binding.tvStatusWord.setText(R.string.plot_status_active);
                    binding.tvStatusWord.setTextColor(ContextCompat.getColor(context, R.color.status_healthy));
                    break;
                case REJECTED:
                    binding.layoutStatusChip.setBackgroundResource(R.drawable.ic_status_at_risk_bg);
                    binding.ivStatusIcon.setImageResource(R.drawable.ic_status_at_risk);
                    binding.tvStatusWord.setText(R.string.plot_status_rejected);
                    binding.tvStatusWord.setTextColor(ContextCompat.getColor(context, R.color.status_at_risk));
                    break;
                case ARCHIVED:
                    binding.layoutStatusChip.setBackgroundResource(R.drawable.ic_status_watch_bg);
                    binding.ivStatusIcon.setImageResource(R.drawable.ic_status_watch);
                    binding.tvStatusWord.setText(R.string.plot_status_archived);
                    binding.tvStatusWord.setTextColor(ContextCompat.getColor(context, R.color.status_watch));
                    break;
                case PENDING_VERIFICATION:
                default:
                    binding.layoutStatusChip.setBackgroundResource(R.drawable.ic_status_pending_bg);
                    binding.ivStatusIcon.setImageResource(R.drawable.ic_status_pending);
                    binding.tvStatusWord.setText(R.string.plot_status_pending);
                    binding.tvStatusWord.setTextColor(ContextCompat.getColor(context, R.color.status_pending));
                    break;
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlotClick(plot);
                }
            });
        }
    }
}
