package com.rinda.app.data.local;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rinda.app.data.model.DataOrigin;
import com.rinda.app.data.model.GeoJsonPolygon;
import com.rinda.app.data.model.PlotStatus;
import com.rinda.app.data.model.Role;
import java.lang.reflect.Type;
import java.math.BigDecimal;

public class RoomConverters {
    private static final Gson gson = new Gson();

    @TypeConverter
    public static String fromBigDecimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    @TypeConverter
    public static BigDecimal toBigDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    @TypeConverter
    public static String fromGeoJsonPolygon(GeoJsonPolygon polygon) {
        return polygon == null ? null : gson.toJson(polygon);
    }

    @TypeConverter
    public static GeoJsonPolygon toGeoJsonPolygon(String json) {
        if (json == null) return null;
        return gson.fromJson(json, GeoJsonPolygon.class);
    }

    @TypeConverter
    public static String fromPlotStatus(PlotStatus status) {
        return status == null ? null : status.name();
    }

    @TypeConverter
    public static PlotStatus toPlotStatus(String status) {
        return status == null ? null : PlotStatus.valueOf(status);
    }

    @TypeConverter
    public static String fromDataOrigin(DataOrigin origin) {
        return origin == null ? null : origin.name();
    }

    @TypeConverter
    public static DataOrigin toDataOrigin(String origin) {
        return origin == null ? null : DataOrigin.valueOf(origin);
    }

    @TypeConverter
    public static String fromRole(Role role) {
        return role == null ? null : role.name();
    }

    @TypeConverter
    public static Role toRole(String role) {
        return role == null ? null : Role.valueOf(role);
    }
}
