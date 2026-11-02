package com.rinda.app.data.api;

import android.content.Context;
import com.google.gson.Gson;
import com.rinda.app.R;
import com.rinda.app.data.model.ApiProblem;
import retrofit2.Response;

public class ApiErrorParser {

    private static final Gson gson = new Gson();

    public static ApiProblem parse(Response<?> response) {
        if (response == null || response.errorBody() == null) {
            return new ApiProblem();
        }
        try {
            String errorJson = response.errorBody().string();
            return gson.fromJson(errorJson, ApiProblem.class);
        } catch (Exception e) {
            return new ApiProblem();
        }
    }

    public static String getFriendlyMessage(Context context, ApiProblem problem) {
        if (problem == null || problem.getCode() == null) {
            return context.getString(R.string.error_generic);
        }

        switch (problem.getCode()) {
            case "PHONE_INVALID":
                return context.getString(R.string.error_phone_invalid);
            case "OTP_INVALID":
                return context.getString(R.string.error_otp_invalid);
            case "OTP_RATE_LIMITED":
                return context.getString(R.string.error_otp_rate_limited);
            case "COOPERATIVE_NOT_FOUND":
                return context.getString(R.string.error_cooperative_not_found);
            case "PLOT_OUTSIDE_RWANDA":
                return context.getString(R.string.error_plot_outside_rwanda);
            case "PLOT_TOO_SMALL":
                return context.getString(R.string.error_plot_too_small);
            case "PLOT_TOO_LARGE":
                return context.getString(R.string.error_plot_too_large);
            case "PLOT_GEOMETRY_INVALID":
                return context.getString(R.string.error_plot_geometry_invalid);
            case "PLANTING_DATE_INVALID":
                return context.getString(R.string.error_planting_date_invalid);
            case "PLOT_LOCKED":
                return context.getString(R.string.error_plot_locked);
            case "PLOT_DELETE_DEFERRED":
                return context.getString(R.string.error_plot_delete_deferred);
            case "UNAUTHORIZED":
                return context.getString(R.string.error_unauthorized);
            case "FORBIDDEN":
                return context.getString(R.string.error_forbidden);
            case "VALIDATION_FAILED":
                if (problem.getFields() != null && !problem.getFields().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (String fieldMsg : problem.getFields().values()) {
                        if (sb.length() > 0) sb.append(". ");
                        sb.append(fieldMsg);
                    }
                    return sb.toString();
                }
                return problem.getDetail() != null ? problem.getDetail() : context.getString(R.string.error_generic);
            default:
                if (problem.getDetail() != null && !problem.getDetail().trim().isEmpty()) {
                    return problem.getDetail();
                }
                return context.getString(R.string.error_generic);
        }
    }
}
