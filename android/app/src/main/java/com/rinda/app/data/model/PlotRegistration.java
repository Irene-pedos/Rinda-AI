package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request and response bodies for registering a plot (docs/API.md, "Registering a plot"). There is no way to send
 * a boundary: it comes only from the GPS walk, built by the server from the walked points.
 */
public final class PlotRegistration {

    private PlotRegistration() {
    }

    /** POST /plots: step 1 of a registration. */
    public static class CreateRequest {
        @SerializedName("name") final String name;
        @SerializedName("upi") final String upi;
        @SerializedName("plantingDate") final String plantingDate;
        @SerializedName("relationshipToPlot") final String relationshipToPlot;
        @SerializedName("declarationAccepted") final boolean declarationAccepted;

        public CreateRequest(String name, String upi, String plantingDate, String relationshipToPlot,
                             boolean declarationAccepted) {
            this.name = name;
            this.upi = upi;
            this.plantingDate = plantingDate;
            this.relationshipToPlot = relationshipToPlot;
            this.declarationAccepted = declarationAccepted;
        }
    }

    /** PUT /plots/{id}: name and planting date only. */
    public static class UpdateRequest {
        @SerializedName("name") final String name;
        @SerializedName("plantingDate") final String plantingDate;

        public UpdateRequest(String name, String plantingDate) {
            this.name = name;
            this.plantingDate = plantingDate;
        }
    }

    /** One GPS fix of the walk, as Android reported it. Never a default or remembered location. */
    public static class WalkPoint {
        @SerializedName("lat") final double lat;
        @SerializedName("lng") final double lng;
        @SerializedName("accuracyM") final Double accuracyM;
        @SerializedName("recordedAt") final String recordedAt; // ISO-8601 instant
        @SerializedName("mockLocation") final boolean mockLocation;

        public WalkPoint(double lat, double lng, Double accuracyM, String recordedAt, boolean mockLocation) {
            this.lat = lat;
            this.lng = lng;
            this.accuracyM = accuracyM;
            this.recordedAt = recordedAt;
            this.mockLocation = mockLocation;
        }
    }

    /** POST /plots/{id}/walk. The server closes the ring: don't repeat the first point. */
    public static class WalkRequest {
        @SerializedName("points") final List<WalkPoint> points;

        public WalkRequest(List<WalkPoint> points) {
            this.points = points;
        }
    }

    /** GET /land-registry/parcels: the parcel as the registry provider knows it. A reference, never the boundary. */
    public static class ParcelLookupResponse {
        @SerializedName("upi") public String upi;
        @SerializedName("found") public boolean found;
        @SerializedName("source") public String source;
        @SerializedName("isMock") public boolean isMock;
        @SerializedName("notice") public String notice;
        @SerializedName("parcel") public Parcel parcel;
    }

    public static class Parcel {
        @SerializedName("boundary") public GeoJsonPolygon boundary;
        @SerializedName("areaM2") public BigDecimal areaM2;
        @SerializedName("areaHa") public BigDecimal areaHa;
        @SerializedName("landUse") public String landUse;
        @SerializedName("location") public String location;
    }

    public static class DocumentResponse {
        @SerializedName("id") public long id;
        @SerializedName("type") public String type;
        @SerializedName("typedAreaM2") public BigDecimal typedAreaM2;
    }

    /** A review flag on the plot. resolvedAt null means it is still open. */
    public static class Flag {
        @SerializedName("id") public long id;
        @SerializedName("type") public String type;
        @SerializedName("details") public String details;
        @SerializedName("resolvedAt") public String resolvedAt;
    }

    /** One registration check: PASSED, FLAGGED, REVIEWED, PENDING or NOT_RUN. Show NOT_RUN; never hide it. */
    public static class Check {
        @SerializedName("check") public String check;
        @SerializedName("result") public String result;
        @SerializedName("note") public String note;
    }
}
