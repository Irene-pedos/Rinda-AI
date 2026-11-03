package com.rinda.app.ui.common;

import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.rinda.app.databinding.ViewEmptyStateBinding;

/** Fills the shared empty state (layout/view_empty_state.xml). Every empty list uses it. */
public final class EmptyState {

    private EmptyState() {
    }

    public static void bind(@NonNull ViewEmptyStateBinding empty, @DrawableRes int iconRes,
                            @StringRes int titleRes, @StringRes int bodyRes) {
        bind(empty, iconRes, titleRes, bodyRes, 0, null);
    }

    /** With an action button. Pass actionRes = 0 for no button. */
    public static void bind(@NonNull ViewEmptyStateBinding empty, @DrawableRes int iconRes,
                            @StringRes int titleRes, @StringRes int bodyRes,
                            @StringRes int actionRes, View.OnClickListener action) {
        empty.ivEmptyIcon.setImageResource(iconRes);
        empty.tvEmptyTitle.setText(titleRes);
        empty.tvEmptyBody.setText(bodyRes);
        if (actionRes != 0 && action != null) {
            empty.btnEmptyAction.setText(actionRes);
            empty.btnEmptyAction.setOnClickListener(action);
            empty.btnEmptyAction.setVisibility(View.VISIBLE);
        } else {
            empty.btnEmptyAction.setVisibility(View.GONE);
        }
    }

    public static void show(@NonNull ViewEmptyStateBinding empty, boolean visible) {
        if (visible) {
            Ui.reveal(empty.getRoot());
        } else {
            empty.getRoot().setVisibility(View.GONE);
        }
    }
}
