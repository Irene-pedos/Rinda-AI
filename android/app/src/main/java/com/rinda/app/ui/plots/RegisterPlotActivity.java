package com.rinda.app.ui.plots;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.ApiErrorParser;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.ApiProblem;
import com.rinda.app.data.model.GeoJsonPolygon;
import com.rinda.app.data.model.PlotRequest;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.databinding.ActivityRegisterPlotBinding;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterPlotActivity extends AppCompatActivity {

    private ActivityRegisterPlotBinding binding;
    private final List<GeoPoint> boundaryPoints = new ArrayList<>();
    private final List<Marker> pointMarkers = new ArrayList<>();
    private Polygon currentPolygon;
    private final Calendar calendar = Calendar.getInstance();
    private String selectedPlantingDate = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        binding = ActivityRegisterPlotBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupMap();
        setupForm();
    }

    private void setupMap() {
        binding.mapView.setMultiTouchControls(true);
        // Default center on pilot area in Rwanda
        GeoPoint startPoint = new GeoPoint(-1.95, 30.1);
        binding.mapView.getController().setZoom(15.0);
        binding.mapView.getController().setCenter(startPoint);

        MapEventsOverlay mapEventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                addBoundaryPoint(p);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        });
        binding.mapView.getOverlays().add(0, mapEventsOverlay);

        binding.btnUndoPoint.setOnClickListener(v -> undoLastPoint());
        binding.btnClearPoints.setOnClickListener(v -> clearAllPoints());
    }

    private void addBoundaryPoint(GeoPoint point) {
        boundaryPoints.add(point);

        Marker marker = new Marker(binding.mapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setTitle("Point " + boundaryPoints.size());
        binding.mapView.getOverlays().add(marker);
        pointMarkers.add(marker);

        updatePolygonOverlay();
        updateMapUi();
    }

    private void undoLastPoint() {
        if (!boundaryPoints.isEmpty()) {
            int lastIndex = boundaryPoints.size() - 1;
            boundaryPoints.remove(lastIndex);

            Marker marker = pointMarkers.remove(lastIndex);
            binding.mapView.getOverlays().remove(marker);

            updatePolygonOverlay();
            updateMapUi();
        }
    }

    private void clearAllPoints() {
        boundaryPoints.clear();
        for (Marker m : pointMarkers) {
            binding.mapView.getOverlays().remove(m);
        }
        pointMarkers.clear();

        if (currentPolygon != null) {
            binding.mapView.getOverlays().remove(currentPolygon);
            currentPolygon = null;
        }

        updateMapUi();
        binding.mapView.invalidate();
    }

    private void updatePolygonOverlay() {
        if (currentPolygon != null) {
            binding.mapView.getOverlays().remove(currentPolygon);
            currentPolygon = null;
        }

        if (boundaryPoints.size() >= 3) {
            currentPolygon = new Polygon(binding.mapView);
            List<GeoPoint> closedPoints = new ArrayList<>(boundaryPoints);
            closedPoints.add(boundaryPoints.get(0)); // Visual close
            currentPolygon.setPoints(closedPoints);

            currentPolygon.setFillColor(ContextCompat.getColor(this, R.color.rinda_green_container) & 0x66FFFFFF);
            currentPolygon.setStrokeColor(ContextCompat.getColor(this, R.color.rinda_green));
            currentPolygon.setStrokeWidth(4.0f);

            binding.mapView.getOverlays().add(currentPolygon);
        }
        binding.mapView.invalidate();
    }

    private void updateMapUi() {
        int count = boundaryPoints.size();
        if (count < 3) {
            binding.tvPointCount.setText(String.format("Tap map to add points (%d/3 min)", count));
            binding.tvEstimatedArea.setText("Area: 0.00 ha");
        } else {
            binding.tvPointCount.setText(String.format("Boundary closed (%d points)", count));
            double areaHa = computeApproxAreaHa(boundaryPoints);
            binding.tvEstimatedArea.setText(String.format(Locale.US, "Estimated: %.2f ha", areaHa));
        }
    }

    /** Simple geodesic planar projection approximation for client feedback */
    private double computeApproxAreaHa(List<GeoPoint> points) {
        if (points.size() < 3) return 0.0;
        double area = 0.0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            GeoPoint p1 = points.get(i);
            GeoPoint p2 = points.get((i + 1) % n);
            double x1 = Math.toRadians(p1.getLongitude()) * 6378137.0 * Math.cos(Math.toRadians(p1.getLatitude()));
            double y1 = Math.toRadians(p1.getLatitude()) * 6378137.0;
            double x2 = Math.toRadians(p2.getLongitude()) * 6378137.0 * Math.cos(Math.toRadians(p2.getLatitude()));
            double y2 = Math.toRadians(p2.getLatitude()) * 6378137.0;
            area += (x1 * y2 - x2 * y1);
        }
        return Math.abs(area) / 20000.0; // in hectares
    }

    private void setupForm() {
        binding.etPlantingDate.setOnClickListener(v -> showDatePicker());
        binding.btnSubmitPlot.setOnClickListener(v -> submitPlot());
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    selectedPlantingDate = sdf.format(calendar.getTime());
                    binding.etPlantingDate.setText(selectedPlantingDate);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis()); // Planting date cannot be in future
        dialog.show();
    }

    private void submitPlot() {
        binding.tilPlotName.setError(null);
        binding.tilPlantingDate.setError(null);

        String name = binding.etPlotName.getText() != null ? binding.etPlotName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            binding.tilPlotName.setError("Please enter a name for your plot");
            return;
        }

        if (selectedPlantingDate == null || selectedPlantingDate.isEmpty()) {
            binding.tilPlantingDate.setError("Please select the planting date");
            return;
        }

        if (boundaryPoints.size() < 3) {
            Toast.makeText(this, "Please tap the map to add at least 3 boundary points", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build GeoJSON coordinates array: [ [ [lon, lat], ..., [firstLon, firstLat] ] ]
        List<List<Double>> ring = new ArrayList<>();
        for (GeoPoint p : boundaryPoints) {
            List<Double> coord = new ArrayList<>(2);
            coord.add(p.getLongitude()); // GeoJSON requires [lon, lat]
            coord.add(p.getLatitude());
            ring.add(coord);
        }
        // Close ring per GeoJSON specification (last point repeats first point)
        List<Double> first = new ArrayList<>(2);
        first.add(boundaryPoints.get(0).getLongitude());
        first.add(boundaryPoints.get(0).getLatitude());
        ring.add(first);

        GeoJsonPolygon polygon = new GeoJsonPolygon(Collections.singletonList(ring));
        PlotRequest request = new PlotRequest(name, polygon, selectedPlantingDate, "MAIZE");

        setLoading(true);

        ApiClient.getInstance(this).getService().createPlot(request)
                .enqueue(new Callback<PlotResponse>() {
                    @Override
                    public void onResponse(Call<PlotResponse> call, Response<PlotResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            PlotResponse plot = response.body();
                            savePlotLocally(plot);

                            new AlertDialog.Builder(RegisterPlotActivity.this)
                                    .setTitle("Plot Registered")
                                    .setMessage("Plot \"" + plot.getName() + "\" (" + plot.getAreaHa() + " ha) was registered successfully.\n\nStatus: PENDING_VERIFICATION until confirmed by your cooperative agent.")
                                    .setPositiveButton("Done", (dialog, which) -> finish())
                                    .setCancelable(false)
                                    .show();
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            String msg = ApiErrorParser.getFriendlyMessage(RegisterPlotActivity.this, problem);
                            Toast.makeText(RegisterPlotActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PlotResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(RegisterPlotActivity.this, getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void savePlotLocally(PlotResponse p) {
        new Thread(() -> {
            PlotEntity e = new PlotEntity();
            e.id = p.getId();
            e.farmerId = p.getFarmerId();
            e.name = p.getName();
            e.crop = p.getCrop();
            e.boundary = p.getBoundary();
            e.areaHa = p.getAreaHa();
            e.plantingDate = p.getPlantingDate();
            e.status = p.getStatus();
            e.dataStatus = p.getDataStatus();
            e.isDemo = p.isDemo();
            e.createdAt = p.getCreatedAt();
            e.updatedAt = p.getUpdatedAt();
            e.cachedAt = System.currentTimeMillis();
            RindaDatabase.getInstance(RegisterPlotActivity.this).plotDao().insertOrUpdate(e);
        }).start();
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSubmitPlot.setEnabled(!loading);
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
