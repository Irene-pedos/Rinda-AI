package com.rinda.app.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import com.rinda.app.R;
import com.rinda.app.data.model.PlotStatus;

/**
 * The one place that maps a status to its icon, word, colours and plain-language meaning.
 *
 * Spec §5.3: status is never shown by colour alone, so every status has an icon AND a word.
 * Spec §2.2 #7: plot health and claim status are different things, so they have separate methods.
 * Use {@link #applyPill(TextView, Spec)} for the small pill, and read {@link Spec#meaningRes}
 * for the sentence that explains it to the farmer.
 */
public final class StatusUi {

    private StatusUi() {
    }

    /** How one status looks and what it means. */
    public static final class Spec {
        @StringRes public final int labelRes;
        @DrawableRes public final int iconRes;
        @ColorRes public final int fgColorRes;
        @ColorRes public final int bgColorRes;
        @StringRes public final int meaningRes;
        /** "What you can do" sentence, or 0 when there is none. */
        @StringRes public final int todoRes;

        Spec(int labelRes, int iconRes, int fgColorRes, int bgColorRes, int meaningRes, int todoRes) {
            this.labelRes = labelRes;
            this.iconRes = iconRes;
            this.fgColorRes = fgColorRes;
            this.bgColorRes = bgColorRes;
            this.meaningRes = meaningRes;
            this.todoRes = todoRes;
        }
    }

    /** Crop health from the ML service: HEALTHY / WATCH / AT_RISK / NOT_ENOUGH_DATA. Null = not analysed yet. */
    @NonNull
    public static Spec forHealth(@Nullable String status) {
        if (status == null) {
            return new Spec(R.string.health_not_analysed, R.drawable.ic_status_no_data,
                    R.color.status_not_enough_data, R.color.status_not_enough_data_bg,
                    R.string.health_not_analysed_meaning, 0);
        }
        switch (status) {
            case "HEALTHY":
                return new Spec(R.string.health_healthy, R.drawable.ic_status_healthy,
                        R.color.status_healthy, R.color.status_healthy_bg,
                        R.string.health_healthy_meaning, R.string.health_healthy_todo);
            case "WATCH":
                return new Spec(R.string.health_watch, R.drawable.ic_status_watch,
                        R.color.status_watch, R.color.status_watch_bg,
                        R.string.health_watch_meaning, R.string.health_watch_todo);
            case "AT_RISK":
                return new Spec(R.string.health_at_risk, R.drawable.ic_status_at_risk,
                        R.color.status_at_risk, R.color.status_at_risk_bg,
                        R.string.health_at_risk_meaning, R.string.health_at_risk_todo);
            case "NOT_ENOUGH_DATA":
            default:
                // Unknown values are treated as "not enough data": never guess a status.
                return new Spec(R.string.health_not_enough_data, R.drawable.ic_status_no_data,
                        R.color.status_not_enough_data, R.color.status_not_enough_data_bg,
                        R.string.health_no_data_meaning, R.string.health_no_data_todo);
        }
    }

    /** Plot verification status. This is not crop health and not insurance cover. */
    @NonNull
    public static Spec forPlot(@Nullable PlotStatus status) {
        PlotStatus s = status != null ? status : PlotStatus.PENDING_VERIFICATION;
        switch (s) {
            case ACTIVE:
                return new Spec(R.string.plot_status_active, R.drawable.ic_status_healthy,
                        R.color.status_healthy, R.color.status_healthy_bg,
                        R.string.plot_status_active_meaning, 0);
            case REJECTED:
                return new Spec(R.string.plot_status_rejected, R.drawable.ic_status_at_risk,
                        R.color.status_at_risk, R.color.status_at_risk_bg,
                        R.string.plot_status_rejected_meaning, 0);
            case ARCHIVED:
                return new Spec(R.string.plot_status_archived, R.drawable.ic_status_no_data,
                        R.color.status_not_enough_data, R.color.status_not_enough_data_bg,
                        R.string.plot_status_archived_meaning, 0);
            case PENDING_VERIFICATION:
            default:
                return new Spec(R.string.plot_status_pending, R.drawable.ic_status_pending,
                        R.color.status_pending, R.color.status_pending_bg,
                        R.string.plot_status_pending_meaning, 0);
        }
    }

