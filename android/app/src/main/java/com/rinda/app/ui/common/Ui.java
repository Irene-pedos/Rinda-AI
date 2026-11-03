package com.rinda.app.ui.common;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.rinda.app.R;

/**
 * Small shared UI helpers, so every screen gives feedback the same way.
 *
 * - Short confirmations ("Plot name saved"): {@link #toast(View, int)} (a Snackbar, not a Toast).
 * - Errors the user can fix by retrying: {@link #error(View, String, Runnable)} with a "Try again" action.
 * - Anything that needs a decision or explains a consequence: {@link #dialog(Context)}.
 */
public final class Ui {

    private Ui() {
    }

    public static void toast(@NonNull View anchor, @StringRes int messageRes) {
        Snackbar.make(anchor, messageRes, Snackbar.LENGTH_SHORT).show();
    }

    public static void toast(@NonNull View anchor, @NonNull String message) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show();
    }

    /** Error message with an optional "Try again" button. */
    public static void error(@NonNull View anchor, @NonNull String message, Runnable retry) {
        Snackbar bar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG);
        if (retry != null) {
            bar.setAction(R.string.btn_retry, v -> retry.run());
        }
        bar.show();
    }

    /** Material dialog with the Rinda look (rounded, large text). */
    @NonNull
    public static MaterialAlertDialogBuilder dialog(@NonNull Context context) {
        return new MaterialAlertDialogBuilder(context);
    }

    public static boolean isOnline(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return true;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /** Fades a view in when it becomes visible, for content that arrives from the network. */
    public static void reveal(@NonNull View view) {
        if (view.getVisibility() == View.VISIBLE && view.getAlpha() == 1f) {
            return;
        }
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(220).start();
    }
}
