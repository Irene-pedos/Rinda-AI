package com.rinda.app.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.rinda.app.R;
import com.rinda.app.databinding.SheetInfoBinding;

/**
 * Plain-language explanation shown from a "?" button (help_* strings).
 * Use it for every word a farmer may not know: NDVI, confidence, cover, forecast, claims.
 */
public final class InfoSheet {

    private InfoSheet() {
    }

    public static void show(@NonNull Context context, @StringRes int titleRes, @StringRes int bodyRes) {
        show(context, R.drawable.ic_lightbulb, titleRes, bodyRes);
    }

    public static void show(@NonNull Context context, @DrawableRes int iconRes,
                            @StringRes int titleRes, @StringRes int bodyRes) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        SheetInfoBinding binding = SheetInfoBinding.inflate(LayoutInflater.from(context));
        binding.ivIcon.setImageResource(iconRes);
        binding.tvTitle.setText(titleRes);
        binding.tvBody.setText(bodyRes);
        binding.btnGotIt.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(binding.getRoot());
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }

    /** Wires a "?" button to open a help sheet. */
    public static void bind(@NonNull View helpButton, @StringRes int titleRes, @StringRes int bodyRes) {
        helpButton.setOnClickListener(v -> show(v.getContext(), titleRes, bodyRes));
    }
}
