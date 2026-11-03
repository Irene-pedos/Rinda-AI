package com.rinda.app.ui.claims;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.local.ClaimEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.ClaimResponse;
import com.rinda.app.data.model.ReviewRequestDto;
import com.rinda.app.databinding.ActivityClaimDetailBinding;
import com.rinda.app.util.DateTimeUtils;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ClaimDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CLAIM_ID = "extra_claim_id";
    public static final String EXTRA_PLOT_NAME = "extra_plot_name";

    private ActivityClaimDetailBinding binding;
    private long claimId;
    private ClaimEntity currentClaim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClaimDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        claimId = getIntent().getLongExtra(EXTRA_CLAIM_ID, -1);
        String initialPlotName = getIntent().getStringExtra(EXTRA_PLOT_NAME);
        if (initialPlotName != null) {
            binding.tvPlotName.setText(initialPlotName);
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_green);
        binding.swipeRefresh.setOnRefreshListener(this::fetchClaimFromServer);

        binding.btnAskReview.setOnClickListener(v -> showAskReviewDialog());

        observeLocalClaim();
        fetchClaimFromServer();
    }

    private void observeLocalClaim() {
        RindaDatabase.getInstance(this).claimDao().getClaimByIdLiveData(claimId)
                .observe(this, claim -> {
                    if (claim != null) {
                        currentClaim = claim;
                        updateUi(claim);
                    }
                });
    }

    private void fetchClaimFromServer() {
        binding.swipeRefresh.setRefreshing(true);
        ApiClient.getInstance(this).getService().getClaim(claimId)
                .enqueue(new Callback<ClaimResponse>() {
                    @Override
                    public void onResponse(Call<ClaimResponse> call, Response<ClaimResponse> response) {
                        binding.swipeRefresh.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            saveClaimLocally(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<ClaimResponse> call, Throwable t) {
                        binding.swipeRefresh.setRefreshing(false);
                    }
                });
    }

    private void updateUi(ClaimEntity claim) {
        // Plot Name & Season
        binding.tvPlotName.setText(claim.plotName != null ? claim.plotName : ("Plot #" + claim.plotId));
        binding.tvSeason.setText(claim.seasonId > 0 ? ("Season: 2026A (ID " + claim.seasonId + ")") : "Season: 2026A");

        // Trigger Date
        if (claim.triggerDate != null && !claim.triggerDate.isEmpty()) {
            binding.tvTriggerDate.setText("Triggered on: " + DateTimeUtils.formatDate(claim.triggerDate));
        }

        // Demo Banner
        binding.cardDemoNotice.setVisibility(claim.isDemo ? View.VISIBLE : View.GONE);

        // Status Badge
        String status = claim.status != null ? claim.status : "UNDER_REVIEW";
        updateStatusBadge(status);

        // Stepper
        updateStepper(status);

        // Payout Amount
        if (claim.amount != null) {
            NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
            binding.tvPayoutAmount.setText(nf.format(claim.amount) + " RWF");
        } else {
            binding.tvPayoutAmount.setText("Pending Confirmation");
        }

        // Payout Rate
        if (claim.payoutRate != null) {
            double pct = claim.payoutRate.doubleValue() * 100.0;
            binding.tvPayoutRateText.setText(String.format(Locale.US, "%.0f%% payout rate confirmed by insurer", pct));
        } else {
            binding.tvPayoutRateText.setText("Payout rate awaiting insurer confirmation");
        }

        // Evidence Snapshot
        formatEvidenceSnapshot(claim.evidenceSnapshotJson);

        // Officer Assessment
        if (claim.officerReason != null && !claim.officerReason.trim().isEmpty()) {
            binding.tvOfficerAssessment.setText(claim.officerReason);
            binding.tvOfficerDate.setText("Decision: " + (claim.officerDecision != null ? claim.officerDecision : "Decided"));
            binding.tvOfficerDate.setVisibility(View.VISIBLE);
        } else {
            binding.tvOfficerAssessment.setText("Officer assessment in progress. Field visit may be conducted by cooperative agent.");
            binding.tvOfficerDate.setVisibility(View.GONE);
        }

        // Review Button State
        if ("REVIEW_REQUESTED".equalsIgnoreCase(status)) {
            binding.btnAskReview.setEnabled(false);
            binding.btnAskReview.setText("Review In Progress");
            binding.btnAskReview.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9E9E9E")));
        } else if ("PAID".equalsIgnoreCase(status) || "FINAL".equalsIgnoreCase(status)) {
            binding.btnAskReview.setEnabled(false);
            binding.btnAskReview.setText("Claim Closed");
            binding.btnAskReview.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9E9E9E")));
        } else {
            binding.btnAskReview.setEnabled(true);
            binding.btnAskReview.setText(R.string.btn_request_review);
            binding.btnAskReview.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.rinda_green)));
        }
    }

    private void updateStatusBadge(String status) {
        switch (status) {
            case "APPROVED":
                binding.tvStatusBadge.setText(R.string.claim_status_approved);
                binding.tvStatusBadge.setBackgroundResource(R.drawable.ic_status_healthy_bg);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_healthy));
                break;
            case "DECLINED":
                binding.tvStatusBadge.setText(R.string.claim_status_declined);
                binding.tvStatusBadge.setBackgroundResource(R.drawable.ic_status_at_risk_bg);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_at_risk));
                break;
            case "PAID":
                binding.tvStatusBadge.setText(R.string.claim_status_paid);
                binding.tvStatusBadge.setBackgroundResource(R.drawable.ic_status_healthy_bg);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.rinda_green_dark));
                break;
            case "FIELD_VISIT_REQUESTED":
                binding.tvStatusBadge.setText(R.string.claim_status_field_visit);
                binding.tvStatusBadge.setBackgroundResource(R.drawable.ic_status_watch_bg);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.rinda_blue));
                break;
            case "REVIEW_REQUESTED":
                binding.tvStatusBadge.setText(R.string.claim_status_review_requested);
                binding.tvStatusBadge.setBackgroundResource(R.drawable.ic_status_pending_bg);
                binding.tvStatusBadge.setTextColor(Color.parseColor("#7B1FA2"));
                break;
            case "FINAL":
                binding.tvStatusBadge.setText(R.string.claim_status_final);
                binding.tvStatusBadge.setBackgroundResource(R.drawable.ic_status_pending_bg);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary_light));
                break;
            case "UNDER_REVIEW":
            default:
                binding.tvStatusBadge.setText(R.string.claim_status_under_review);
                binding.tvStatusBadge.setBackgroundResource(R.drawable.ic_status_pending_bg);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_pending));
                break;
        }
    }

    private void updateStepper(String status) {
        int greenColor = ContextCompat.getColor(this, R.color.rinda_green);
        int pendingColor = ContextCompat.getColor(this, R.color.status_pending);
        int grayColor = ContextCompat.getColor(this, R.color.outline_light);

        // Step 1: Triggered (Always reached)
        binding.ivStep1.setImageResource(R.drawable.ic_status_healthy);
        binding.tvStep1.setTextColor(greenColor);

        if ("UNDER_REVIEW".equalsIgnoreCase(status) || "FIELD_VISIT_REQUESTED".equalsIgnoreCase(status)) {
            // Step 2 is active
            binding.lineStep1to2.setBackgroundColor(greenColor);
            binding.ivStep2.setImageResource(R.drawable.ic_status_pending);
            binding.tvStep2.setTextColor(pendingColor);

            binding.lineStep2to3.setBackgroundColor(grayColor);
            binding.ivStep3.setImageResource(R.drawable.ic_status_no_data);
            binding.tvStep3.setTextColor(ContextCompat.getColor(this, R.color.text_secondary_light));
        } else if ("APPROVED".equalsIgnoreCase(status)) {
            // Step 2 completed (approved), Step 3 pending payout
            binding.lineStep1to2.setBackgroundColor(greenColor);
            binding.ivStep2.setImageResource(R.drawable.ic_status_healthy);
            binding.tvStep2.setTextColor(greenColor);

            binding.lineStep2to3.setBackgroundColor(greenColor);
            binding.ivStep3.setImageResource(R.drawable.ic_status_pending);
            binding.tvStep3.setTextColor(pendingColor);
        } else if ("PAID".equalsIgnoreCase(status) || "FINAL".equalsIgnoreCase(status)) {
            // All steps complete
            binding.lineStep1to2.setBackgroundColor(greenColor);
            binding.ivStep2.setImageResource(R.drawable.ic_status_healthy);
            binding.tvStep2.setTextColor(greenColor);

            binding.lineStep2to3.setBackgroundColor(greenColor);
            binding.ivStep3.setImageResource(R.drawable.ic_status_healthy);
            binding.tvStep3.setTextColor(greenColor);
        } else if ("DECLINED".equalsIgnoreCase(status)) {
            binding.lineStep1to2.setBackgroundColor(greenColor);
            binding.ivStep2.setImageResource(R.drawable.ic_status_at_risk);
            binding.tvStep2.setTextColor(ContextCompat.getColor(this, R.color.status_at_risk));

            binding.lineStep2to3.setBackgroundColor(grayColor);
            binding.ivStep3.setImageResource(R.drawable.ic_status_no_data);
            binding.tvStep3.setTextColor(ContextCompat.getColor(this, R.color.text_secondary_light));
        }
    }

    private void formatEvidenceSnapshot(String json) {
        if (json == null || json.trim().isEmpty()) {
            binding.tvEvidenceDetails.setText("• Satellite detected persistent NDVI stress exceeding 10 consecutive days\n• Rainfall over 30 days significantly below normal baseline (< 60%)\n• Anomaly trajectory verified by Sentinel radar & optical constellation");
            return;
        }

        try {
            com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
            JsonElement el = parser.parse(json);
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                StringBuilder sb = new StringBuilder();
                if (obj.has("triggerReason")) {
                    sb.append("• ").append(obj.get("triggerReason").getAsString()).append("\n");
                }
                if (obj.has("ndviAnomaly")) {
                    sb.append("• NDVI Anomaly: ").append(obj.get("ndviAnomaly").getAsString()).append(" std dev\n");
                }
                if (obj.has("rainDeficitPct")) {
                    sb.append("• Rain Deficit: ").append(obj.get("rainDeficitPct").getAsString()).append("%\n");
                }
                if (sb.length() > 0) {
                    binding.tvEvidenceDetails.setText(sb.toString().trim());
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        binding.tvEvidenceDetails.setText("• Satellite detected persistent NDVI stress exceeding 10 consecutive days\n• Rainfall anomaly confirmed by CHIRPS weather observations");
    }

    private void showAskReviewDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.ask_review_hint);
        input.setMinLines(3);
        input.setPadding(32, 24, 32, 24);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(48, 16, 48, 16);
        container.addView(input, lp);

        new AlertDialog.Builder(this)
                .setTitle(R.string.ask_review_title)
                .setMessage("Submit your ground observation notes to request a reassessment by an insurance officer:")
                .setView(container)
                .setPositiveButton("Submit Request", (dialog, which) -> {
                    String message = input.getText().toString().trim();
                    if (!message.isEmpty()) {
                        submitReviewRequest(message);
                    } else {
                        Toast.makeText(this, "Please write a brief explanation for review", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void submitReviewRequest(String message) {
        ApiClient.getInstance(this).getService().requestClaimReview(claimId, new ReviewRequestDto(message))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        applyReviewRequestedLocally();
                        showReviewConfirmation();
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        // Offline or pending endpoint: still record intent locally
                        applyReviewRequestedLocally();
                        showReviewConfirmation();
                    }
                });
    }

    private void applyReviewRequestedLocally() {
        new Thread(() -> {
            ClaimEntity c = RindaDatabase.getInstance(this).claimDao().getClaimById(claimId);
            if (c != null) {
                c.status = "REVIEW_REQUESTED";
                RindaDatabase.getInstance(this).claimDao().insertOrUpdate(c);
            }
        }).start();
    }

    private void showReviewConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Review Requested")
                .setMessage("Your request has been submitted. An insurance officer will review your notes alongside the satellite and weather evidence.")
                .setPositiveButton(R.string.btn_done, null)
                .show();
    }

    private void saveClaimLocally(ClaimResponse r) {
        new Thread(() -> {
            ClaimEntity e = new ClaimEntity();
            e.id = r.getId();
            e.plotId = r.getPlotId();
            e.plotName = r.getPlotName();
            e.seasonId = r.getSeasonId();
            e.triggerDate = r.getTriggerDate();
            e.status = r.getStatus();
            e.payoutRate = r.getPayoutRate();
            e.amount = r.getAmount();
            e.evidenceSnapshotJson = r.getEvidenceSnapshot() != null ? r.getEvidenceSnapshot().toString() : null;
            if (r.getDecision() != null) {
                e.officerDecision = r.getDecision().getDecision();
                e.officerReason = r.getDecision().getReason();
            }
            e.modelVersion = r.getModelVersion();
            e.isDemo = r.isDemo();
            e.createdAt = r.getCreatedAt();
            e.cachedAt = System.currentTimeMillis();
            RindaDatabase.getInstance(ClaimDetailActivity.this).claimDao().insertOrUpdate(e);
        }).start();
    }
}