    /** Claim status. Always comes from the server; never set it on the phone. */
    @NonNull
    public static Spec forClaim(@Nullable String status) {
        String s = status != null ? status : "UNDER_REVIEW";
        switch (s) {
            case "APPROVED":
                return new Spec(R.string.claim_status_approved, R.drawable.ic_status_healthy,
                        R.color.status_healthy, R.color.status_healthy_bg,
                        R.string.claim_status_approved_meaning, 0);
            case "DECLINED":
                return new Spec(R.string.claim_status_declined, R.drawable.ic_status_at_risk,
                        R.color.status_at_risk, R.color.status_at_risk_bg,
                        R.string.claim_status_declined_meaning, 0);
            case "PAID":
                return new Spec(R.string.claim_status_paid, R.drawable.ic_wallet,
                        R.color.status_healthy, R.color.status_healthy_bg,
                        R.string.claim_status_paid_meaning, 0);
            case "FIELD_VISIT_REQUESTED":
                return new Spec(R.string.claim_status_field_visit, R.drawable.ic_visit,
                        R.color.status_watch, R.color.status_watch_bg,
                        R.string.claim_status_field_visit_meaning, 0);
            case "REVIEW_REQUESTED":
                return new Spec(R.string.claim_status_review_requested, R.drawable.ic_sync,
                        R.color.status_pending, R.color.status_pending_bg,
                        R.string.claim_status_review_requested_meaning, 0);
            case "FINAL":
                return new Spec(R.string.claim_status_final, R.drawable.ic_lock,
                        R.color.status_not_enough_data, R.color.status_not_enough_data_bg,
                        R.string.claim_status_final_meaning, 0);
            case "UNDER_REVIEW":
            default:
                return new Spec(R.string.claim_status_under_review, R.drawable.ic_status_pending,
                        R.color.status_pending, R.color.status_pending_bg,
                        R.string.claim_status_under_review_meaning, 0);
        }
    }

    /** Insurance cover for a plot this season (spec §2.2 #5): COVERED / PENDING / CANCELLED / NOT_ENROLLED. */
    @NonNull
    public static Spec forEnrollment(@Nullable String status) {
        String s = status != null ? status.toUpperCase(java.util.Locale.ROOT) : "NOT_ENROLLED";
        switch (s) {
            case "COVERED":
                return new Spec(R.string.enrollment_covered_no_season, R.drawable.ic_shield,
                        R.color.status_healthy, R.color.status_healthy_bg, 0, 0);
            case "PENDING":
                return new Spec(R.string.enrollment_pending, R.drawable.ic_status_pending,
                        R.color.status_pending, R.color.status_pending_bg, 0, 0);
            case "CANCELLED":
                return new Spec(R.string.enrollment_cancelled, R.drawable.ic_close,
                        R.color.status_at_risk, R.color.status_at_risk_bg, 0, 0);
            case "NOT_ENROLLED":
            default:
                return new Spec(R.string.enrollment_not_enrolled, R.drawable.ic_status_no_data,
                        R.color.status_not_enough_data, R.color.status_not_enough_data_bg,
                        R.string.enrollment_not_enrolled_hint, 0);
        }
    }

    /** Confidence label. LOW always says that a person will check (spec §3). */
    @NonNull
    public static String confidenceLabel(@NonNull Context context, @Nullable String confidence) {
        if (confidence == null) {
            return context.getString(R.string.confidence_low);
        }
        switch (confidence.toUpperCase(java.util.Locale.ROOT)) {
            case "HIGH":
                return context.getString(R.string.confidence_high);
            case "MEDIUM":
                return context.getString(R.string.confidence_medium);
            case "LOW":
                return context.getString(R.string.confidence_low);
            default:
                return context.getString(R.string.confidence_unknown, confidence);
        }
    }

    /** Styles a TextView that uses @style/Widget.Rinda.StatusPill: icon + word + tinted background. */
    public static void applyPill(@NonNull TextView pill, @NonNull Spec spec) {
        Context context = pill.getContext();
        int fg = ContextCompat.getColor(context, spec.fgColorRes);
        int bg = ContextCompat.getColor(context, spec.bgColorRes);
        pill.setText(spec.labelRes);
        pill.setTextColor(fg);
        pill.setBackgroundTintList(ColorStateList.valueOf(bg));
        Drawable icon = ContextCompat.getDrawable(context, spec.iconRes);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon.mutate());
            int size = context.getResources().getDimensionPixelSize(R.dimen.icon_sm);
            icon.setBounds(0, 0, size, size);
            DrawableCompat.setTint(icon, fg);
        }
        pill.setCompoundDrawablesRelative(icon, null, null, null);
    }

    /** Colours a round icon badge (Widget.Rinda.IconBadge) to match a status. */
    public static void applyBadge(@NonNull android.widget.ImageView badge, @NonNull Spec spec) {
        Context context = badge.getContext();
        badge.setImageResource(spec.iconRes);
        badge.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, spec.fgColorRes)));
        badge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, spec.bgColorRes)));
    }
}
