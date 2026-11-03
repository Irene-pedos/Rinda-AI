package com.rinda.app.ui.plots;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
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
import com.rinda.app.data.model.PlotStatus;
import com.rinda.app.databinding.ActivityRegisterPlotBinding;
import com.rinda.app.databinding.ViewDetailRowBinding;
import com.rinda.app.ui.common.InfoSheet;
import com.rinda.app.ui.common.StatusUi;
import com.rinda.app.ui.common.Ui;
import com.rinda.app.util.DateTimeUtils;
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

/**
 * Register a plot as a guided 3-step flow (spec §9.4):
 * 1. draw the field on the map, 2. name and planting date, 3. check and send, then success.
 * The plot starts as PENDING_VERIFICATION until an agent checks it (spec §2.2 #4).
 */
public class RegisterPlotActivity extends AppCompatActivity {

    private static final int STEP_DRAW = 1;
    private static final int STEP_DETAILS = 2;
    private static final int STEP_REVIEW = 3;
    private static final int STEP_SUCCESS = 4;
    private static final int TOTAL_STEPS = 3;
    private static final int MIN_POINTS = 3;

    private ActivityRegisterPlotBinding binding;
    private final List<GeoPoint> boundaryPoints = new ArrayList<>();
    private final List<Marker> pointMarkers = new ArrayList<>();
    private Polygon currentPolygon;
    private final Calendar calendar = Calendar.getInstance();
    private String selectedPlantingDate = null;
    private int step = STEP_DRAW;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        binding = ActivityRegisterPlotBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupMap();
        setupForm();

