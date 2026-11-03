package com.rinda.app.ui.plots;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
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
import com.rinda.app.databinding.ActivityPlotDetailBinding;
import com.rinda.app.databinding.DialogTextInputBinding;
import com.rinda.app.databinding.ViewCardHeaderBinding;
import com.rinda.app.ui.common.InfoSheet;
import com.rinda.app.ui.common.StatusUi;
import com.rinda.app.ui.common.Ui;
import com.rinda.app.util.DateTimeUtils;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        header(binding.healthHeader, R.drawable.ic_leaf, R.string.crop_health_title, R.string.help_ndvi_title, R.string.help_ndvi_body);
        header(binding.forecastHeader, R.drawable.ic_trending, R.string.harvest_forecast_title, R.string.help_forecast_title, R.string.help_forecast_body);
        header(binding.coverageHeader, R.drawable.ic_shield, R.string.coverage_title, R.string.help_coverage_title, R.string.help_coverage_body);
        header(binding.ndviHeader, R.drawable.ic_satellite, R.string.chart_ndvi_title, R.string.help_ndvi_title, R.string.help_ndvi_body);
        header(binding.rainHeader, R.drawable.ic_weather_rain, R.string.chart_rain_title, 0, 0);
        binding.btnConfidenceHelp.setOnClickListener(v ->
                InfoSheet.show(this, R.string.help_confidence_title, R.string.help_confidence_body));

        binding.rowSumInsured.ivRowIcon.setImageResource(R.drawable.ic_wallet);
        binding.rowSumInsured.tvRowLabel.setText(R.string.coverage_sum_insured);
        binding.rowSumInsured.tvRowValue.setText(R.string.coverage_not_confirmed);

        setupCharts();

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_primary);
        binding.swipeRefresh.setOnRefreshListener(this::fetchAllData);

        binding.btnEditPlot.setOnClickListener(v -> showEditNameDialog());
        binding.btnDeletePlot.setOnClickListener(v -> showDeleteConfirmDialog());

        observeLocalPlot();
        fetchAllData();
    }

    private void header(ViewCardHeaderBinding h, int iconRes, int titleRes, int helpTitleRes, int helpBodyRes) {
        h.ivHeaderIcon.setImageResource(iconRes);
        h.tvHeaderTitle.setText(titleRes);
        if (helpTitleRes != 0) {
            h.btnHeaderHelp.setVisibility(View.VISIBLE);
            InfoSheet.bind(h.btnHeaderHelp, helpTitleRes, helpBodyRes);
        }
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

    // ===== Plot header =====

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
                        // Offline: the cached plot is shown
                    }
                });
    }

    private void updatePlotMetadataUi(PlotEntity plot) {
        binding.tvPlotName.setText(plot.name);
        binding.tvHeaderDemoBadge.setVisibility(plot.isDemo ? View.VISIBLE : View.GONE);

        double area = plot.areaHa != null ? plot.areaHa.doubleValue() : 0.0;
        binding.tvArea.setText(getString(R.string.plot_area, area));

        if (plot.plantingDate != null && !plot.plantingDate.isEmpty()) {
            binding.tvPlantingDate.setText(getString(R.string.plot_planted_on, DateTimeUtils.formatDate(plot.plantingDate)));
        } else {
            binding.tvPlantingDate.setText(R.string.plot_planting_unconfirmed);
        }

        StatusUi.Spec spec = StatusUi.forPlot(plot.status);
        StatusUi.applyPill(binding.tvVerificationText, spec);
        binding.tvVerificationMeaning.setText(spec.meaningRes);
    }

    // ===== Crop health =====

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
                        // Keep "Not checked yet": never guess a status
                    }
                });
    }

    private void updateHealthStatusUi(PlotHealthStatusResponse health) {
        String status = health.getStatus();
        StatusUi.Spec spec = StatusUi.forHealth(status);
        StatusUi.applyBadge(binding.ivHealthIcon, spec);
        binding.tvHealthWord.setText(spec.labelRes);
        binding.tvHealthWord.setTextColor(ContextCompat.getColor(this, spec.fgColorRes));
        binding.tvHealthMeaning.setText(spec.meaningRes);

        if (health.getDate() != null) {
            binding.tvHealthDate.setText(getString(R.string.health_checked_on, DateTimeUtils.formatDate(health.getDate())));
            binding.tvHealthDate.setVisibility(View.VISIBLE);
        }

        if (spec.todoRes != 0) {
            binding.tvTodo.setText(spec.todoRes);
            binding.layoutTodo.setVisibility(View.VISIBLE);
        } else {
            binding.layoutTodo.setVisibility(View.GONE);
        }

        // Likely cause only when there is stress (spec §3.5). Never a named pest or disease.
        boolean stressed = "WATCH".equals(status) || "AT_RISK".equals(status);
        String cause = health.getFriendlyCause();
        if (stressed && cause != null && !cause.trim().isEmpty()) {
            binding.tvCause.setText(getString(R.string.likely_cause, cause));
            binding.tvCause.setVisibility(View.VISIBLE);
        } else {
            binding.tvCause.setVisibility(View.GONE);
        }

        if (health.getRainPct() != null) {
            binding.tvExplanation.setText(getString(R.string.rain_vs_normal, health.getRainPct().doubleValue()));
            binding.tvExplanation.setVisibility(View.VISIBLE);
        } else {
            binding.tvExplanation.setVisibility(View.GONE);
        }

        binding.tvFieldVisit.setVisibility(health.isNeedsFieldVisit() ? View.VISIBLE : View.GONE);

        // Every ML output shows its confidence and data quality note (spec §3)
        binding.tvConfidenceBadge.setText(StatusUi.confidenceLabel(this, health.getConfidence()));
        binding.layoutConfidence.setVisibility(View.VISIBLE);
        if (health.getDataQualityNote() != null && !health.getDataQualityNote().isEmpty()) {
            binding.tvDataQualityNote.setText(health.getDataQualityNote());
            binding.tvDataQualityNote.setVisibility(View.VISIBLE);
        } else {
            binding.tvDataQualityNote.setVisibility(View.GONE);
        }
    }

    // ===== Charts =====

    private int color(int res) {
        return ContextCompat.getColor(this, res);
    }

    private void setupCharts() {
        for (com.github.mikephil.charting.charts.BarLineChartBase<?> chart :
                new com.github.mikephil.charting.charts.BarLineChartBase<?>[]{binding.chartNdvi, binding.chartRain}) {
            chart.getDescription().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setPinchZoom(true);
            chart.setDoubleTapToZoomEnabled(false);
            chart.setDrawGridBackground(false);
            chart.setNoDataTextColor(color(R.color.rinda_text_tertiary));
            chart.getAxisRight().setEnabled(false);
            chart.setExtraBottomOffset(4f);

            XAxis x = chart.getXAxis();
            x.setPosition(XAxis.XAxisPosition.BOTTOM);
            x.setDrawGridLines(false);
            x.setTextColor(color(R.color.chart_axis_text));
            x.setTextSize(12f);
            x.setAxisLineColor(color(R.color.chart_grid));

            YAxis y = chart.getAxisLeft();
            y.setTextColor(color(R.color.chart_axis_text));
            y.setTextSize(12f);
            y.setGridColor(color(R.color.chart_grid));
            y.setAxisLineColor(color(R.color.chart_grid));

            Legend legend = chart.getLegend();
            legend.setTextColor(color(R.color.rinda_text_secondary));
            legend.setTextSize(13f);
            legend.setWordWrapEnabled(true);
            legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
            legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
            legend.setDrawInside(false);
        }
        binding.chartNdvi.setNoDataText(getString(R.string.chart_no_data));
        binding.chartRain.setNoDataText(getString(R.string.chart_rain_no_data));
        binding.chartNdvi.getAxisLeft().setDrawGridLines(false);
        binding.chartNdvi.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%d", Math.round(value));
            }
        });
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

    /**
     * NDVI vs the normal band (spec §2.2 #8). The band is drawn by filling p90 down to the axis in the
     * band colour, then p10 down to the axis in the card colour, which leaves the p10–p90 range shaded.
     */
    private void renderNdviChart() {
        LineChart chart = binding.chartNdvi;
        List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet> dataSets = new ArrayList<>();

        if (currentBaseline != null && currentBaseline.getBins() != null && !currentBaseline.getBins().isEmpty()) {
            List<Entry> p10 = new ArrayList<>();
            List<Entry> p50 = new ArrayList<>();
            List<Entry> p90 = new ArrayList<>();
            for (BaselineBinResponse bin : currentBaseline.getBins()) {
                if (bin.getP10() == null || bin.getP50() == null || bin.getP90() == null) continue;
                float dap = bin.getDapBin();
                p10.add(new Entry(dap, bin.getP10().floatValue()));
                p50.add(new Entry(dap, bin.getP50().floatValue()));
                p90.add(new Entry(dap, bin.getP90().floatValue()));
            }
            if (!p50.isEmpty()) {
                dataSets.add(bandEdge(p90, R.color.chart_normal_band));
                dataSets.add(bandEdge(p10, R.color.rinda_surface));

                LineDataSet median = new LineDataSet(p50, getString(R.string.chart_legend_normal));
                median.setColor(color(R.color.chart_normal_median));
                median.setLineWidth(1.6f);
                median.enableDashedLine(12f, 6f, 0f);
                median.setDrawCircles(false);
                median.setDrawValues(false);
                median.setHighlightEnabled(false);
                dataSets.add(median);
            }
        }

        List<Entry> plotEntries = new ArrayList<>();
        for (ObservationResponse obs : currentObservations) {
            if (obs.getSmoothedNdvi() != null && obs.getDap() != null) {
                plotEntries.add(new Entry(obs.getDap().floatValue(), obs.getSmoothedNdvi().floatValue()));
            }
        }
        if (!plotEntries.isEmpty()) {
            LineDataSet plotSet = new LineDataSet(plotEntries, getString(R.string.chart_legend_plot));
            plotSet.setColor(color(R.color.chart_plot_line));
            plotSet.setLineWidth(3f);
            plotSet.setCircleColor(color(R.color.chart_plot_line));
            plotSet.setCircleRadius(3.5f);
            plotSet.setDrawCircleHole(false);
            plotSet.setDrawValues(false);
            plotSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
            dataSets.add(plotSet);

            Entry latest = plotEntries.get(plotEntries.size() - 1);
            binding.tvDap.setText(getString(R.string.plot_days_since_planting, Math.round(latest.getX())));
            binding.tvDap.setVisibility(View.VISIBLE);
        }

        if (dataSets.isEmpty()) {
            chart.clear();
            return;
        }

        chart.setData(new LineData(dataSets));
        chart.getAxisLeft().setAxisMinimum(-0.2f);
        chart.getAxisLeft().setAxisMaximum(1.0f);

        List<LegendEntry> legend = new ArrayList<>();
        if (!plotEntries.isEmpty()) {
            legend.add(new LegendEntry(getString(R.string.chart_legend_plot), Legend.LegendForm.LINE, 12f, 3f, null, color(R.color.chart_plot_line)));
        }
        if (dataSets.size() > (plotEntries.isEmpty() ? 0 : 1)) {
            legend.add(new LegendEntry(getString(R.string.chart_legend_band), Legend.LegendForm.SQUARE, 10f, 0f, null, color(R.color.chart_normal_band)));
            legend.add(new LegendEntry(getString(R.string.chart_legend_normal), Legend.LegendForm.LINE, 12f, 1.6f, null, color(R.color.chart_normal_median)));
        }
        chart.getLegend().setCustom(legend);
        chart.animateX(500);
    }

    private LineDataSet bandEdge(List<Entry> entries, int fillColorRes) {
        LineDataSet set = new LineDataSet(entries, "");
        set.setColor(color(R.color.chart_normal_band));
        set.setLineWidth(1f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setHighlightEnabled(false);
        set.setDrawFilled(true);
        set.setFillColor(color(fillColorRes));
        set.setFillAlpha(255);
        return set;
    }

    private void fetchWeather() {
        ApiClient.getInstance(this).getService().getWeather(plotId, null, null)
                .enqueue(new Callback<List<com.rinda.app.data.model.WeatherResponse>>() {
                    @Override
                    public void onResponse(Call<List<com.rinda.app.data.model.WeatherResponse>> call,
                                           Response<List<com.rinda.app.data.model.WeatherResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            renderRainChart(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<com.rinda.app.data.model.WeatherResponse>> call, Throwable t) {
                    }
                });
    }

    private void renderRainChart(List<com.rinda.app.data.model.WeatherResponse> weatherList) {
        BarChart chart = binding.chartRain;
        List<BarEntry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        int index = 0;
        for (com.rinda.app.data.model.WeatherResponse w : weatherList) {
            float rain = w.getRainMm() != null ? w.getRainMm().floatValue() : 0.0f;
            entries.add(new BarEntry(index++, rain));
            labels.add(shortDate(w.getDate()));
        }
        if (entries.isEmpty()) {
            chart.clear();
            return;
        }

        BarDataSet set = new BarDataSet(entries, getString(R.string.chart_legend_rain));
        set.setColor(color(R.color.chart_rain_bar));
        set.setDrawValues(false);
        BarData data = new BarData(set);
        data.setBarWidth(0.7f);
        chart.setData(data);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getXAxis().setLabelCount(5);
        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int i = Math.round(value);
                return i >= 0 && i < labels.size() ? labels.get(i) : "";
            }
        });
        chart.getLegend().setEnabled(false);
        chart.animateY(500);
    }

    private static String shortDate(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return "";
        try {
            java.util.Date d = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate.substring(0, 10));
            return d != null ? new java.text.SimpleDateFormat("d MMM", Locale.getDefault()).format(d) : "";
        } catch (java.text.ParseException e) {
            return "";
        }
    }

    // ===== Harvest forecast: always a range, never a single number =====

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
            binding.tvHarvestForecastRange.setText(getString(R.string.harvest_forecast_range,
                    forecast.getLowPct().doubleValue(), forecast.getHighPct().doubleValue()));
            binding.tvHarvestForecastRange.setTextColor(color(R.color.rinda_secondary));
        } else {
            String reason = forecast.getReason();
            int msg;
            if ("TOO_EARLY".equalsIgnoreCase(reason)) {
                msg = R.string.harvest_too_early;
            } else if ("NOT_ENOUGH_DATA".equalsIgnoreCase(reason)) {
                msg = R.string.harvest_not_enough_data;
            } else if ("NOT_ANALYSED".equalsIgnoreCase(reason)) {
                msg = R.string.harvest_not_analysed;
            } else {
                msg = R.string.harvest_pending;
            }
            binding.tvHarvestForecastRange.setText(msg);
            binding.tvHarvestForecastRange.setTextColor(color(R.color.rinda_text_secondary));
        }
        String note = forecast.getDataQualityNote();
        String confidence = StatusUi.confidenceLabel(this, forecast.getConfidence());
        String disclaimer = getString(R.string.harvest_forecast_disclaimer) + " " + confidence + ".";
        if (note != null && !note.isEmpty()) {
            disclaimer = disclaimer + "\n" + note;
        }
        binding.tvForecastDisclaimer.setText(disclaimer);
    }

    // ===== Insurance cover =====

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
        StatusUi.Spec spec = StatusUi.forEnrollment(enrollment.getStatus());
        StatusUi.applyPill(binding.tvEnrollmentStatus, spec);
        // Season label comes only from the server; never a made-up season code.
        if ("COVERED".equalsIgnoreCase(enrollment.getStatus()) && enrollment.getSeasonCode() != null) {
            binding.tvEnrollmentStatus.setText(getString(R.string.enrollment_covered, enrollment.getSeasonCode()));
        }

        String hint = null;
        if (enrollment.getStartDate() != null && enrollment.getEndDate() != null) {
            hint = getString(R.string.enrollment_dates, DateTimeUtils.formatDate(enrollment.getStartDate()),
                    DateTimeUtils.formatDate(enrollment.getEndDate()));
        } else if (spec.meaningRes != 0) {
            hint = getString(spec.meaningRes);
        }
        binding.tvEnrollmentHint.setText(hint);
        binding.tvEnrollmentHint.setVisibility(hint != null ? View.VISIBLE : View.GONE);

        // Sum insured is a money value: show only what the server confirms (spec §0 rule 2).
        if (enrollment.getSumInsured() != null) {
            binding.rowSumInsured.tvRowValue.setText(getString(R.string.claim_amount_rwf,
                    NumberFormat.getNumberInstance(Locale.getDefault()).format(enrollment.getSumInsured())));
        } else {
            binding.rowSumInsured.tvRowValue.setText(R.string.coverage_not_confirmed);
        }
    }

    // ===== Manage =====

    private void showEditNameDialog() {
        if (currentPlot != null && (currentPlot.plantingDate == null || currentPlot.plantingDate.isEmpty())) {
            Ui.dialog(this)
                    .setTitle(R.string.rename_not_allowed_title)
                    .setMessage(R.string.rename_not_allowed_body)
                    .setPositiveButton(R.string.btn_ok, null)
                    .show();
            return;
        }

        DialogTextInputBinding input = DialogTextInputBinding.inflate(LayoutInflater.from(this));
        input.tilInput.setHint(R.string.plot_name_label);
        input.etInput.setText(currentPlot != null ? currentPlot.name : "");
        if (input.etInput.getText() != null) {
            input.etInput.setSelection(input.etInput.getText().length());
        }

        Ui.dialog(this)
                .setTitle(R.string.rename_plot_title)
                .setView(input.getRoot())
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String newName = input.etInput.getText() != null ? input.etInput.getText().toString().trim() : "";
                    if (!newName.isEmpty() && currentPlot != null) {
                        updatePlotName(newName);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
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
                            Ui.toast(binding.getRoot(), R.string.plot_updated);
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            Ui.toast(binding.getRoot(), ApiErrorParser.getFriendlyMessage(PlotDetailActivity.this, problem));
                        }
                    }

                    @Override
                    public void onFailure(Call<PlotResponse> call, Throwable t) {
                        Ui.error(binding.getRoot(), getString(R.string.error_network_action), () -> updatePlotName(newName));
                    }
                });
    }

    private void showDeleteConfirmDialog() {
        Ui.dialog(this)
                .setIcon(R.drawable.ic_delete)
                .setTitle(R.string.delete_plot_title)
                .setMessage(R.string.delete_plot_body)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> deletePlot())
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void deletePlot() {
        ApiClient.getInstance(this).getService().deletePlot(plotId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful() || response.code() == 204) {
                            final android.content.Context app = getApplicationContext();
                            new Thread(() -> RindaDatabase.getInstance(app).plotDao().deleteById(plotId)).start();
                            android.widget.Toast.makeText(app, R.string.plot_archived, android.widget.Toast.LENGTH_SHORT).show();
                            finish();
                        } else if (response.code() == 409) {
                            Ui.dialog(PlotDetailActivity.this)
                                    .setTitle(R.string.delete_deferred_title)
                                    .setMessage(R.string.error_plot_delete_deferred)
                                    .setPositiveButton(R.string.btn_ok, null)
                                    .show();
                        } else {
                            ApiProblem problem = ApiErrorParser.parse(response);
                            Ui.toast(binding.getRoot(), ApiErrorParser.getFriendlyMessage(PlotDetailActivity.this, problem));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Ui.error(binding.getRoot(), getString(R.string.error_network_action), PlotDetailActivity.this::deletePlot);
                    }
                });
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
}
