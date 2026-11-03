package com.rinda.app.ui.claims;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.ApiErrorParser;
import com.rinda.app.data.local.ClaimEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.ClaimResponse;
import com.rinda.app.data.model.ReviewRequestDto;
import com.rinda.app.databinding.ActivityClaimDetailBinding;
import com.rinda.app.databinding.SheetAskReviewBinding;
import com.rinda.app.databinding.ViewCardHeaderBinding;
import com.rinda.app.databinding.ViewTrackerStepBinding;
import com.rinda.app.ui.common.InfoSheet;
import com.rinda.app.ui.common.StatusUi;
import com.rinda.app.ui.common.Ui;
import com.rinda.app.util.DateTimeUtils;
import java.text.NumberFormat;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Claim detail (spec §9.5). Claim status always comes from the server: this screen never
 * changes it locally (CLAUDE.md "On sync the server wins, and claim status always comes from the server").
 */
public class ClaimDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CLAIM_ID = "extra_claim_id";
    public static final String EXTRA_PLOT_NAME = "extra_plot_name";

    private enum StepState { DONE, CURRENT, FAILED, TODO }

    private ActivityClaimDetailBinding binding;
    private long claimId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClaimDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        claimId = getIntent().getLongExtra(EXTRA_CLAIM_ID, -1);
        if (claimId == -1) {
            finish();
            return;
        }
        String initialPlotName = getIntent().getStringExtra(EXTRA_PLOT_NAME);
        if (initialPlotName != null) {
            binding.tvPlotName.setText(initialPlotName);
        }

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        header(binding.progressHeader, R.drawable.ic_trending, R.string.claim_progress_title, true);
        header(binding.payoutHeader, R.drawable.ic_wallet, R.string.claim_payout_title, false);
        header(binding.whyHeader, R.drawable.ic_help, R.string.why_decision_title, false);

        step(binding.step1, R.string.claim_step_triggered, R.string.claim_step_triggered_desc, false);
        step(binding.step2, R.string.claim_step_assessment, R.string.claim_step_assessment_desc, false);
        step(binding.step3, R.string.claim_step_payout, R.string.claim_step_payout_desc, true);

        binding.swipeRefresh.setColorSchemeResources(R.color.rinda_primary);
        binding.swipeRefresh.setOnRefreshListener(this::fetchClaimFromServer);

        binding.btnAskReview.setOnClickListener(v -> showAskReviewSheet());

        observeLocalClaim();
        fetchClaimFromServer();
    }

    private void header(ViewCardHeaderBinding h, int iconRes, int titleRes, boolean help) {
        h.ivHeaderIcon.setImageResource(iconRes);
        h.tvHeaderTitle.setText(titleRes);
        if (help) {
            h.btnHeaderHelp.setVisibility(View.VISIBLE);
            InfoSheet.bind(h.btnHeaderHelp, R.string.help_claims_title, R.string.help_claims_body);
        }
    }

    private void step(ViewTrackerStepBinding s, int titleRes, int descRes, boolean last) {
        s.tvStepTitle.setText(titleRes);
        s.tvStepDesc.setText(descRes);
        s.vStepLine.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
    }

    private void observeLocalClaim() {
        RindaDatabase.getInstance(this).claimDao().getClaimByIdLiveData(claimId)
                .observe(this, claim -> {
                    if (claim != null) {
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
        binding.tvPlotName.setText(claim.plotName != null ? claim.plotName : getString(R.string.plot_number, claim.plotId));
        // Season: only the id the server gives. Season codes and dates are not confirmed yet (spec §10.3).
        if (claim.seasonId > 0) {
            binding.tvSeason.setText(getString(R.string.claim_season, claim.seasonId));
            binding.tvSeason.setVisibility(View.VISIBLE);
        } else {
            binding.tvSeason.setVisibility(View.GONE);
        }

        binding.tvTriggerDate.setText(claim.triggerDate != null && !claim.triggerDate.isEmpty()
                ? getString(R.string.claim_triggered_on, DateTimeUtils.formatDate(claim.triggerDate))
                : getString(R.string.claim_trigger_date_pending));

        binding.cardDemoNotice.setVisibility(claim.isDemo ? View.VISIBLE : View.GONE);

        String status = claim.status != null ? claim.status : "UNDER_REVIEW";
        StatusUi.Spec spec = StatusUi.forClaim(status);
        StatusUi.applyPill(binding.tvStatusBadge, spec);
        binding.tvStatusMeaning.setText(spec.meaningRes);

        updateTracker(status);

        // Money: only server values; null = not decided (spec §0 rule 2)
        if (claim.amount != null) {
            binding.tvPayoutAmount.setText(getString(R.string.claim_amount_rwf,
                    NumberFormat.getNumberInstance(Locale.getDefault()).format(claim.amount)));
            binding.tvPayoutAmount.setTextColor(ContextCompat.getColor(this, R.color.rinda_text_primary));
        } else {
            binding.tvPayoutAmount.setText(R.string.claim_amount_pending);
            binding.tvPayoutAmount.setTextColor(ContextCompat.getColor(this, R.color.rinda_text_secondary));
        }
        binding.tvPayoutRateText.setText(claim.payoutRate != null
                ? getString(R.string.claim_rate_confirmed, claim.payoutRate.doubleValue() * 100.0)
                : getString(R.string.claim_rate_pending));

        binding.tvEvidenceDetails.setText(formatEvidenceSnapshot(claim.evidenceSnapshotJson));

        if (claim.officerReason != null && !claim.officerReason.trim().isEmpty()) {
            binding.tvOfficerAssessment.setText(claim.officerReason);
            if (claim.officerDecision != null) {
                binding.tvOfficerDate.setText(getString(R.string.officer_decision,
                        getString(StatusUi.forClaim(claim.officerDecision).labelRes)));
                binding.tvOfficerDate.setVisibility(View.VISIBLE);
            } else {
                binding.tvOfficerDate.setVisibility(View.GONE);
            }
        } else {
            binding.tvOfficerAssessment.setText(R.string.officer_pending);
            binding.tvOfficerDate.setVisibility(View.GONE);
        }

        if ("REVIEW_REQUESTED".equalsIgnoreCase(status)) {
            binding.btnAskReview.setEnabled(false);
            binding.btnAskReview.setText(R.string.review_in_progress);
        } else if ("PAID".equalsIgnoreCase(status) || "FINAL".equalsIgnoreCase(status)) {
            binding.btnAskReview.setEnabled(false);
            binding.btnAskReview.setText(R.string.claim_closed);
        } else {
            binding.btnAskReview.setEnabled(true);
            binding.btnAskReview.setText(R.string.btn_request_review);
        }
    }

    /** Vertical tracker: Started → Officer checks → Payout. Each step has an icon, not just a colour. */
    private void updateTracker(String status) {
        StepState s2;
        StepState s3;
        switch (status) {
            case "APPROVED":
                s2 = StepState.DONE;
                s3 = StepState.CURRENT;
                break;
            case "PAID":
            case "FINAL":
                s2 = StepState.DONE;
                s3 = StepState.DONE;
                break;
            case "DECLINED":
                s2 = StepState.FAILED;
                s3 = StepState.TODO;
                break;
            case "UNDER_REVIEW":
            case "FIELD_VISIT_REQUESTED":
            case "REVIEW_REQUESTED":
            default:
                s2 = StepState.CURRENT;
                s3 = StepState.TODO;
                break;
        }
        paint(binding.step1, StepState.DONE, s2 != StepState.TODO);
        paint(binding.step2, s2, s3 != StepState.TODO);
        paint(binding.step3, s3, false);
    }

    private void paint(@NonNull ViewTrackerStepBinding s, @NonNull StepState state, boolean lineDone) {
        int icon;
        int fg;
        int bg;
        switch (state) {
            case DONE:
                icon = R.drawable.ic_check;
                fg = R.color.rinda_on_primary;
                bg = R.color.rinda_primary;
                break;
            case CURRENT:
                icon = R.drawable.ic_status_pending;
                fg = R.color.status_pending;
                bg = R.color.status_pending_bg;
                break;
            case FAILED:
                icon = R.drawable.ic_close;
                fg = R.color.status_at_risk;
                bg = R.color.status_at_risk_bg;
                break;
            case TODO:
            default:
                icon = R.drawable.ic_lock;
                fg = R.color.rinda_text_tertiary;
                bg = R.color.rinda_surface_variant;
                break;
        }
        s.ivStepIcon.setImageResource(icon);
        s.ivStepIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, fg)));
        s.ivStepIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, bg)));
        s.tvStepTitle.setTextColor(ContextCompat.getColor(this,
                state == StepState.TODO ? R.color.rinda_text_tertiary : R.color.rinda_text_primary));
        s.vStepLine.setBackgroundColor(ContextCompat.getColor(this, lineDone ? R.color.rinda_primary : R.color.rinda_outline));
    }

    /** Evidence in plain words. If the snapshot is missing, say so: never invent evidence. */
    private String formatEvidenceSnapshot(String json) {
        if (json != null && !json.trim().isEmpty()) {
            try {
                JsonElement el = JsonParser.parseString(json);
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    StringBuilder sb = new StringBuilder();
                    if (obj.has("triggerReason") && !obj.get("triggerReason").isJsonNull()) {
                        sb.append("• ").append(obj.get("triggerReason").getAsString()).append("\n");
                    }
                    if (obj.has("ndviAnomaly") && !obj.get("ndviAnomaly").isJsonNull()) {
                        sb.append("• ").append(getString(R.string.evidence_ndvi_anomaly, obj.get("ndviAnomaly").getAsString())).append("\n");
                    }
                    if (obj.has("rainDeficitPct") && !obj.get("rainDeficitPct").isJsonNull()) {
                        sb.append("• ").append(getString(R.string.evidence_rain_deficit, obj.get("rainDeficitPct").getAsString())).append("\n");
                    }
                    if (sb.length() > 0) {
                        return sb.toString().trim();
                    }
                }
            } catch (RuntimeException ignored) {
                // Fall through to "not available"
            }
        }
        return getString(R.string.evidence_unavailable);
    }

    // ===== Ask for a review (bottom sheet, spec §9.5) =====

    private void showAskReviewSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        SheetAskReviewBinding s = SheetAskReviewBinding.inflate(LayoutInflater.from(this));
        sheet.setContentView(s.getRoot());
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);

        s.btnCancel.setOnClickListener(v -> sheet.dismiss());
        s.btnSend.setOnClickListener(v -> {
            String message = s.etMessage.getText() != null ? s.etMessage.getText().toString().trim() : "";
            if (message.isEmpty()) {
                s.tilMessage.setError(getString(R.string.ask_review_empty));
                return;
            }
            s.tilMessage.setError(null);
            s.progressBar.setVisibility(View.VISIBLE);
            s.btnSend.setEnabled(false);
            submitReviewRequest(message, sheet, s);
        });
        sheet.show();
        s.etMessage.requestFocus();
    }

    private void submitReviewRequest(String message, BottomSheetDialog sheet, SheetAskReviewBinding s) {
        ApiClient.getInstance(this).getService().requestClaimReview(claimId, new ReviewRequestDto(message))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        s.progressBar.setVisibility(View.GONE);
                        s.btnSend.setEnabled(true);
                        if (response.isSuccessful()) {
                            sheet.dismiss();
                            // The new status comes from the server, not from the phone.
                            fetchClaimFromServer();
                            Ui.dialog(ClaimDetailActivity.this)
                                    .setIcon(R.drawable.ic_status_healthy)
                                    .setTitle(R.string.ask_review_sent_title)
                                    .setMessage(R.string.ask_review_sent_body)
                                    .setPositiveButton(R.string.btn_done, null)
                                    .show();
                        } else {
                            s.tilMessage.setError(ApiErrorParser.getFriendlyMessage(ClaimDetailActivity.this,
                                    ApiErrorParser.parse(response)));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        // Not sent: say so. The text stays in the box so the farmer can try again.
                        s.progressBar.setVisibility(View.GONE);
                        s.btnSend.setEnabled(true);
                        s.tilMessage.setError(getString(R.string.ask_review_failed));
                    }
                });
    }

    // ===== Cache =====

    /** Maps a server claim to the local cache row. Shared with ClaimsFragment. */
    static ClaimEntity toEntity(ClaimResponse r) {
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
        return e;
    }

    private void saveClaimLocally(ClaimResponse r) {
        final android.content.Context app = getApplicationContext();
        new Thread(() -> RindaDatabase.getInstance(app).claimDao().insertOrUpdate(toEntity(r))).start();
    }
}
