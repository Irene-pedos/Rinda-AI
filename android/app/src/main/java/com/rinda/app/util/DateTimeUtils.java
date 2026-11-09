package com.rinda.app.util;

import android.text.format.DateUtils;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class DateTimeUtils {

    private DateTimeUtils() {
    }

    public static String formatRelativeTime(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isEmpty()) {
            return "";
        }
        try {
            long timeMillis = parseIsoToMillis(isoDateTime);
            long now = System.currentTimeMillis();
            CharSequence relative = DateUtils.getRelativeTimeSpanString(
                    timeMillis,
                    now,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
            );
            return relative.toString();
        } catch (Exception e) {
            return isoDateTime;
        }
    }

    public static String formatRelativeTime(long timeMillis) {
        if (timeMillis <= 0) {
            return "";
        }
        try {
            long now = System.currentTimeMillis();
            CharSequence relative = DateUtils.getRelativeTimeSpanString(
                    timeMillis,
                    now,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
            );
            return relative.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String formatLastUpdated(android.content.Context context, long timeMillis) {
        if (timeMillis <= 0) {
            return context.getString(com.rinda.app.R.string.value_not_available);
        }
        String relative = formatRelativeTime(timeMillis);
        return context.getString(com.rinda.app.R.string.last_updated, relative);
    }

    public static String formatDate(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isEmpty()) {
            return "";
        }
        try {
            long timeMillis = parseIsoToMillis(isoDateTime);
            SimpleDateFormat outFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            return outFormat.format(new Date(timeMillis));
        } catch (Exception e) {
            return isoDateTime;
        }
    }

    private static long parseIsoToMillis(String isoString) throws ParseException {
        // Try standard ISO-8601 with milliseconds: "yyyy-MM-dd'T'HH:mm:ss.SSSX"
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = sdf.parse(isoString);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return System.currentTimeMillis();
    }
}