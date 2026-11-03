package com.rinda.app.ui.plots;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rinda.app.R;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.databinding.ItemPlotCardBinding;
import com.rinda.app.ui.common.StatusUi;
import com.rinda.app.util.DateTimeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            binding.tvPlotName.setText(plot.name != null ? plot.name : context.getString(R.string.plot_number, plot.id));
            binding.tvPlotDemoBadge.setVisibility(plot.isDemo ? View.VISIBLE : View.GONE);

            double area = plot.areaHa != null ? plot.areaHa.doubleValue() : 0.0;
            binding.tvArea.setText(String.format(Locale.getDefault(), context.getString(R.string.plot_area), area));

            if (plot.plantingDate != null && !plot.plantingDate.isEmpty()) {
                binding.tvPlantingDate.setText(context.getString(R.string.plot_planted_on, DateTimeUtils.formatDate(plot.plantingDate)));
            } else {
                binding.tvPlantingDate.setText(R.string.plot_planting_unconfirmed);
            }

            // Verification status: icon + word (never colour alone)
            StatusUi.Spec spec = StatusUi.forPlot(plot.status);
            StatusUi.applyPill(binding.tvStatusWord, spec);
            binding.ivPlotBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(context, spec.bgColorRes)));
            binding.ivPlotBadge.setImageTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(context, spec.fgColorRes)));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlotClick(plot);
                }
            });
        }
    }
}
