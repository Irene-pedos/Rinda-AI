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

    /** Null only on plots registered before UPIs were collected. */
    @Column
    private String upi;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String crop = "MAIZE";

    /**
     * Held as GeoJSON here. MySQL converts it, which keeps lon/lat and SRID 4326 axis order correct.
     * Set only from an accepted GPS walk (WalkService); null until the plot is walked.
     */
    @ColumnTransformer(read = "ST_AsGeoJSON(boundary)", write = "ST_GeomFromGeoJSON(?)")
    @Column
    private String boundary;

    @Column
    private String geojson;

    @Column(name = "area_ha")
    private BigDecimal areaHa;

    @Column(name = "planting_date")
    private LocalDate plantingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlotStatus status = PlotStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_level", nullable = false)
    private VerificationLevel verificationLevel = VerificationLevel.SELF_DECLARED;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_to_plot")
    private RelationshipToPlot relationshipToPlot;

    @Column(name = "declaration_accepted_at")
    private Instant declarationAcceptedAt;

    @Column(name = "planted_area_geojson")
    private String plantedAreaGeojson;

    @Column(name = "walked_area_m2")
    private BigDecimal walkedAreaM2;

    @Column(name = "document_area_m2")
    private BigDecimal documentAreaM2;

    @Column(name = "area_diff_pct")
    private BigDecimal areaDiffPct;

    @Column(name = "data_status", nullable = false)
    private String dataStatus = "CONFIRMED";

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

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

    /** A new registration: no boundary until the farmer walks the plot. */
    public Plot(long farmerId, String name, String upi, LocalDate plantingDate, RelationshipToPlot relationship) {
        this.farmerId = farmerId;
        this.name = name;
        this.upi = upi;
        this.plantingDate = plantingDate;
        this.relationshipToPlot = relationship;
        this.declarationAcceptedAt = now();
    }

    @PreUpdate
    void touch() {
        updatedAt = now();
    }

    public void rename(String newName) {
        this.name = newName;
    }

    /** Only WalkService calls this, with a polygon it built from walk points. */
    void setWalkedBoundary(String newGeojson, BigDecimal newAreaHa, BigDecimal newAreaM2) {
        this.geojson = newGeojson;
        this.boundary = newGeojson;
        this.areaHa = newAreaHa;
        this.walkedAreaM2 = newAreaM2;
        // A new boundary means the earlier decision no longer applies.
        this.rejectionReason = null;
        if (status == PlotStatus.REJECTED) {
            status = PlotStatus.DRAFT;
        }
        if (!verificationLevel.atLeast(VerificationLevel.FIELD_VERIFIED)) {
            verificationLevel = VerificationLevel.SELF_DECLARED;
        }
    }

    void setDocumentArea(BigDecimal areaM2) {
        this.documentAreaM2 = areaM2;
    }

    void setAreaDiffPct(BigDecimal pct) {
        this.areaDiffPct = pct;
    }

    void setStatus(PlotStatus status) {
        this.status = status;
    }

    void setVerificationLevel(VerificationLevel level) {
        this.verificationLevel = level;
    }

    public void changePlantingDate(LocalDate date) {
        this.plantingDate = date;
    }

    public void archive() {
        this.status = PlotStatus.ARCHIVED;
    }

    /** A person checked the plot in the field with a geotagged photo (docs/01-plot-verification.md). */
    public void verify(long agentId) {
        this.status = PlotStatus.ACTIVE;
        this.verificationLevel = VerificationLevel.FIELD_VERIFIED;
        this.verifiedBy = agentId;
        this.rejectionReason = null;
    }

    public void reject(long agentId, String reason) {
        this.status = PlotStatus.REJECTED;
        this.verifiedBy = agentId;
        this.rejectionReason = reason;
    }

    public boolean hasBoundary() {
        return geojson != null;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Long getId() {
        return id;
    }

    public long getFarmerId() {
        return farmerId;
    }

    public String getUpi() {
        return upi;
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

    public VerificationLevel getVerificationLevel() {
        return verificationLevel;
    }

    public RelationshipToPlot getRelationshipToPlot() {
        return relationshipToPlot;
    }

    public Instant getDeclarationAcceptedAt() {
        return declarationAcceptedAt;
    }

    public String getPlantedAreaGeojson() {
        return plantedAreaGeojson;
    }

    public BigDecimal getWalkedAreaM2() {
        return walkedAreaM2;
    }

    public BigDecimal getDocumentAreaM2() {
        return documentAreaM2;
    }

    public BigDecimal getAreaDiffPct() {
        return areaDiffPct;
    }

    public String getDataStatus() {
        return dataStatus;
    }

    public boolean isDemo() {
        return demo;
    }

    public Long getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
