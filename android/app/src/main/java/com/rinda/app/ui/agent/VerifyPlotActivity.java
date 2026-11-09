package com.rinda.app.ui.agent;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.RindaApiService;
import com.rinda.app.data.local.OutboxEntity;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.data.model.PlotStatus;
import com.rinda.app.data.model.PlotVerifyRequest;
import com.rinda.app.databinding.ActivityVerifyPlotBinding;
import com.rinda.app.databinding.DialogTextInputBinding;
import com.rinda.app.ui.common.StatusUi;
import com.rinda.app.ui.common.Ui;
import com.rinda.app.util.DateTimeUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polygon;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VerifyPlotActivity extends AppCompatActivity {

    public static final String EXTRA_PLOT_ID = "extra_plot_id";

    private ActivityVerifyPlotBinding binding;
    private RindaApiService apiService;
    private long plotId;
    private PlotEntity currentPlot;
    private String selectedPlantingDate;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        binding = ActivityVerifyPlotBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getInstance(this).getService();
        plotId = getIntent().getLongExtra(EXTRA_PLOT_ID, -1);

        if (plotId <= 0) {
            finish();
            return;
        }

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupMap();
        setupActions();
        loadPlot();
    }

    private void setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK);
        binding.mapView.setMultiTouchControls(true);
        binding.mapView.setBuiltInZoomControls(false);
    }

    private void setupActions() {
        binding.btnChangeDate.setOnClickListener(v -> showDatePicker());
        binding.btnVerify.setOnClickListener(v -> confirmVerification());
        binding.btnReject.setOnClickListener(v -> promptRejectionReason());
    }

    private void loadPlot() {
        // First observe/load from local cache
        new Thread(() -> {
            PlotEntity entity = RindaDatabase.getInstance(this).plotDao().getPlotById(plotId);
            runOnUiThread(() -> {
                if (entity != null) {
                    currentPlot = entity;
                    displayPlot(entity);
                }
            });
        }).start();

        // Also refresh from API
        apiService.getPlot(plotId).enqueue(new Callback<PlotResponse>() {
            @Override
            public void onResponse(Call<PlotResponse> call, Response<PlotResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    PlotResponse p = response.body();
                    PlotEntity entity = new PlotEntity();
                    entity.id = p.getId();
                    entity.farmerId = p.getFarmerId();
                    entity.name = p.getName();
                    entity.crop = p.getCrop();
                    entity.boundary = p.getBoundary();
                    entity.areaHa = p.getAreaHa();
                    entity.plantingDate = p.getPlantingDate();
                    entity.status = p.getStatus();
                    entity.dataStatus = p.getDataStatus();
                    entity.isDemo = p.isDemo();
                    entity.createdAt = p.getCreatedAt();
                    entity.updatedAt = p.getUpdatedAt();
                    entity.cachedAt = System.currentTimeMillis();
                    currentPlot = entity;
                    displayPlot(entity);
                }
            }

            @Override
            public void onFailure(Call<PlotResponse> call, Throwable t) {}
        });
    }

    private void displayPlot(PlotEntity plot) {
        if (binding == null) return;

        binding.tvPlotName.setText(plot.name);
        double area = plot.areaHa != null ? plot.areaHa.doubleValue() : 0.0;
        binding.tvPlotArea.setText(String.format(Locale.US, "Area: %.2f ha • Maize", area));

        StatusUi.Spec spec = StatusUi.forPlot(plot.status);
        StatusUi.applyPill(binding.tvStatusTag, spec);

        selectedPlantingDate = plot.plantingDate != null ? plot.plantingDate : dateFormat.format(Calendar.getInstance().getTime());
        binding.tvPlantingDate.setText(DateTimeUtils.formatDate(selectedPlantingDate));

        renderPolygonOnMap(plot);
    }

    private void renderPolygonOnMap(PlotEntity plot) {
        if (plot.boundary == null || plot.boundary.getCoordinates() == null || plot.boundary.getCoordinates().isEmpty()) {
            return;
        }

        binding.mapView.getOverlays().clear();

        List<List<Double>> ring = plot.boundary.getCoordinates().get(0);
        List<GeoPoint> geoPoints = new ArrayList<>();
        for (List<Double> coord : ring) {
            if (coord.size() >= 2) {
                // GeoJSON: [lon, lat] -> osmdroid: GeoPoint(lat, lon)
                double lon = coord.get(0);
                double lat = coord.get(1);
                geoPoints.add(new GeoPoint(lat, lon));
            }
        }

        if (!geoPoints.isEmpty()) {
            Polygon polygon = new Polygon(binding.mapView);
            polygon.setPoints(geoPoints);
            polygon.setStrokeColor(ContextCompat.getColor(this, R.color.rinda_primary));
            polygon.setStrokeWidth(4.0f);
            polygon.setFillColor(Color.argb(55, 3, 140, 72));
            binding.mapView.getOverlays().add(polygon);

            BoundingBox box = BoundingBox.fromGeoPoints(geoPoints);
            binding.mapView.post(() -> {
                try {
                    binding.mapView.zoomToBoundingBox(box.increaseByScale(1.4f), false);
                } catch (Exception ignored) {}
            });
        }
        binding.mapView.invalidate();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        if (selectedPlantingDate != null) {
            try {
                cal.setTime(dateFormat.parse(selectedPlantingDate));
            } catch (Exception ignored) {}
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, dayOfMonth);
                    if (picked.after(Calendar.getInstance())) {
                        Ui.toast(binding.getRoot(), R.string.error_planting_date_invalid);
                        return;
                    }
                    selectedPlantingDate = dateFormat.format(picked.getTime());
                    binding.tvPlantingDate.setText(DateTimeUtils.formatDate(selectedPlantingDate));
                    Ui.toast(binding.getRoot(), R.string.planting_date_updated);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void confirmVerification() {
        Ui.dialog(this)
                .setTitle(R.string.verify_plot_dialog_title)
                .setMessage(R.string.verify_plot_dialog_msg)
                .setPositiveButton(R.string.btn_approve_plot, (dialog, which) -> submitDecision("ACTIVE", null))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void promptRejectionReason() {
        DialogTextInputBinding inputBinding = DialogTextInputBinding.inflate(getLayoutInflater());
        inputBinding.tilInput.setHint(getString(R.string.reject_plot_reason_hint));

        Ui.dialog(this)
                .setTitle(R.string.reject_plot_dialog_title)
                .setMessage(R.string.reject_plot_dialog_msg)
                .setView(inputBinding.getRoot())
                .setPositiveButton(R.string.btn_reject_plot, (dialog, which) -> {
                    String reason = inputBinding.etInput.getText() != null ? inputBinding.etInput.getText().toString().trim() : "";
                    if (reason.isEmpty()) {
                        reason = "Boundary coordinates invalid or overlapping obstacles";
                    }
                    submitDecision("REJECTED", reason);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void submitDecision(String decision, String reason) {
        binding.btnVerify.setEnabled(false);
        binding.btnReject.setEnabled(false);
        binding.progressVerify.setVisibility(View.VISIBLE);

        PlotVerifyRequest request = new PlotVerifyRequest(decision, selectedPlantingDate, reason);

        apiService.verifyAgentPlot(plotId, request).enqueue(new Callback<PlotResponse>() {
            @Override
            public void onResponse(Call<PlotResponse> call, Response<PlotResponse> response) {
                handleDecisionComplete(decision, reason);
            }

            @Override
            public void onFailure(Call<PlotResponse> call, Throwable t) {
                // If offline or planned endpoint returns error, persist locally & queue in outbox
                handleDecisionComplete(decision, reason);
            }
        });
    }

    private void handleDecisionComplete(String decision, String reason) {
        new Thread(() -> {
            RindaDatabase db = RindaDatabase.getInstance(VerifyPlotActivity.this);
            if (currentPlot != null) {
                currentPlot.status = "ACTIVE".equalsIgnoreCase(decision) ? PlotStatus.ACTIVE : PlotStatus.REJECTED;
                currentPlot.plantingDate = selectedPlantingDate;
                db.plotDao().insertOrUpdate(currentPlot);
            }

            // Queue in offline outbox
            String payload = String.format(Locale.US, "{\"plotId\":%d,\"decision\":\"%s\",\"confirmedPlantingDate\":\"%s\"}",
                    plotId, decision, selectedPlantingDate);
            db.outboxDao().insert(new OutboxEntity("PLOT_VERIFY", payload, null));
            com.rinda.app.data.sync.OutboxSyncWorker.syncNow(VerifyPlotActivity.this);

            runOnUiThread(() -> {
                int msgRes = "ACTIVE".equals(decision) ? R.string.plot_verified_success : R.string.plot_rejected_success;
                Ui.toast(binding.getRoot(), msgRes);
                setResult(RESULT_OK);
                finish();
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.mapView.onPause();
    }
}
