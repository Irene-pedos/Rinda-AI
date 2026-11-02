package com.rinda.backend.plot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "plots")
public class Plot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false)
    private long farmerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String crop = "MAIZE";

    /** Held as GeoJSON here. MySQL converts it, which keeps lon/lat and SRID 4326 axis order correct. */
    @ColumnTransformer(read = "ST_AsGeoJSON(boundary)", write = "ST_GeomFromGeoJSON(?)")
    @Column(nullable = false)
    private String boundary;

    @Column(nullable = false)
    private String geojson;

    @Column(name = "area_ha", nullable = false)
    private BigDecimal areaHa;

    @Column(name = "planting_date")
    private LocalDate plantingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlotStatus status = PlotStatus.PENDING_VERIFICATION;

    @Column(name = "data_status", nullable = false)
    private String dataStatus = "CONFIRMED";

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = now();

    protected Plot() {
    }

    /** Microseconds match DATETIME(6), so a write response shows the same time a later read does. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Plot(long farmerId, String name, String geojson, BigDecimal areaHa, LocalDate plantingDate) {
        this.farmerId = farmerId;
        this.name = name;
        this.plantingDate = plantingDate;
        changeBoundary(geojson, areaHa);
    }

    @PreUpdate
    void touch() {
        updatedAt = now();
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void changeBoundary(String newGeojson, BigDecimal newAreaHa) {
        this.geojson = newGeojson;
        this.boundary = newGeojson;
        this.areaHa = newAreaHa;
    }

    public void changePlantingDate(LocalDate date) {
        this.plantingDate = date;
    }

    public void returnToVerification() {
        this.status = PlotStatus.PENDING_VERIFICATION;
    }

    public void archive() {
        this.status = PlotStatus.ARCHIVED;
    }

    public Long getId() {
        return id;
    }

    public long getFarmerId() {
        return farmerId;
    }

    public String getName() {
        return name;
    }

    public String getCrop() {
        return crop;
    }

    public String getGeojson() {
        return geojson;
    }

    public BigDecimal getAreaHa() {
        return areaHa;
    }

    public LocalDate getPlantingDate() {
        return plantingDate;
    }

    public PlotStatus getStatus() {
        return status;
    }

    public String getDataStatus() {
        return dataStatus;
    }

    public boolean isDemo() {
        return demo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
