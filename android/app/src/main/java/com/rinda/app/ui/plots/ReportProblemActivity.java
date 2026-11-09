package com.rinda.app.ui.plots;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.rinda.app.databinding.ActivityReportProblemBinding;
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

public class ReportProblemActivity extends AppCompatActivity {

    public static final String EXTRA_PRESELECTED_PLOT_ID = "extra_preselected_plot_id";

    private static final int STEP_PLOT = 1;
    private static final int STEP_SYMPTOM = 2;
    private static final int STEP_DETAILS = 3;
    private static final int STEP_REVIEW = 4;
    private static final int STEP_SUCCESS = 5;
    private static final int TOTAL_STEPS = 4;

    private ActivityReportProblemBinding binding;
    private int step = STEP_PLOT;

    private final List<PlotEntity> plots = new ArrayList<>();
    private PlotChoiceAdapter plotAdapter;
    private PlotEntity selectedPlot = null;
    private String selectedSymptomCode = "DRY_LEAVES";
    private String attachedPhotoPath = null;

    private ActivityResultLauncher<String> photoPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportProblemBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupPhotoPicker();
        setupPlotList();
        setupSymptomCards();

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
            binding.tvPhotoStatus.setText("Tap to capture or select a photo of your field");
            binding.btnRemovePhoto.setVisibility(View.GONE);
        });
    }

    private void savePhotoLocally(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();

            File dir = new File(getFilesDir(), "problem_photos");
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, "report_" + System.currentTimeMillis() + ".jpg");

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

    private void setupSymptomCards() {
        binding.cardSymptomDry.setOnClickListener(v -> selectSymptom("DRY_LEAVES"));
        binding.cardSymptomYellow.setOnClickListener(v -> selectSymptom("YELLOW_LEAVES"));
        binding.cardSymptomFlooding.setOnClickListener(v -> selectSymptom("FLOODING"));
        binding.cardSymptomOther.setOnClickListener(v -> selectSymptom("OTHER"));
        selectSymptom("DRY_LEAVES");
    }

    private void selectSymptom(String code) {
        selectedSymptomCode = code;
        highlightCard(binding.cardSymptomDry, "DRY_LEAVES".equals(code));
        highlightCard(binding.cardSymptomYellow, "YELLOW_LEAVES".equals(code));
        highlightCard(binding.cardSymptomFlooding, "FLOODING".equals(code));
        highlightCard(binding.cardSymptomOther, "OTHER".equals(code));
    }

    private void highlightCard(MaterialCardView card, boolean selected) {
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
                goTo(STEP_SYMPTOM);
                break;
            case STEP_SYMPTOM:
                goTo(STEP_DETAILS);
                break;
            case STEP_DETAILS:
                goTo(STEP_REVIEW);
                break;
            case STEP_REVIEW:
                submitReport();
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
        show(binding.stepSymptom, step == STEP_SYMPTOM);
        show(binding.stepDetails, step == STEP_DETAILS);
        show(binding.stepReview, step == STEP_REVIEW);
        show(binding.stepSuccess, step == STEP_SUCCESS);

        if (step == STEP_SUCCESS) {
            binding.stepHeader.setVisibility(View.GONE);
            binding.btnBack.setVisibility(View.GONE);
            binding.btnNext.setText(R.string.btn_done);
            binding.btnNext.setIcon(null);
            binding.toolbar.setNavigationIcon(null);

            // Pop in success badge with haptic
            binding.ivSuccessBadge.setScaleX(0.4f);
            binding.ivSuccessBadge.setScaleY(0.4f);
            binding.ivSuccessBadge.animate().scaleX(1.0f).scaleY(1.0f).setDuration(350).start();
            binding.ivSuccessBadge.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            return;
        }

        binding.stepHeader.setVisibility(View.VISIBLE);
        binding.stepProgress.setProgressCompat(step, true);
        binding.tvStepLabel.setText(getString(R.string.step_of, step, TOTAL_STEPS));

        switch (step) {
            case STEP_PLOT:
                binding.tvStepTitle.setText(R.string.report_step_choose_plot);
                binding.btnBack.setVisibility(View.GONE);
                binding.btnNext.setText(R.string.btn_next);
                binding.btnNext.setIconResource(R.drawable.ic_arrow_forward);
                break;
            case STEP_SYMPTOM:
                binding.tvStepTitle.setText(R.string.report_step_symptom);
                binding.btnBack.setVisibility(View.VISIBLE);
                binding.btnNext.setText(R.string.btn_next);
                binding.btnNext.setIconResource(R.drawable.ic_arrow_forward);
                break;
            case STEP_DETAILS:
                binding.tvStepTitle.setText(R.string.report_step_details);
                binding.btnBack.setVisibility(View.VISIBLE);
                binding.btnNext.setText(R.string.btn_next);
                binding.btnNext.setIconResource(R.drawable.ic_arrow_forward);
                break;
            case STEP_REVIEW:
                binding.tvStepTitle.setText(R.string.report_step_review);
                binding.btnBack.setVisibility(View.VISIBLE);
                binding.btnNext.setText(R.string.btn_send_report);
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
        // Plot
        bindDetailRow(binding.rowReviewPlot, R.drawable.ic_leaf, R.string.report_review_plot,
                selectedPlot != null ? selectedPlot.name : "-");

        // Symptom
        bindDetailRow(binding.rowReviewSymptom, R.drawable.ic_status_watch, R.string.report_review_symptom,
                getSymptomLabel(selectedSymptomCode));

        // Notes
        String notes = binding.etNotes.getText() != null ? binding.etNotes.getText().toString().trim() : "";
        bindDetailRow(binding.rowReviewNotes, R.drawable.ic_info, R.string.report_review_note,
                !notes.isEmpty() ? notes : "No additional description");

        // Visit
        boolean visit = binding.switchAgentVisit.isChecked();
        bindDetailRow(binding.rowReviewVisit, R.drawable.ic_visit, R.string.report_review_visit,
                getString(visit ? R.string.report_review_visit_yes : R.string.report_review_visit_no));

        // Photo
        bindDetailRow(binding.rowReviewPhoto, R.drawable.ic_camera, R.string.report_review_photo,
                getString(attachedPhotoPath != null ? R.string.report_review_photo_yes : R.string.report_review_photo_no));
    }

    private void bindDetailRow(ViewDetailRowBinding row, int iconRes, int labelRes, String value) {
        row.ivRowIcon.setImageResource(iconRes);
        row.tvRowLabel.setText(labelRes);
        row.tvRowValue.setText(value);
    }

    private String getSymptomLabel(String code) {
        switch (code) {
            case "YELLOW_LEAVES":
                return getString(R.string.report_symptom_yellow_leaves);
            case "FLOODING":
                return getString(R.string.report_symptom_flooding);
            case "OTHER":
                return getString(R.string.report_symptom_other);
            case "DRY_LEAVES":
            default:
                return getString(R.string.report_symptom_dry_leaves);
        }
    }

    private void submitReport() {
        if (selectedPlot == null) return;

        String notes = binding.etNotes.getText() != null ? binding.etNotes.getText().toString().trim() : "";
        boolean agentVisit = binding.switchAgentVisit.isChecked();

        JsonObject payload = new JsonObject();
        payload.addProperty("plotId", selectedPlot.id);
        payload.addProperty("plotName", selectedPlot.name);
        payload.addProperty("reason", selectedSymptomCode);
        payload.addProperty("note", notes);
        payload.addProperty("agentVisitRequested", agentVisit);
        payload.addProperty("reportedAt", System.currentTimeMillis());

        // Save into local SQLite outbox queue per §4 of RINDA_PROJECT.md
        new Thread(() -> {
            OutboxEntity entity = new OutboxEntity("PROBLEM_REPORT", payload.toString(), attachedPhotoPath);
            RindaDatabase.getInstance(ReportProblemActivity.this).outboxDao().insert(entity);
            com.rinda.app.data.sync.OutboxSyncWorker.syncNow(ReportProblemActivity.this);
            runOnUiThread(() -> {
                boolean online = Ui.isOnline(ReportProblemActivity.this);
                binding.tvOfflineNotice.setVisibility(online ? View.GONE : View.VISIBLE);
                goTo(STEP_SUCCESS);
            });
        }).start();
    }

    // ===== Plot Adapter =====
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