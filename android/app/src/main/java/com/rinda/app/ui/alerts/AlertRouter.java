package com.rinda.app.ui.alerts;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.local.NotificationEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.ui.claims.ClaimDetailActivity;
import com.rinda.app.ui.common.StatusUi;
import com.rinda.app.ui.common.Ui;
import com.rinda.app.ui.plots.PlotDetailActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Opens the right screen for an alert, and the look of each alert type.
 * Spec §2.2 #7: HEALTH alerts open Plot detail; CLAIM updates open Claim detail. Never mix them.
 */
public final class AlertRouter {

    private AlertRouter() {
    }

    public static void open(@NonNull Context context, @NonNull NotificationEntity item) {
        markRead(context, item);

        String type = item.type != null ? item.type : "SYSTEM";
        if ("HEALTH".equalsIgnoreCase(type)) {
            long plotId = item.plotId != null ? item.plotId : parseId(item.link, "/plots/");
            if (plotId > 0) {
                Intent intent = new Intent(context, PlotDetailActivity.class);
                intent.putExtra(PlotDetailActivity.EXTRA_PLOT_ID, plotId);
                intent.putExtra(PlotDetailActivity.EXTRA_PLOT_NAME, item.plotName);
                context.startActivity(intent);
                return;
            }
        } else if ("CLAIM".equalsIgnoreCase(type)) {
            long claimId = parseId(item.link, "/claims/");
            if (claimId > 0) {
                Intent intent = new Intent(context, ClaimDetailActivity.class);
                intent.putExtra(ClaimDetailActivity.EXTRA_CLAIM_ID, claimId);
                intent.putExtra(ClaimDetailActivity.EXTRA_PLOT_NAME, item.plotName);
                context.startActivity(intent);
                return;
            }
        }
        // System notices, or a link we cannot open: show the message itself.
        Ui.dialog(context)
                .setTitle(item.title != null ? item.title : context.getString(R.string.alert_fallback_title))
                .setMessage(item.body != null ? item.body : "")
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    /** Title in the user's language for known codes, else the server title. */
    @NonNull
    public static String title(@NonNull Context context, @NonNull NotificationEntity item) {
        if ("HEALTH_AT_RISK".equalsIgnoreCase(item.code)) {
            return context.getString(R.string.alert_title_at_risk);
        } else if ("HEALTH_WATCH".equalsIgnoreCase(item.code)) {
            return context.getString(R.string.alert_title_watch);
        } else if ("HEALTH_RECOVERED".equalsIgnoreCase(item.code)) {
            return context.getString(R.string.alert_title_recovered);
        }
        return item.title != null ? item.title : context.getString(R.string.alert_fallback_title);
    }

    /** Icon and colours for an alert, so the type is shown by icon, not colour alone. */
    @NonNull
    public static StatusUi.Spec look(@NonNull NotificationEntity item) {
        String type = item.type != null ? item.type : "SYSTEM";
        if ("HEALTH".equalsIgnoreCase(type)) {
            if ("HEALTH_AT_RISK".equalsIgnoreCase(item.code)) {
                return StatusUi.forHealth("AT_RISK");
            } else if ("HEALTH_WATCH".equalsIgnoreCase(item.code)) {
                return StatusUi.forHealth("WATCH");
            } else if ("HEALTH_RECOVERED".equalsIgnoreCase(item.code)) {
                return StatusUi.forHealth("HEALTHY");
            }
            return StatusUi.forHealth("NOT_ENOUGH_DATA");
        } else if ("CLAIM".equalsIgnoreCase(type)) {
            return StatusUi.forClaim("UNDER_REVIEW");
        }
        return StatusUi.forClaim("UNDER_REVIEW");
    }

    @DrawableRes
    public static int icon(@NonNull NotificationEntity item) {
        String type = item.type != null ? item.type : "SYSTEM";
        if ("HEALTH".equalsIgnoreCase(type)) {
            return look(item).iconRes;
        } else if ("CLAIM".equalsIgnoreCase(type)) {
            return R.drawable.ic_nav_claims;
        }
        return R.drawable.ic_nav_alerts;
    }

    private static void markRead(Context context, NotificationEntity item) {
        if (item.isRead) {
            return;
        }
        Context app = context.getApplicationContext();
        ApiClient.getInstance(app).getService().markNotificationRead(item.id)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                    }
                });
        new Thread(() -> RindaDatabase.getInstance(app).notificationDao().markAsRead(item.id)).start();
    }

    private static long parseId(String link, String prefix) {
        if (link == null) {
            return -1;
        }
        try {
            return Long.parseLong(link.replace(prefix, "").replace("/", "").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
