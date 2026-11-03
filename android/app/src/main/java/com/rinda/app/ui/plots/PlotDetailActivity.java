package com.rinda.app.ui.plots;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.ApiErrorParser;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.ApiProblem;
import com.rinda.app.data.model.BaselineBinResponse;
import com.rinda.app.data.model.BaselineResponse;
import com.rinda.app.data.model.EnrollmentResponse;
import com.rinda.app.data.model.HarvestForecastResponse;
import com.rinda.app.data.model.ObservationResponse;
import com.rinda.app.data.model.PlotHealthStatusResponse;
import com.rinda.app.data.model.PlotRequest;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.data.model.PlotStatus;
import com.rinda.app.data.model.WeatherResponse;
import com.rinda.app.databinding.ActivityPlotDetailBinding;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlotDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PLOT_ID = "extra_plot_id";
    public static final String EXTRA_PLOT_NAME = "extra_plot_name";

    private ActivityPlotDetailBinding binding;
    private long plotId;
    private PlotEntity currentPlot;
    private BaselineResponse currentBaseline;
    private List<ObservationResponse> currentObservations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlotDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        plotId = getIntent().getLongExtra(EXTRA_PLOT_ID, -1);
        String name = getIntent().getStringExtra(EXTRA_PLOT_NAME);
        if (name != null) {
            binding.tvPlotName.setText(name);
        }

        if (plotId == -1) {
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_green);
        binding.swipeRefresh.setOnRefreshListener(this::fetchAllData);

        binding.btnEditPlot.setOnClickListener(v -> showEditNameDialog());
        binding.btnDeletePlot.setOnClickListener(v -> showDeleteConfirmDialog());

        observeLocalPlot();
        fetchAllData();
    }

    private void observeLocalPlot() {
        RindaDatabase.getInstance(this).plotDao().getPlotByIdLiveData(plotId)
                .observe(this, plot -> {
                    if (plot != null) {
                        currentPlot = plot;
                        updatePlotMetadataUi(plot);
                    }
                });
    }

    private void fetchAllData() {
        binding.swipeRefresh.setRefreshing(true);
        fetchPlotMetadata();
        fetchCropHealthStatus();
        fetchEnrollment();
        fetchHarvestForecast();
        fetchBaseline();
        fetchObservations();
        fetchWeather();
    }

    private void fetchPlotMetadata() {
        ApiClient.getInstance(this).getService().getPlot(plotId)
                .enqueue(new Callback<PlotResponse>() {
                    @Override
                    public void onResponse(Call<PlotResponse> call, Response<PlotResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            savePlotLocally(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<PlotResponse> call, Throwable t) {
                        // Offline: cache displays
                    }
                });
    }

    private void updatePlotMetadataUi(PlotEntity plot) {
        binding.tvPlotName.setText(plot.name);
        binding.tvHeaderDemoBadge.setVisibility(plot.isDemo ? View.VISIBLE : View.GONE);

        double area = plot.areaHa != null ? plot.areaHa.doubleValue() : 0.0;
        binding.tvArea.setText(String.format("%.2f ha", area));

        if (plot.plantingDate != null && !plot.plantingDate.isEmpty()) {
            binding.tvPlantingDate.setText("Planted: " + plot.plantingDate);
        } else {
            binding.tvPlantingDate.setText("Planting date unconfirmed");
        }

        PlotStatus status = plot.status != null ? plot.status : PlotStatus.PENDING_VERIFICATION;
        switch (status) {
            case ACTIVE:
                binding.layoutVerificationChip.setBackgroundResource(R.drawable.ic_status_healthy_bg);
                binding.ivVerificationIcon.setImageResource(R.drawable.ic_status_healthy);
                binding.tvVerificationText.setText(R.string.plot_status_active);
                binding.tvVerificationText.setTextColor(ContextCompat.getColor(this, R.color.status_healthy));
                break;
            case REJECTED:
                binding.layoutVerificationChip.setBackgroundResource(R.drawable.ic_status_at_risk_bg);
                binding.ivVerificationIcon.setImageResource(R.drawable.ic_status_at_risk);
                binding.tvVerificationText.setText(R.string.plot_status_rejected);
                binding.tvVerificationText.setTextColor(ContextCompat.getColor(this, R.color.status_at_risk));
                break;
            case ARCHIVED:
                binding.layoutVerificationChip.setBackgroundResource(R.drawable.ic_status_watch_bg);
                binding.ivVerificationIcon.setImageResource(R.drawable.ic_status_watch);
                binding.tvVerificationText.setText(R.string.plot_status_archived);
                binding.tvVerificationText.setTextColor(ContextCompat.getColor(this, R.color.status_watch));
                break;
            case PENDING_VERIFICATION:
            default:
                binding.layoutVerificationChip.setBackgroundResource(R.drawable.ic_status_pending_bg);
                binding.ivVerificationIcon.setImageResource(R.drawable.ic_status_pending);
                binding.tvVerificationText.setText(R.string.plot_status_pending);
                binding.tvVerificationText.setTextColor(ContextCompat.getColor(this, R.color.status_pending));
                break;
        }
    }

    private void fetchCropHealthStatus() {
        ApiClient.getInstance(this).getService().getPlotHealthStatus(plotId)
                .enqueue(new Callback<PlotHealthStatusResponse>() {
                    @Override
                    public void onResponse(Call<PlotHealthStatusResponse> call, Response<PlotHealthStatusResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            updateHealthStatusUi(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<PlotHealthStatusResponse> call, Throwable t) {
                        // Offline or not available
                    }
                });
    }

    private void updateHealthStatusUi(PlotHealthStatusResponse health) {
        String status = health.getStatus();
        switch (status) {
            case "HEALTHY":
                binding.ivHealthIcon.setImageResource(R.drawable.ic_status_healthy);
                binding.tvHealthWord.setText(R.string.health_healthy);
                binding.tvHealthWord.setTextColor(ContextCompat.getColor(this, R.color.status_healthy));
                break;
            case "WATCH":
                binding.ivHealthIcon.setImageResource(R.drawable.ic_status_watch);
                binding.tvHealthWord.setText(R.string.health_watch);
                binding.tvHealthWord.setTextColor(ContextCompat.getColor(this, R.color.status_watch));
                break;
            case "AT_RISK":
                binding.ivHealthIcon.setImageResource(R.drawable.ic_status_at_risk);
                binding.tvHealthWord.setText(R.string.health_at_risk);
                binding.tvHealthWord.setTextColor(ContextCompat.getColor(this, R.color.status_at_risk));
                break;
            case "NOT_ENOUGH_DATA":
            default:
                binding.ivHealthIcon.setImageResource(R.drawable.ic_status_no_data);
                binding.tvHealthWord.setText(R.string.health_not_enough_data);
                binding.tvHealthWord.setTextColor(ContextCompat.getColor(this, R.color.status_not_enough_data));
                break;
        }

        binding.tvConfidenceBadge.setText(health.getConfidence() + " Confidence");
        binding.tvCause.setText("Observed: " + health.getFriendlyCause());

        if (health.getDataQualityNote() != null) {
            binding.tvDataQualityNote.setText(health.getDataQualityNote());
        }

        if (health.getRainPct() != null) {
            binding.tvExplanation.setText(String.format("30-day rain is %.0f%% of normal. Anomaly score: %.2f.",
                    health.getRainPct().doubleValue(), health.getAnomaly() != null ? health.getAnomaly().doubleValue() : 0.0));
        }
    }

    private void fetchBaseline() {
        ApiClient.getInstance(this).getService().getBaseline(plotId)
                .enqueue(new Callback<BaselineResponse>() {
                    @Override
                    public void onResponse(Call<BaselineResponse> call, Response<BaselineResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            currentBaseline = response.body();
                            renderNdviChart();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaselineResponse> call, Throwable t) {
                    }
                });
    }

    private void fetchObservations() {
        ApiClient.getInstance(this).getService().getObservations(plotId, null, null)
                .enqueue(new Callback<List<ObservationResponse>>() {
                    @Override
                    public void onResponse(Call<List<ObservationResponse>> call, Response<List<ObservationResponse>> response) {
                        binding.swipeRefresh.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            currentObservations = response.body();
                            renderNdviChart();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<ObservationResponse>> call, Throwable t) {
                        binding.swipeRefresh.setRefreshing(false);
                    }
                });
    }

    private void renderNdviChart() {
        LineChart chart = binding.chartNdvi;
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);

        List<LineDataSet> dataSets = new ArrayList<>();

        // 1. Normal Band from Baseline (p10, p50, p90)
        if (currentBaseline != null && !currentBaseline.getBins().isEmpty()) {
            List<Entry> p10Entries = new ArrayList<>();
            List<Entry> p50Entries = new ArrayList<>();
            List<Entry> p90Entries = new ArrayList<>();

            for (BaselineBinResponse bin : currentBaseline.getBins()) {
                float dap = bin.getDapBin();
                p10Entries.add(new Entry(dap, bin.getP10().floatValue()));
                p50Entries.add(new Entry(dap, bin.getP50().floatValue()));
                p90Entries.add(new Entry(dap, bin.getP90().floatValue()));
            }

            LineDataSet p50Set = new LineDataSet(p50Entries, "Normal Median (p50)");
            p50Set.setColor(ContextCompat.getColor(this, R.color.rinda_green_light));
            p50Set.setLineWidth(1.8f);
            p50Set.enableDashedLine(10f, 5f, 0f);
            p50Set.setDrawCircles(false);
            p50Set.setDrawValues(false);
            dataSets.add(p50Set);

            LineDataSet p90Set = new LineDataSet(p90Entries, "Upper Band (p90)");
            p90Set.setColor(Color.parseColor("#A5D6A7"));
            p90Set.setLineWidth(1.2f);
            p90Set.setDrawCircles(false);
            p90Set.setDrawValues(false);
            dataSets.add(p90Set);

            LineDataSet p10Set = new LineDataSet(p10Entries, "Lower Band (p10)");
            p10Set.setColor(Color.parseColor("#A5D6A7"));
            p10Set.setLineWidth(1.2f);
            p10Set.setDrawCircles(false);
            p10Set.setDrawValues(false);
            dataSets.add(p10Set);
        }

        // 2. Plot Observations (NDVI)
        if (!currentObservations.isEmpty()) {
            List<Entry> plotEntries = new ArrayList<>();
            for (ObservationResponse obs : currentObservations) {
                if (obs.getSmoothedNdvi() != null && obs.getDap() != null) {
                    plotEntries.add(new Entry(obs.getDap().floatValue(), obs.getSmoothedNdvi().floatValue()));
                }
            }

            if (!plotEntries.isEmpty()) {
                LineDataSet plotSet = new LineDataSet(plotEntries, "This Plot (NDVI)");
                plotSet.setColor(ContextCompat.getColor(this, R.color.rinda_green_dark));
                plotSet.setLineWidth(2.8f);
                plotSet.setCircleColor(ContextCompat.getColor(this, R.color.rinda_green_dark));
                plotSet.setCircleRadius(3.5f);
                plotSet.setDrawCircleHole(false);
                plotSet.setDrawValues(false);
                dataSets.add(plotSet);

                // Update DAP in header
                Entry latest = plotEntries.get(plotEntries.size() - 1);
                binding.tvDap.setText(String.format("%.0f DAP", latest.getX()));
            }
        }

        if (!dataSets.isEmpty()) {
            chart.setData(new LineData(dataSets.toArray(new LineDataSet[0])));
            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);

            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setAxisMinimum(-0.2f);
            leftAxis.setAxisMaximum(1.0f);
            chart.getAxisRight().setEnabled(false);

            chart.invalidate();
        }
    }

    private void fetchWeather() {
        ApiClient.getInstance(this).getService().getWeather(plotId, null, null)
                .enqueue(new Callback<List<WeatherResponse>>() {
                    @Override
                    public void onResponse(Call<List<WeatherResponse>> call, Response<List<WeatherResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            renderRainChart(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<WeatherResponse>> call, Throwable t) {
                    }
                });
    }

    private void renderRainChart(List<WeatherResponse> weatherList) {
        BarChart chart = binding.chartRain;
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);

        List<BarEntry> entries = new ArrayList<>();
        int index = 0;
        for (WeatherResponse w : weatherList) {
            float rain = w.getRainMm() != null ? w.getRainMm().floatValue() : 0.0f;
            entries.add(new BarEntry(index++, rain));
        }

        if (!entries.isEmpty()) {
            BarDataSet set = new BarDataSet(entries, "Rainfall (mm)");
            set.setColor(ContextCompat.getColor(this, R.color.rinda_blue));
            set.setDrawValues(false);

            BarData data = new BarData(set);
            data.setBarWidth(0.6f);
            chart.setData(data);

            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            chart.getAxisRight().setEnabled(false);

            chart.invalidate();
        }
    }

    private void fetchHarvestForecast() {
        ApiClient.getInstance(this).getService().getHarvestForecast(plotId)
                .enqueue(new Callback<HarvestForecastResponse>() {
                    @Override
                    public void onResponse(Call<HarvestForecastResponse> call, Response<HarvestForecastResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            updateForecastUi(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<HarvestForecastResponse> call, Throwable t) {
                    }
                });
    }

    private void updateForecastUi(HarvestForecastResponse forecast) {
        if (forecast.isAvailable() && forecast.getLowPct() != null && forecast.getHighPct() != null) {
            binding.tvHarvestForecastRange.setText(String.format(java.util.Locale.US, "%.0f%% – %.0f%% of normal",
                    forecast.getLowPct().doubleValue(), forecast.getHighPct().doubleValue()));
        } else {
            String reason = forecast.getReason();
            if ("TOO_EARLY".equalsIgnoreCase(reason)) {
                binding.tvHarvestForecastRange.setText("Too early in season");
            } else if ("NOT_ENOUGH_DATA".equalsIgnoreCase(reason)) {
                binding.tvHarvestForecastRange.setText("Not enough satellite data yet");
            } else if ("NOT_ANALYSED".equalsIgnoreCase(reason)) {
                binding.tvHarvestForecastRange.setText("Awaiting initial satellite pass");
            } else {
                binding.tvHarvestForecastRange.setText("Estimate pending");
            }
        }
        if (forecast.getDataQualityNote() != null && !forecast.getDataQualityNote().isEmpty()) {
            binding.tvForecastDisclaimer.setText(forecast.getDataQualityNote());
        }
    }

    private void fetchEnrollment() {
        ApiClient.getInstance(this).getService().getEnrollment(plotId)
                .enqueue(new Callback<EnrollmentResponse>() {
                    @Override
                    public void onResponse(Call<EnrollmentResponse> call, Response<EnrollmentResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            updateEnrollmentUi(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<EnrollmentResponse> call, Throwable t) {
                    }
                });
    }

    private void updateEnrollmentUi(EnrollmentResponse enrollment) {
        String status = enrollment.getStatus();
        if ("COVERED".equalsIgnoreCase(status)) {
            String season = enrollment.getSeasonCode() != null ? enrollment.getSeasonCode() : "2026A";
            binding.tvEnrollmentStatus.setText("Covered (Season " + season + ")");
            binding.tvEnrollmentStatus.setTextColor(ContextCompat.getColor(this, R.color.rinda_green));
        } else if ("PENDING".equalsIgnoreCase(status)) {
            binding.tvEnrollmentStatus.setText("Coverage Pending");
            binding.tvEnrollmentStatus.setTextColor(ContextCompat.getColor(this, R.color.status_watch));
        } else if ("CANCELLED".equalsIgnoreCase(status)) {
            binding.tvEnrollmentStatus.setText("Coverage Cancelled");
            binding.tvEnrollmentStatus.setTextColor(ContextCompat.getColor(this, R.color.status_at_risk));
        } else {
            binding.tvEnrollmentStatus.setText("Not Enrolled");
            binding.tvEnrollmentStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary_light));
        }

        if (enrollment.getSumInsured() != null) {
            java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
            binding.tvSumInsured.setText(nf.format(enrollment.getSumInsured()) + " RWF");
        } else {
            binding.tvSumInsured.setText("Not confirmed yet");
        }
    }

    private void showEditNameDialog() {
        if (currentPlot != null && (currentPlot.plantingDate == null || currentPlot.plantingDate.isEmpty())) {
            new AlertDialog.Builder(this)
                    .setTitle("Cannot Edit Placeholder Plot")
                    .setMessage("Seeded demo plots have no confirmed planting date. They cannot be edited until confirmed.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        EditText input = new EditText(this);
        input.setText(currentPlot != null ? currentPlot.name : "");
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Edit Plot Name")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && currentPlot != null) {
                        updatePlotName(newName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updatePlotName(String newName) {
        PlotRequest req = new PlotRequest(newName, currentPlot.boundary, currentPlot.plantingDate, "MAIZE");
        ApiClient.getInstance(this).getService().updatePlot(plotId, req)
                .enqueue(new Callback<PlotResponse>() {
                    @Override
                    public void onResponse(Call<PlotResponse> call, Response<PlotResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            savePlotLocally(response.body());
                            Toast.makeText(PlotDetailActivity.this, "Plot updated", Toast.LENGTH_SHORT).show();
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            Toast.makeText(PlotDetailActivity.this, ApiErrorParser.getFriendlyMessage(PlotDetailActivity.this, problem), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PlotResponse> call, Throwable t) {
                        Toast.makeText(PlotDetailActivity.this, getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_delete)
                .setMessage("Are you sure you want to delete this plot? Active covers and open claims will preserve the record.")
                .setPositiveButton("Delete", (dialog, which) -> deletePlot())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deletePlot() {
        ApiClient.getInstance(this).getService().deletePlot(plotId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful() || response.code() == 204) {
                            new Thread(() -> RindaDatabase.getInstance(PlotDetailActivity.this).plotDao().deleteById(plotId)).start();
                            Toast.makeText(PlotDetailActivity.this, "Plot archived", Toast.LENGTH_SHORT).show();
                            finish();
                        } else if (response.code() == 409) {
                            new AlertDialog.Builder(PlotDetailActivity.this)
                                    .setTitle("Delete Deferred")
                                    .setMessage(getString(R.string.error_plot_delete_deferred))
                                    .setPositiveButton("OK", null)
                                    .show();
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            Toast.makeText(PlotDetailActivity.this, ApiErrorParser.getFriendlyMessage(PlotDetailActivity.this, problem), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(PlotDetailActivity.this, getString(R.string.error_network), Toast.LENGTH_SHORT).show();
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
            RindaDatabase.getInstance(PlotDetailActivity.this).plotDao().insertOrUpdate(e);
        }).start();
    }
}
