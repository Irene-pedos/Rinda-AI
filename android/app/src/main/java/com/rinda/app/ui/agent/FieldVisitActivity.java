package com.rinda.app.ui.agent;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonObject;
import com.rinda.app.R;
import com.rinda.app.data.local.OutboxEntity;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.databinding.ActivityFieldVisitBinding;
import com.rinda.app.databinding.ItemPlotChoiceBinding;
import com.rinda.app.databinding.ViewDetailRowBinding;
import com.rinda.app.ui.common.Ui;
import com.rinda.app.util.DateTimeUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FieldVisitActivity extends AppCompatActivity {

    public static final String EXTRA_PRESELECTED_PLOT_ID = "extra_preselected_plot_id";

    private static final int STEP_PLOT = 1;
    private static final int STEP_CONDITION = 2;
    private static final int STEP_CAUSE = 3;
    private static final int STEP_STAGE = 4;
    private static final int STEP_PHOTO = 5;
    private static final int STEP_REVIEW = 6;
    private static final int STEP_SUCCESS = 7;
    private static final int TOTAL_STEPS = 6;

    private ActivityFieldVisitBinding binding;
    private int step = STEP_PLOT;

    private final List<PlotEntity> plots = new ArrayList<>();
    private PlotChoiceAdapter plotAdapter;
    private PlotEntity selectedPlot = null;

    private String selectedCondition = "GOOD";
    private String selectedCause = "WATER_DEFICIT";
    private String selectedStage = "VEGETATIVE";
    private String attachedPhotoPath = null;
    // Only a real reading from the phone is ever sent: no default location, so evidence never carries a made-up place.
    private double currentLat;
    private double currentLng;
    private boolean hasGps = false;

    private ActivityResultLauncher<String> photoPickerLauncher;
    private final ActivityResultLauncher<String> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    readLocation();
                } else {
                    showNoGps(R.string.field_gps_permission_denied);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFieldVisitBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupPhotoPicker();
        setupPlotList();
        setupConditionCards();
        setupCauseCards();
        setupStageCards();
        setupGpsButton();

        binding.btnNext.setOnClickListener(v -> onNext());
        binding.btnBack.setOnClickListener(v -> goTo(step - 1));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (step > STEP_PLOT && step < STEP_SUCCESS) {
                    goTo(step - 1);
                } else {
                    finish();
                }
            }
        });

        long preselectedId = getIntent().getLongExtra(EXTRA_PRESELECTED_PLOT_ID, -1);
        loadPlots(preselectedId);

        goTo(STEP_PLOT);
    }

    private void setupPhotoPicker() {
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        savePhotoLocally(uri);
                    }
                }
        );

        binding.cardAttachPhoto.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));
        binding.btnRemovePhoto.setOnClickListener(v -> {
            attachedPhotoPath = null;
            binding.ivPhotoPreview.setImageResource(R.drawable.ic_camera);
            binding.ivPhotoPreview.setImageTintList(ContextCompat.getColorStateList(this, R.color.rinda_text_secondary));
            binding.tvPhotoTitle.setText(R.string.report_add_photo);
            binding.tvPhotoStatus.setText("Tap to capture or select field photo");
            binding.btnRemovePhoto.setVisibility(View.GONE);
        });
    }

    private void savePhotoLocally(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();

            File dir = new File(getFilesDir(), "field_visit_photos");
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, "visit_" + System.currentTimeMillis() + ".jpg");

            FileOutputStream fos = new FileOutputStream(dest);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.flush();
            fos.close();

            attachedPhotoPath = dest.getAbsolutePath();
            binding.ivPhotoPreview.setImageBitmap(bitmap);
            binding.ivPhotoPreview.setImageTintList(null);
            binding.tvPhotoTitle.setText(R.string.report_photo_attached);
            binding.tvPhotoStatus.setText(dest.getName());
            binding.btnRemovePhoto.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Ui.toast(binding.getRoot(), R.string.error_generic);
        }
    }

    private void setupPlotList() {
        plotAdapter = new PlotChoiceAdapter(plot -> {
            selectedPlot = plot;
            plotAdapter.notifyDataSetChanged();
            updateNextButtonState();
        });
        binding.rvPlots.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPlots.setAdapter(plotAdapter);
    }

    private void loadPlots(long preselectedId) {
        RindaDatabase.getInstance(this).plotDao().getAllPlotsLiveData()
                .observe(this, list -> {
                    plots.clear();
                    if (list != null && !list.isEmpty()) {
                        plots.addAll(list);
                        binding.tvPlotsEmpty.setVisibility(View.GONE);
                        binding.rvPlots.setVisibility(View.VISIBLE);
                        if (selectedPlot == null) {
                            if (preselectedId > 0) {
                                for (PlotEntity p : list) {
                                    if (p.id == preselectedId) {
                                        selectedPlot = p;
                                        break;
                                    }
                                }
                            }
                            if (selectedPlot == null) {
                                selectedPlot = list.get(0);
                            }
                        }
                    } else {
                        binding.tvPlotsEmpty.setVisibility(View.VISIBLE);
                        binding.rvPlots.setVisibility(View.GONE);
                    }
                    plotAdapter.notifyDataSetChanged();
                    updateNextButtonState();
                });
    }

    private void setupConditionCards() {
        binding.cardCondGood.setOnClickListener(v -> selectCondition("GOOD"));
        binding.cardCondStressed.setOnClickListener(v -> selectCondition("STRESSED"));
        binding.cardCondSevere.setOnClickListener(v -> selectCondition("SEVERE_LOSS"));
        binding.cardCondFailed.setOnClickListener(v -> selectCondition("FAILED"));
        selectCondition("GOOD");
    }

    private void selectCondition(String condition) {
        selectedCondition = condition;
        highlight(binding.cardCondGood, "GOOD".equals(condition));
        highlight(binding.cardCondStressed, "STRESSED".equals(condition));
        highlight(binding.cardCondSevere, "SEVERE_LOSS".equals(condition));
        highlight(binding.cardCondFailed, "FAILED".equals(condition));
    }

    private void setupCauseCards() {
        binding.cardCauseWater.setOnClickListener(v -> selectCause("WATER_DEFICIT"));
        binding.cardCauseExcess.setOnClickListener(v -> selectCause("EXCESS_WATER"));
        binding.cardCauseNutrient.setOnClickListener(v -> selectCause("NUTRIENT"));
        binding.cardCauseOther.setOnClickListener(v -> selectCause("OTHER"));
        selectCause("WATER_DEFICIT");
    }

    private void selectCause(String cause) {
        selectedCause = cause;
        highlight(binding.cardCauseWater, "WATER_DEFICIT".equals(cause));
        highlight(binding.cardCauseExcess, "EXCESS_WATER".equals(cause));
        highlight(binding.cardCauseNutrient, "NUTRIENT".equals(cause));
        highlight(binding.cardCauseOther, "OTHER".equals(cause));
    }

    private void setupStageCards() {
        binding.cardStageEmergence.setOnClickListener(v -> selectStage("EMERGENCE"));
        binding.cardStageVegetative.setOnClickListener(v -> selectStage("VEGETATIVE"));
        binding.cardStageFlowering.setOnClickListener(v -> selectStage("FLOWERING"));
        binding.cardStageGrain.setOnClickListener(v -> selectStage("GRAIN"));
        binding.cardStageMaturity.setOnClickListener(v -> selectStage("MATURITY"));
        selectStage("VEGETATIVE");
    }

    private void selectStage(String stage) {
        selectedStage = stage;
        highlight(binding.cardStageEmergence, "EMERGENCE".equals(stage));
        highlight(binding.cardStageVegetative, "VEGETATIVE".equals(stage));
        highlight(binding.cardStageFlowering, "FLOWERING".equals(stage));
        highlight(binding.cardStageGrain, "GRAIN".equals(stage));
        highlight(binding.cardStageMaturity, "MATURITY".equals(stage));
    }

    /** The location is read only when the agent taps the button at the plot, after the permission is granted. */
    private void setupGpsButton() {
        showNoGps(R.string.field_gps_not_captured);
        binding.btnCaptureGps.setOnClickListener(v -> captureGps());
    }

    private void captureGps() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            readLocation();
        } else {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void readLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc == null) {
                    loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                if (loc != null) {
                    currentLat = loc.getLatitude();
                    currentLng = loc.getLongitude();
                    hasGps = true;
                    binding.tvGpsLocation.setText(String.format(Locale.US, "GPS: %.5f, %.5f (±%.1fm)", currentLat, currentLng, loc.getAccuracy()));
                    return;
                }
            }
        } catch (SecurityException ignored) {
            // Falls through: no location is recorded.
        }
        showNoGps(R.string.field_gps_unavailable);
    }

    private void showNoGps(int messageRes) {
        hasGps = false;
        binding.tvGpsLocation.setText(messageRes);
    }

    private void highlight(MaterialCardView card, boolean selected) {
        if (selected) {
            card.setStrokeColor(ContextCompat.getColor(this, R.color.rinda_primary));
            card.setStrokeWidth(Math.round(2 * getResources().getDisplayMetrics().density));
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.rinda_primary_container));
        } else {
            card.setStrokeColor(ContextCompat.getColor(this, R.color.rinda_outline));
            card.setStrokeWidth(Math.round(1 * getResources().getDisplayMetrics().density));
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.rinda_surface));
        }
    }

    private void onNext() {
        switch (step) {
            case STEP_PLOT:
                if (selectedPlot == null) {
                    Ui.toast(binding.getRoot(), R.string.report_choose_plot_title);
                    return;
                }
                goTo(STEP_CONDITION);
                break;
            case STEP_CONDITION:
                goTo(STEP_CAUSE);
                break;
            case STEP_CAUSE:
                goTo(STEP_STAGE);
                break;
            case STEP_STAGE:
                goTo(STEP_PHOTO);
                break;
            case STEP_PHOTO:
                goTo(STEP_REVIEW);
                break;
            case STEP_REVIEW:
                submitVisit();
                break;
            case STEP_SUCCESS:
            default:
                finish();
                break;
        }
    }

    private void goTo(int newStep) {
        step = Math.max(STEP_PLOT, newStep);

        show(binding.stepPlot, step == STEP_PLOT);
        show(binding.stepCondition, step == STEP_CONDITION);
        show(binding.stepCause, step == STEP_CAUSE);
        show(binding.stepStage, step == STEP_STAGE);
        show(binding.stepPhoto, step == STEP_PHOTO);
        show(binding.stepReview, step == STEP_REVIEW);
        show(binding.stepSuccess, step == STEP_SUCCESS);

        if (step == STEP_SUCCESS) {
            binding.stepHeader.setVisibility(View.GONE);
            binding.btnBack.setVisibility(View.GONE);
            binding.btnNext.setText(R.string.btn_done);
            binding.btnNext.setIcon(null);
            binding.toolbar.setNavigationIcon(null);

            binding.ivSuccessBadge.setScaleX(0f);
            binding.ivSuccessBadge.setScaleY(0f);
            binding.ivSuccessBadge.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator(1.4f))
                    .start();
            return;
        }

        binding.stepHeader.setVisibility(View.VISIBLE);
        binding.stepProgress.setProgressCompat(step, true);
        binding.tvStepLabel.setText(getString(R.string.step_of, step, TOTAL_STEPS));

        switch (step) {
            case STEP_PLOT:
                binding.tvStepTitle.setText(R.string.field_visit_step_plot);
                binding.btnBack.setVisibility(View.GONE);
                binding.btnNext.setText(R.string.btn_next);
                binding.btnNext.setIconResource(R.drawable.ic_arrow_forward);
                break;
            case STEP_CONDITION:
                binding.tvStepTitle.setText(R.string.field_visit_step_condition);
                binding.btnBack.setVisibility(View.VISIBLE);
                binding.btnNext.setText(R.string.btn_next);
                binding.btnNext.setIconResource(R.drawable.ic_arrow_forward);
                break;
            case STEP_CAUSE:
                binding.tvStepTitle.setText(R.string.field_visit_step_cause);
                binding.btnBack.setVisibility(View.VISIBLE);
                binding.btnNext.setText(R.string.btn_next);
                binding.btnNext.setIconResource(R.drawable.ic_arrow_forward);
                break;
            case STEP_STAGE:
                binding.tvStepTitle.setText(R.string.field_visit_step_stage);
                binding.btnBack.setVisibility(View.VISIBLE);
                binding.btnNext.setText(R.string.btn_next);
                binding.btnNext.setIconResource(R.drawable.ic_arrow_forward);
                break;
            case STEP_PHOTO:
                binding.tvStepTitle.setText(R.string.field_visit_step_photo);
                binding.btnBack.setVisibility(View.VISIBLE);
                binding.btnNext.setText(R.string.btn_next);
                binding.btnNext.setIconResource(R.drawable.ic_arrow_forward);
                break;
            case STEP_REVIEW:
                binding.tvStepTitle.setText(R.string.field_visit_step_review);
                binding.btnBack.setVisibility(View.VISIBLE);
                binding.btnNext.setText(R.string.btn_submit);
                binding.btnNext.setIconResource(R.drawable.ic_check);
                fillReview();
                break;
        }
        updateNextButtonState();
    }

    private void show(View view, boolean visible) {
        if (visible) {
            Ui.reveal(view);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private void updateNextButtonState() {
        if (step == STEP_PLOT) {
            binding.btnNext.setEnabled(selectedPlot != null);
        } else {
            binding.btnNext.setEnabled(true);
        }
    }

    private void fillReview() {
        bindDetailRow(binding.rowReviewPlot, R.drawable.ic_leaf, R.string.report_review_plot,
                selectedPlot != null ? selectedPlot.name : "-");

        bindDetailRow(binding.rowReviewCondition, R.drawable.ic_status_healthy, R.string.field_condition_label,
                getConditionLabel(selectedCondition));

        bindDetailRow(binding.rowReviewCause, R.drawable.ic_status_watch, R.string.field_cause_label,
                getCauseLabel(selectedCause));

        bindDetailRow(binding.rowReviewStage, R.drawable.ic_trending, R.string.field_stage_label,
                getStageLabel(selectedStage));

        bindDetailRow(binding.rowReviewGps, R.drawable.ic_map_pin, R.string.field_visit_step_photo,
                hasGps ? String.format(Locale.US, "%.4f, %.4f", currentLat, currentLng)
                        : getString(R.string.field_gps_review_none));
    }

    private void bindDetailRow(ViewDetailRowBinding row, int iconRes, int labelRes, String value) {
        row.ivRowIcon.setImageResource(iconRes);
        row.tvRowLabel.setText(labelRes);
        row.tvRowValue.setText(value);
    }

    private String getConditionLabel(String code) {
        switch (code) {
            case "STRESSED":
                return getString(R.string.field_condition_stressed);
            case "SEVERE_LOSS":
                return getString(R.string.field_condition_severe);
            case "FAILED":
                return getString(R.string.field_condition_failed);
            case "GOOD":
            default:
                return getString(R.string.field_condition_good);
        }
    }

    private String getCauseLabel(String code) {
        switch (code) {
            case "EXCESS_WATER":
                return getString(R.string.field_cause_excess_water);
            case "NUTRIENT":
                return getString(R.string.field_cause_nutrient);
            case "OTHER":
                return getString(R.string.field_cause_other);
            case "WATER_DEFICIT":
            default:
                return getString(R.string.field_cause_water_deficit);
        }
    }

    private String getStageLabel(String code) {
        switch (code) {
            case "EMERGENCE":
                return getString(R.string.field_stage_emergence);
            case "FLOWERING":
                return getString(R.string.field_stage_flowering);
            case "GRAIN":
                return getString(R.string.field_stage_grain);
            case "MATURITY":
                return getString(R.string.field_stage_maturity);
            case "VEGETATIVE":
            default:
                return getString(R.string.field_stage_vegetative);
        }
    }

    private void submitVisit() {
        if (selectedPlot == null) return;

        String notes = binding.etNotes.getText() != null ? binding.etNotes.getText().toString().trim() : "";

        JsonObject payload = new JsonObject();
        payload.addProperty("plotId", selectedPlot.id);
        payload.addProperty("plotName", selectedPlot.name);
        payload.addProperty("plantCondition", selectedCondition);
        payload.addProperty("observedCause", selectedCause);
        payload.addProperty("growthStage", selectedStage);
        payload.addProperty("moistureChecked", binding.cbMoisture.isChecked());
        payload.addProperty("standDensityChecked", binding.cbStand.isChecked());
        payload.addProperty("farmerPresent", binding.cbFarmerPresent.isChecked());
        if (hasGps) {
            payload.addProperty("lat", currentLat);
            payload.addProperty("lng", currentLng);
        }
        payload.addProperty("notes", notes);
        payload.addProperty("visitedAt", System.currentTimeMillis());

        new Thread(() -> {
            OutboxEntity entity = new OutboxEntity("FIELD_VISIT", payload.toString(), attachedPhotoPath);
            RindaDatabase.getInstance(FieldVisitActivity.this).outboxDao().insert(entity);
            com.rinda.app.data.sync.OutboxSyncWorker.syncNow(FieldVisitActivity.this);
            runOnUiThread(() -> {
                boolean online = Ui.isOnline(FieldVisitActivity.this);
                binding.tvOfflineNotice.setVisibility(online ? View.GONE : View.VISIBLE);
                goTo(STEP_SUCCESS);
            });
        }).start();
    }

    // ===== Plot Choice Adapter =====
    class PlotChoiceAdapter extends RecyclerView.Adapter<PlotChoiceAdapter.ViewHolder> {
        private final OnPlotSelectedListener listener;

        PlotChoiceAdapter(OnPlotSelectedListener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemPlotChoiceBinding b = ItemPlotChoiceBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false
            );
            return new ViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(plots.get(position));
        }

        @Override
        public int getItemCount() {
            return plots.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemPlotChoiceBinding b;

            ViewHolder(ItemPlotChoiceBinding b) {
                super(b.getRoot());
                this.b = b;
            }

            void bind(PlotEntity p) {
                b.tvPlotName.setText(p.name);
                double area = p.areaHa != null ? p.areaHa.doubleValue() : 0.0;
                String meta = String.format(Locale.US, "%.2f ha", area);
                if (p.plantingDate != null && !p.plantingDate.isEmpty()) {
                    meta += " • Planted: " + DateTimeUtils.formatDate(p.plantingDate);
                }
                b.tvPlotMeta.setText(meta);

                boolean isSelected = selectedPlot != null && selectedPlot.id == p.id;
                b.ivSelectedCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);

                if (isSelected) {
                    b.cardPlotChoice.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.rinda_primary));
                    b.cardPlotChoice.setStrokeWidth(Math.round(2 * getResources().getDisplayMetrics().density));
                    b.cardPlotChoice.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.rinda_primary_container));
                } else {
                    b.cardPlotChoice.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.rinda_outline));
                    b.cardPlotChoice.setStrokeWidth(Math.round(1 * getResources().getDisplayMetrics().density));
                    b.cardPlotChoice.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.rinda_surface));
                }

                itemView.setOnClickListener(v -> listener.onPlotSelected(p));
            }
        }
    }

    interface OnPlotSelectedListener {
        void onPlotSelected(PlotEntity plot);
    }
}