        binding.btnNext.setOnClickListener(v -> onNext());
        binding.btnBack.setOnClickListener(v -> goTo(step - 1));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (step > STEP_DRAW && step < STEP_SUCCESS) {
                    goTo(step - 1);
                } else {
                    finish();
                }
            }
        });

        goTo(STEP_DRAW);
    }

    // ===== Steps =====

    private void onNext() {
        switch (step) {
            case STEP_DRAW:
                if (boundaryPoints.size() < MIN_POINTS) {
                    Ui.toast(binding.getRoot(), R.string.draw_need_three);
                    return;
                }
                goTo(STEP_DETAILS);
                break;
            case STEP_DETAILS:
                if (validateDetails()) {
                    goTo(STEP_REVIEW);
                }
                break;
            case STEP_REVIEW:
                submitPlot();
                break;
            case STEP_SUCCESS:
            default:
                finish();
                break;
        }
    }

    private void goTo(int newStep) {
        step = Math.max(STEP_DRAW, newStep);
        show(binding.stepDraw, step == STEP_DRAW);
        show(binding.stepDetails, step == STEP_DETAILS);
        show(binding.stepReview, step == STEP_REVIEW);
        show(binding.stepSuccess, step == STEP_SUCCESS);

        if (step == STEP_SUCCESS) {
            binding.stepHeader.setVisibility(View.GONE);
            binding.btnBack.setVisibility(View.GONE);
            binding.btnNext.setText(R.string.btn_see_my_plots);
            binding.btnNext.setIcon(null);
            binding.toolbar.setNavigationIcon(null);
            return;
        }

        binding.stepHeader.setVisibility(View.VISIBLE);
        binding.stepProgress.setProgressCompat(step, true);
        binding.tvStepLabel.setText(getString(R.string.step_of, step, TOTAL_STEPS));
        binding.tvStepTitle.setText(step == STEP_DRAW ? R.string.register_step_draw
                : step == STEP_DETAILS ? R.string.register_step_details : R.string.register_step_review);
        binding.btnBack.setVisibility(step == STEP_DRAW ? View.GONE : View.VISIBLE);
        binding.btnNext.setText(step == STEP_REVIEW ? R.string.btn_send_plot : R.string.btn_next);
        binding.btnNext.setIconResource(step == STEP_REVIEW ? R.drawable.ic_check : R.drawable.ic_arrow_forward);

        if (step == STEP_DRAW) {
            updateMapUi();
        } else if (step == STEP_REVIEW) {
            fillReview();
        }
    }

    private void show(View view, boolean visible) {
        if (visible) {
            Ui.reveal(view);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    // ===== Step 1: map =====

    private void setupMap() {
        binding.mapView.setMultiTouchControls(true);
        // Default centre on the pilot area in Rwanda
        binding.mapView.getController().setZoom(15.0);
        binding.mapView.getController().setCenter(new GeoPoint(-1.95, 30.1));

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
        binding.btnDrawHelp.setOnClickListener(v ->
                InfoSheet.show(this, R.drawable.ic_touch, R.string.help_draw_title, R.string.help_draw_body));
    }

    private void addBoundaryPoint(GeoPoint point) {
        boundaryPoints.add(point);

        Marker marker = new Marker(binding.mapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setTitle(getString(R.string.draw_point_title, boundaryPoints.size()));
        binding.mapView.getOverlays().add(marker);
        pointMarkers.add(marker);

        binding.mapView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        updatePolygonOverlay();
        updateMapUi();
    }

    private void undoLastPoint() {
        if (!boundaryPoints.isEmpty()) {
            int lastIndex = boundaryPoints.size() - 1;
            boundaryPoints.remove(lastIndex);
            binding.mapView.getOverlays().remove(pointMarkers.remove(lastIndex));
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
        if (boundaryPoints.size() >= MIN_POINTS) {
            currentPolygon = new Polygon(binding.mapView);
            List<GeoPoint> closedPoints = new ArrayList<>(boundaryPoints);
            closedPoints.add(boundaryPoints.get(0)); // visual close
            currentPolygon.setPoints(closedPoints);
            int fill = ContextCompat.getColor(this, R.color.rinda_primary_container);
            currentPolygon.getFillPaint().setColor((fill & 0x00FFFFFF) | 0x88000000);
            currentPolygon.getOutlinePaint().setColor(ContextCompat.getColor(this, R.color.rinda_primary));
            currentPolygon.getOutlinePaint().setStrokeWidth(6f);
            binding.mapView.getOverlays().add(currentPolygon);
        }
        binding.mapView.invalidate();
    }

    private void updateMapUi() {
        int count = boundaryPoints.size();
        boolean ready = count >= MIN_POINTS;
        binding.tvPointCount.setText(ready
                ? getString(R.string.draw_points_ready, count)
                : getString(R.string.draw_points_needed, MIN_POINTS - count));
        binding.tvPointCount.setTextColor(ContextCompat.getColor(this, ready ? R.color.status_healthy : R.color.rinda_primary));
        binding.tvEstimatedArea.setText(ready
                ? getString(R.string.draw_area_estimate, computeApproxAreaHa(boundaryPoints))
                : getString(R.string.draw_area_empty));
        binding.btnUndoPoint.setEnabled(count > 0);
        binding.btnClearPoints.setEnabled(count > 0);
        binding.btnNext.setEnabled(ready);
    }

    /** Simple planar approximation, for feedback only. The server computes the real area. */
    private double computeApproxAreaHa(List<GeoPoint> points) {
        if (points.size() < MIN_POINTS) return 0.0;
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
        return Math.abs(area) / 20000.0; // hectares
    }

    // ===== Step 2: details =====

    private void setupForm() {
        binding.etPlantingDate.setOnClickListener(v -> showDatePicker());
        binding.tilPlantingDate.setEndIconOnClickListener(v ->
                InfoSheet.show(this, R.drawable.ic_calendar, R.string.help_planting_title, R.string.help_planting_body));
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    selectedPlantingDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
                    binding.etPlantingDate.setText(DateTimeUtils.formatDate(selectedPlantingDate));
                    binding.tilPlantingDate.setError(null);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis()); // cannot be in the future
        dialog.show();
    }

    private boolean validateDetails() {
        binding.tilPlotName.setError(null);
        binding.tilPlantingDate.setError(null);
        boolean ok = true;
        if (plotName().isEmpty()) {
            binding.tilPlotName.setError(getString(R.string.error_plot_name_required));
            ok = false;
        }
        if (selectedPlantingDate == null || selectedPlantingDate.isEmpty()) {
            binding.tilPlantingDate.setError(getString(R.string.error_planting_date_required));
            ok = false;
        }
        return ok;
    }

    private String plotName() {
        return binding.etPlotName.getText() != null ? binding.etPlotName.getText().toString().trim() : "";
    }

    // ===== Step 3: review =====

    private void fillReview() {
        row(binding.rowName, R.drawable.ic_edit, R.string.review_name, plotName());
        row(binding.rowArea, R.drawable.ic_area, R.string.review_area,
                getString(R.string.draw_area_estimate, computeApproxAreaHa(boundaryPoints)));
        row(binding.rowPlanting, R.drawable.ic_calendar, R.string.review_planting, DateTimeUtils.formatDate(selectedPlantingDate));
        row(binding.rowCrop, R.drawable.ic_leaf, R.string.review_crop, getString(R.string.crop_maize));
        row(binding.rowCorners, R.drawable.ic_map_pin, R.string.review_corners, String.valueOf(boundaryPoints.size()));

        binding.nextInfo.ivPointIcon.setImageResource(R.drawable.ic_visit);
        binding.nextInfo.tvPointTitle.setText(R.string.review_next_title);
        binding.nextInfo.tvPointBody.setText(R.string.review_next_body);
    }

    private void row(ViewDetailRowBinding row, int iconRes, int labelRes, String value) {
        row.ivRowIcon.setImageResource(iconRes);
        row.tvRowLabel.setText(labelRes);
        row.tvRowValue.setText(value);
    }

    private void submitPlot() {
        // GeoJSON ring: [[lon, lat], ..., first point again]
        List<List<Double>> ring = new ArrayList<>();
        for (GeoPoint p : boundaryPoints) {
            List<Double> coord = new ArrayList<>(2);
            coord.add(p.getLongitude());
            coord.add(p.getLatitude());
            ring.add(coord);
        }
        List<Double> first = new ArrayList<>(2);
        first.add(boundaryPoints.get(0).getLongitude());
        first.add(boundaryPoints.get(0).getLatitude());
        ring.add(first);

        GeoJsonPolygon polygon = new GeoJsonPolygon(Collections.singletonList(ring));
        PlotRequest request = new PlotRequest(plotName(), polygon, selectedPlantingDate, "MAIZE");

        setLoading(true);

        ApiClient.getInstance(this).getService().createPlot(request)
                .enqueue(new Callback<PlotResponse>() {
                    @Override
                    public void onResponse(Call<PlotResponse> call, Response<PlotResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            PlotResponse plot = response.body();
                            savePlotLocally(plot);
                            showSuccess(plot);
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            Ui.dialog(RegisterPlotActivity.this)
                                    .setTitle(R.string.error_generic)
                                    .setMessage(ApiErrorParser.getFriendlyMessage(RegisterPlotActivity.this, problem))
                                    .setPositiveButton(R.string.btn_ok, null)
                                    .show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PlotResponse> call, Throwable t) {
                        setLoading(false);
                        Ui.error(binding.getRoot(), getString(R.string.error_network_action), RegisterPlotActivity.this::submitPlot);
                    }
                });
    }

    private void showSuccess(PlotResponse plot) {
        String area = plot.getAreaHa() != null
                ? getString(R.string.plot_area, plot.getAreaHa().doubleValue())
                : getString(R.string.draw_area_estimate, computeApproxAreaHa(boundaryPoints));
        binding.tvSuccessBody.setText(getString(R.string.register_success_body, plot.getName(), area));
        PlotStatus status = plot.getStatus() != null ? plot.getStatus() : PlotStatus.PENDING_VERIFICATION;
        StatusUi.applyPill(binding.tvSuccessStatus, StatusUi.forPlot(status));
        goTo(STEP_SUCCESS);

        binding.successBadge.setScaleX(0.4f);
        binding.successBadge.setScaleY(0.4f);
        binding.successBadge.animate().scaleX(1f).scaleY(1f)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                .setDuration(450).start();
        binding.successBadge.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void savePlotLocally(PlotResponse p) {
        final android.content.Context app = getApplicationContext();
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
            RindaDatabase.getInstance(app).plotDao().insertOrUpdate(e);
        }).start();
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnNext.setEnabled(!loading);
        binding.btnBack.setEnabled(!loading);
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
